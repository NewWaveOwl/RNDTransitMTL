package transit

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/** Proves selected-point street previews, offset-aware trip time and recoverable provider failures. */
class WalkingCyclingPreviewTest {
    private val geometry = "_mwtGvz_\u0060Mo}@n}@"
    private fun request(mode: String = "pedestrian", departure: String = "2026-11-23T10:00:00-05:00") =
        obj("origin" to obj("lat" to 45.5088, "lon" to -73.5686), "destination" to obj("lat" to 45.5188, "lon" to -73.5786),
            "mode" to mode, "departureTime" to departure)
    private fun zoned(departure: String = "2026-11-23T10:00:00-05:00", zone: Any? = "America/Toronto") =
        JsonObject(request(departure = departure) + ("timeZone" to value(zone)))
    private fun leg(mode: String = "WALK", points: String = geometry, seconds: Double = 600.0,
        start: String = "2026-11-23T15:00:00Z", end: String = "2026-11-23T15:10:00Z", length: Int = 2) =
        obj("mode" to mode, "distance" to 1400.25, "duration" to seconds, "realTime" to false,
            "start" to obj("scheduledTime" to start), "end" to obj("scheduledTime" to end),
            "legGeometry" to obj("points" to points, "length" to length))
    private fun response(legs: List<JsonObject> = listOf(leg()), seconds: Double = 600.0) =
        obj("data" to obj("planConnection" to obj("routingErrors" to emptyList<Any>(),
            "edges" to listOf(obj("node" to obj("duration" to seconds, "legs" to legs))))))
    private suspend fun rejectsBeforeProvider(body: JsonObject) {
        val planner = TravelTrackerPlanner { error("Invalid input must not contact OTP.") }
        try { planner.plan(body); fail("Expected input rejection.") } catch (_: InputError) { }
        assertEquals(0L, planner.requests.get())
    }
    private suspend fun rejectsProvider(body: JsonObject) {
        try { TravelTrackerPlanner { body }.plan(request()); fail("Expected malformed-provider rejection.") }
        catch (_: ProviderError) { }
    }

    @Test fun selectedWalkingEndpointsAndProviderGeometryDistanceAndDurationAreRetained() = runBlocking {
        val planner = TravelTrackerPlanner { query ->
            val variables = query["variables"]
            assertEquals(45.5088, variables.field("originLat").number()!!, 0.0)
            assertEquals(-73.5786, variables.field("destinationLon").number()!!, 0.0)
            assertEquals("WALK", variables.field("modes").field("direct").items().single().text())
            response()
        }
        val itinerary = planner.plan(request())["itineraries"].items().single()
        assertEquals(1400.25, itinerary.field("distanceMeters").number()!!, 0.0)
        assertEquals(600.0, itinerary.field("durationSeconds").number()!!, 0.0)
        assertEquals(geometry, itinerary.field("legs").items().single().field("geometry").text())
        assertEquals("estimated", itinerary.field("timingBasis").text())
    }

    @Test fun cyclingUsesBicycleRoutingWithItsOwnEstimateRatherThanRelabeledWalking() = runBlocking {
        val planner = TravelTrackerPlanner { query ->
            assertEquals("BICYCLE", query["variables"].field("modes").field("direct").items().single().text())
            response(listOf(leg("BICYCLE", seconds = 300.0, end = "2026-11-23T15:05:00Z")), 300.0)
        }
        val itinerary = planner.plan(request("bike"))["itineraries"].items().single()
        assertEquals(300.0, itinerary.field("durationSeconds").number()!!, 0.0)
        assertEquals("BICYCLE", itinerary.field("legs").items().single().field("mode").text())
    }

    @Test fun namedTripZoneValidatesOffsetAndPreservesTheRequestedLocalDeparture() = runBlocking {
        val planner = TravelTrackerPlanner { query ->
            assertEquals("2026-11-23T15:00:00Z", query["variables"].field("departure").text())
            response()
        }
        val preview = planner.plan(zoned())
        assertEquals("America/Toronto", preview["timeZone"].text())
        assertEquals("2026-11-23T10:00-05:00", preview["requestedDepartureTime"].text())
    }

    @Test fun omittedTimeZoneKeepsFourFieldCallerCompatibility() = runBlocking {
        assertEquals(JsonNull, TravelTrackerPlanner { response() }.plan(request())["timeZone"])
    }

    @Test fun explicitNullTimeZoneKeepsDefaultSerializerCompatibility() = runBlocking {
        assertEquals("success", TravelTrackerPlanner { response() }.plan(zoned(zone = null))["status"].text())
    }

    @Test fun summerOffsetMismatchIsRejectedWithoutProviderIo() = runBlocking {
        rejectsBeforeProvider(zoned("2026-07-01T10:00:00-05:00"))
    }

    @Test fun impossibleDaylightSavingLocalTimeIsRejectedWithoutProviderIo() = runBlocking {
        rejectsBeforeProvider(zoned("2026-03-08T02:30:00-05:00"))
    }

    @Test fun explicitFirstOffsetResolvesAmbiguousAutumnLocalTime() = runBlocking {
        val planner = TravelTrackerPlanner { query ->
            assertEquals("2026-11-01T05:30:00Z", query["variables"].field("departure").text())
            response(listOf(leg(start = "2026-11-01T05:30:00Z", end = "2026-11-01T05:40:00Z")))
        }
        assertEquals("success", planner.plan(zoned("2026-11-01T01:30:00-04:00"))["status"].text())
    }

    @Test fun explicitSecondOffsetAlsoResolvesAmbiguousAutumnLocalTime() = runBlocking {
        val planner = TravelTrackerPlanner { query ->
            assertEquals("2026-11-01T06:30:00Z", query["variables"].field("departure").text())
            response(listOf(leg(start = "2026-11-01T06:30:00Z", end = "2026-11-01T06:40:00Z")))
        }
        assertEquals("success", planner.plan(zoned("2026-11-01T01:30:00-05:00"))["status"].text())
    }

    @Test fun unknownZoneDoesNotSilentlyFallBackToServerLocalTime() = runBlocking { rejectsBeforeProvider(zoned(zone = "Unknown/Zone")) }
    @Test fun blankZoneDoesNotMasqueradeAsAnOmittedZone() = runBlocking { rejectsBeforeProvider(zoned(zone = "")) }

    @Test fun reversedProviderPathCannotSilentlyReverseSelectedStopOrder() = runBlocking {
        rejectsProvider(response(listOf(leg(points = "okytGfya\u0060Mn}@o}@"))))
    }

    @Test fun inconsistentGeometryPointCountIsAProviderFailure() = runBlocking {
        rejectsProvider(response(listOf(leg(length = 30))))
    }

    @Test fun legDurationWithWrongUnitsIsNotPresentedAsATravelEstimate() = runBlocking {
        rejectsProvider(response(listOf(leg(seconds = 60.0))))
    }

    @Test fun roundedProviderDurationsAllowFractionalSecondDifferences() = runBlocking {
        assertEquals("success", TravelTrackerPlanner { response(listOf(leg(seconds = 600.5)), 600.5) }.plan(request())["status"].text())
    }

    @Test fun realtimeDurationUsesEstimatedSpanWhenArrivalAndDepartureDelaysDiffer() = runBlocking {
        val updated = JsonObject(leg("BUS", seconds = 660.0) + mapOf(
            "realTime" to JsonPrimitive(true),
            "start" to obj("scheduledTime" to "2026-11-23T15:00:00Z", "estimated" to obj("time" to "2026-11-23T15:02:00Z", "delay" to 120)),
            "end" to obj("scheduledTime" to "2026-11-23T15:10:00Z", "estimated" to obj("time" to "2026-11-23T15:13:00Z", "delay" to 180))))
        val itinerary = TravelTrackerPlanner { response(listOf(updated), 660.0) }.plan(request("bus"))["itineraries"].items().single()
        assertEquals("realtime", itinerary.field("timingBasis").text())
        assertEquals("2026-11-23T15:02:00Z", itinerary.field("legs").items().single().field("departureTime").text())
        assertEquals(660.0, itinerary.field("durationSeconds").number()!!, 0.0)
    }

    @Test fun itinerarySpanIncludesWaitingInsteadOfRequiringSumOfMovementDurations() = runBlocking {
        val legs = listOf(leg(points = "_mwtGvz_\u0060Mg^f^", seconds = 240.0, end = "2026-11-23T15:04:00Z"),
            leg(points = "glxtG~y\u0060\u0060Mg^f^", seconds = 240.0, start = "2026-11-23T15:06:00Z"))
        val itinerary = TravelTrackerPlanner { response(legs) }.plan(request())["itineraries"].items().single()
        assertEquals(600.0, itinerary.field("durationSeconds").number()!!, 0.0)
    }

    @Test fun missingRoutingErrorStateCannotBeMisreportedAsNoRoute() = runBlocking {
        rejectsProvider(obj("data" to obj("planConnection" to obj("edges" to emptyList<Any>()))))
    }

    @Test fun knownNoRouteReturnsNoInventedPathAndRetainsRequestedZone() = runBlocking {
        val planner = TravelTrackerPlanner {
            obj("data" to obj("planConnection" to obj("edges" to emptyList<Any>(),
                "routingErrors" to listOf(obj("code" to "LOCATION_NOT_FOUND")))))
        }
        val result = planner.plan(zoned())
        assertEquals("no_route", result["status"].text())
        assertTrue(result["itineraries"].items().isEmpty())
        assertEquals("America/Toronto", result["timeZone"].text())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun callerCancellationPropagatesAndCancelsProviderWork() = runTest {
        var cancelled = false
        val planner = TravelTrackerPlanner {
            try { awaitCancellation() } finally { cancelled = true }
        }
        val pending = async { planner.plan(request()) }
        runCurrent()
        pending.cancelAndJoin()
        assertTrue(cancelled)
        assertEquals(1L, planner.requests.get())
    }

    @Test fun operationTimeoutIsUnavailableAndReleasesCapacityForRetry() = runTest {
        var calls = 0
        val planner = TravelTrackerPlanner { if (calls++ == 0) awaitCancellation() else response() }
        val api = TravelTrackerApi(NominatimAddresses({ _, _ -> error("No address lookup.") }), planner, { "fixture-user" })
        val timeout = api.plan("Bearer fixture", "application/json") { request().toString() }
        assertEquals(503, timeout.first)
        assertEquals("unavailable", timeout.second["status"].text())
        val retry = api.plan("Bearer fixture", "application/json") { request().toString() }
        assertEquals(200, retry.first)
        assertEquals("success", retry.second["status"].text())
    }

    @Test fun providerOutageIsSanitizedAndNeverReturnedAsNoRoute() = runBlocking {
        val api = TravelTrackerApi(NominatimAddresses({ _, _ -> error("No address lookup.") }),
            TravelTrackerPlanner { error("private router diagnostic and token") }, { "fixture-user" })
        val result = api.plan("Bearer fixture", "application/json") { request().toString() }
        assertEquals(503, result.first)
        assertEquals("unavailable", result.second["status"].text())
        assertFalse(result.second.toString().contains("private router diagnostic"))
    }
}
