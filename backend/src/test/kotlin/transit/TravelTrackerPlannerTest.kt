package transit

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class TravelTrackerPlannerTest {
    // Standard Google polyline example, first two points only.
    private val geometry = "_p~iF~ps|U_ulLnnqC"
    private fun request(mode: String = "car") = obj("origin" to obj("lat" to 38.5, "lon" to -120.2),
        "destination" to obj("lat" to 40.7, "lon" to -120.95), "mode" to mode, "departureTime" to "2026-11-23T10:00:00-05:00")
    private fun route(mode: String, realtime: Boolean = false) = obj("data" to obj("planConnection" to obj(
        "routingErrors" to emptyList<Any>(), "edges" to listOf(obj("node" to obj("duration" to 600, "legs" to listOf(obj(
            "mode" to mode, "distance" to 1000, "duration" to 600, "realTime" to realtime,
            "start" to obj("scheduledTime" to "2026-11-23T15:00:00Z", "estimated" to if (realtime) obj("time" to "2026-11-23T15:00:00Z") else null),
            "end" to obj("scheduledTime" to "2026-11-23T15:10:00Z", "estimated" to if (realtime) obj("time" to "2026-11-23T15:10:00Z") else null),
            "route" to obj("shortName" to "747"), "legGeometry" to obj("points" to geometry, "length" to 2)))))))))

    @Test fun carUsesRealCarModeAndChosenDeparture() = runBlocking {
        val planner = TravelTrackerPlanner { body ->
            val variables = body["variables"]!!
            assertEquals(listOf("CAR"), variables.field("modes").field("direct").items().map { it.text() })
            assertEquals("2026-11-23T15:00:00Z", variables.field("departure").text())
            route("CAR")
        }
        val result = planner.plan(request())
        assertEquals("success", result["status"].text())
        assertEquals("estimated", result["itineraries"].items().single().field("timingBasis").text())
        assertEquals(geometry, result["itineraries"].items().single().field("legs").items().single().field("geometry").text())
    }

    @Test fun cyclingUsesBicycleGraphRatherThanAcceleratedWalking() = runBlocking {
        val planner = TravelTrackerPlanner { body ->
            assertEquals("BICYCLE", body["variables"].field("modes").field("direct").items().single().text())
            route("BICYCLE")
        }
        assertEquals("success", planner.plan(request("bike"))["status"].text())
    }

    @Test fun walkingUsesWalkingMode() = runBlocking {
        val planner = TravelTrackerPlanner { body ->
            assertEquals("WALK", body["variables"].field("modes").field("direct").items().single().text())
            route("WALK")
        }
        assertEquals("success", planner.plan(request("pedestrian"))["status"].text())
    }

    @Test fun busUsesTransitAndLabelsScheduledTimes() = runBlocking {
        val planner = TravelTrackerPlanner { body ->
            assertEquals(JsonPrimitive(true), body["variables"].field("modes").field("transitOnly"))
            assertEquals("BUS", body["variables"].field("modes").field("transit").field("transit").items().single().field("mode").text())
            route("BUS")
        }
        assertEquals("scheduled", planner.plan(request("bus"))["itineraries"].items().single().field("timingBasis").text())
    }

    @Test fun trainUsesRailAndLabelsRealtimeSeparately() = runBlocking {
        val planner = TravelTrackerPlanner { body ->
            assertEquals("RAIL", body["variables"].field("modes").field("transit").field("transit").items().single().field("mode").text())
            route("RAIL", true)
        }
        assertEquals("realtime", planner.plan(request("train"))["itineraries"].items().single().field("timingBasis").text())
    }

    @Test fun noRouteIsExplicitWithoutInventedGeometry() = runBlocking {
        val planner = TravelTrackerPlanner { obj("data" to obj("planConnection" to obj("routingErrors" to listOf(obj("code" to "OUTSIDE_SERVICE_PERIOD")), "edges" to emptyList<Any>()))) }
        val result = planner.plan(request())
        assertEquals("no_route", result["status"].text())
        assertTrue(result["itineraries"].items().isEmpty())
    }

    @Test fun unsupportedModeDoesNotContactOtp() = runBlocking {
        val planner = TravelTrackerPlanner { error("OTP must not be called") }
        assertEquals("unsupported", planner.plan(request("plane"))["status"].text())
    }

    @Test fun offsetlessDepartureIsRejectedBeforeOtp() = runBlocking {
        val planner = TravelTrackerPlanner { error("OTP must not be called") }
        try { planner.plan(JsonObject(request() + ("departureTime" to JsonPrimitive("2026-11-23T10:00:00")))); fail() }
        catch (_: InputError) { }
    }

    @Test fun wrongModeReturnedByProviderIsNotPresentedAsChosenMode() = runBlocking {
        val planner = TravelTrackerPlanner { route("WALK") }
        try { planner.plan(request("car")); fail() } catch (_: ProviderError) { }
    }

    @Test fun providerQueryErrorsAreAnOutageRatherThanNoRoute() = runBlocking {
        val planner = TravelTrackerPlanner { obj("errors" to listOf(obj("message" to "private error"))) }
        try { planner.plan(request()); fail() } catch (_: ProviderError) { }
    }
}
