package transit

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/** Covers verified car support, loaded transit modes, real stop metadata and truthful preview failures. */
class CarTransitPreviewTest {
    private val whole = "_mwtGvz_\u0060Mo}@n}@"
    private val firstHalf = "_mwtGvz_\u0060Mg^f^"
    private val secondHalf = "glxtG~y\u0060\u0060Mg^f^"
    private fun request(mode: String = "car", departure: String = "2026-11-23T10:00:00-05:00") =
        obj("origin" to obj("lat" to 45.5088, "lon" to -73.5686), "destination" to obj("lat" to 45.5188, "lon" to -73.5786),
            "mode" to mode, "departureTime" to departure, "timeZone" to "America/Toronto")
    private fun place(name: String, lat: Double, lon: Double, id: String? = null) =
        obj("name" to name, "lat" to lat, "lon" to lon, "stop" to id?.let { obj("gtfsId" to it) })
    private fun leg(mode: String = "CAR", points: String = whole, start: String = "2026-11-23T15:00:00Z",
        end: String = "2026-11-23T15:10:00Z", seconds: Double = 600.0,
        from: JsonObject = place("Origin", 45.5088, -73.5686), to: JsonObject = place("Destination", 45.5188, -73.5786)) =
        obj("mode" to mode, "distance" to 1400, "duration" to seconds, "realTime" to false,
            "interlineWithPreviousLeg" to false, "headsign" to if (mode in listOf("BUS", "RAIL")) "Fixture terminus" else null,
            "route" to if (mode in listOf("BUS", "RAIL")) obj("gtfsId" to "FIXTURE:80", "shortName" to "80") else null,
            "from" to from, "to" to to, "intermediateStops" to null,
            "start" to obj("scheduledTime" to start), "end" to obj("scheduledTime" to end),
            "legGeometry" to obj("points" to points, "length" to 2))
    private fun response(legs: List<JsonObject> = listOf(leg()), seconds: Double = 600.0, transfers: Int? = 0) =
        obj("data" to obj("planConnection" to obj("routingErrors" to emptyList<Any>(), "edges" to listOf(
            obj("node" to obj("duration" to seconds, "numberOfTransfers" to transfers, "legs" to legs))))))
    private fun noRoute(code: String) = obj("data" to obj("planConnection" to obj("edges" to emptyList<Any>(),
        "routingErrors" to listOf(obj("code" to code)))))
    private fun metadata(direct: List<String> = listOf("WALK", "BICYCLE", "CAR"), transit: List<String> = listOf("BUS", "RAIL")) =
        obj("data" to obj("directModes" to obj("enumValues" to direct.map { obj("name" to it) }),
            "serviceTimeRange" to obj("start" to 1787544000L, "end" to 1799038800L),
            "feeds" to listOf(obj("feedId" to "FIXTURE", "publisher" to obj("name" to "Fixture schedules"),
                "agencies" to listOf(obj("routes" to transit.map { obj("mode" to it) }))))))
    private fun info() = obj("data" to obj("serverInfo" to obj("version" to "2.10.0", "internalTransitModelTimeZone" to "America/Toronto")))
    private fun capabilities(direct: List<String> = listOf("WALK", "BICYCLE", "CAR"), transit: List<String> = listOf("BUS", "RAIL")) =
        OtpCapabilities({ metadata(direct, transit) }, { info() })
    private suspend fun rejectsProvider(raw: JsonObject, mode: String = "bus") {
        try { TravelTrackerPlanner { raw }.plan(request(mode)); fail("Expected provider-contract rejection.") }
        catch (_: ProviderError) { }
    }

    @Test fun carUsesActualCarQueryAndReturnsCoverageWithoutAnAlternateRouterOrStraightLineFallback() = runBlocking {
        val planner = TravelTrackerPlanner(capabilities()) { query ->
            assertEquals("CAR", query["variables"].field("modes").field("direct").items().single().text())
            assertTrue(query["query"].text()!!.contains("TravelTrackerRoute"))
            response()
        }
        val result = planner.plan(request())
        assertEquals("success", result["status"].text())
        assertEquals(JsonNull, result["reason"])
        assertEquals("America/Toronto", result["coverage"].field("timeZone").text())
        assertEquals(whole, result["itineraries"].items().single().field("legs").items().single().field("geometry").text())
    }

    @Test fun routerWithoutVerifiedCarSchemaReturnsExplicitUnsupportedInsteadOfRelabeledWalking() = runBlocking {
        val planner = TravelTrackerPlanner(capabilities(direct = listOf("WALK", "BICYCLE"))) { error("No CAR query is supported.") }
        val result = planner.plan(request())
        assertEquals("unsupported", result["status"].text())
        assertEquals("unsupported_mode", result["reason"].text())
        assertTrue(result["itineraries"].items().isEmpty())
        assertEquals(0L, planner.requests.get())
    }

    @Test fun optionalMetadataOutageDoesNotDisableAWorkingRoadRouter() = runBlocking {
        val source = OtpCapabilities({ error("private metadata diagnostic") }, { info() })
        val result = TravelTrackerPlanner(source) { response() }.plan(request())
        assertEquals("success", result["status"].text())
        assertEquals(JsonNull, result["coverage"])
        assertFalse(result.toString().contains("private metadata diagnostic"))
    }

    @Test fun optionalMetadataTimeoutStillAllowsARealCarPreview() = runTest {
        val source = OtpCapabilities({ awaitCancellation() }, { info() })
        val result = TravelTrackerPlanner(source) { response() }.plan(request())
        assertEquals("success", result["status"].text())
        assertEquals(JsonNull, result["coverage"])
    }

    @Test fun missingRailDataIsExplicitAndDoesNotSubstituteMetroOrBus() = runBlocking {
        val planner = TravelTrackerPlanner(capabilities(transit = listOf("BUS", "SUBWAY"))) { error("No train data is loaded.") }
        val result = planner.plan(request("train"))
        assertEquals("unsupported", result["status"].text())
        assertEquals("mode_not_in_dataset", result["reason"].text())
        assertEquals(0L, planner.requests.get())
    }

    @Test fun incompleteModeMetadataDoesNotFalselyRejectAWorkingRailQuery() = runBlocking {
        val source = OtpCapabilities({
            obj("data" to obj("feeds" to listOf(obj("feedId" to "unknown-route-metadata")), "directModes" to null, "serviceTimeRange" to null))
        }, { info() })
        val result = TravelTrackerPlanner(source) { response(listOf(leg("RAIL"))) }.plan(request("train"))
        assertEquals("success", result["status"].text())
    }

    @Test fun scheduledBusKeepsWalkingAccessNamedBoardingAndAlightingAndReportedIntermediateStops() = runBlocking {
        val boarding = place("Boarding platform", 45.5108, -73.5706, "FIXTURE:BOARD")
        val alighting = place("Alighting platform", 45.5168, -73.5766, "FIXTURE:ALIGHT")
        val access = leg("WALK", "_mwtGvz_\u0060MoKnK", end = "2026-11-23T15:03:00Z", seconds = 180.0, to = boarding)
        val vehicle = JsonObject(leg("BUS", "oywtGfg\u0060\u0060Mod@nd@", start = "2026-11-23T15:05:00Z",
            end = "2026-11-23T15:13:00Z", seconds = 480.0, from = boarding, to = alighting) +
            ("intermediateStops" to value(listOf(obj("name" to "Intermediate platform", "lat" to 45.5138,
                "lon" to -73.5736, "gtfsId" to "FIXTURE:MID")))))
        val egress = leg("WALK", "__ytGvla\u0060MoKnK", start = "2026-11-23T15:13:00Z",
            end = "2026-11-23T15:16:00Z", seconds = 180.0, from = alighting)
        val result = TravelTrackerPlanner(capabilities()) { response(listOf(access, vehicle, egress), 960.0) }.plan(request("bus"))
        val itinerary = result["itineraries"].items().single()
        val legs = itinerary.field("legs").items()
        assertEquals(listOf("WALK", "BUS", "WALK"), legs.map { it.field("mode").text() })
        assertEquals("estimated", legs.first().field("timingBasis").text())
        assertEquals("scheduled", legs[1].field("timingBasis").text())
        assertEquals("Boarding platform", legs[1].field("from").field("name").text())
        assertEquals("FIXTURE:ALIGHT", legs[1].field("to").field("stopId").text())
        assertEquals("FIXTURE:MID", legs[1].field("intermediateStops").items().single().field("stopId").text())
        assertEquals("2026-11-23T15:05:00Z", legs[1].field("scheduledDepartureTime").text())
        assertEquals(0.0, itinerary.field("transferCount").number()!!, 0.0)
    }

    @Test fun railPreviewPreservesRailModeAndNamedPublicPlatformIds() = runBlocking {
        val rail = leg("RAIL", from = place("Rail platform A", 45.5088, -73.5686, "FIXTURE:RAIL_A"),
            to = place("Rail platform B", 45.5188, -73.5786, "FIXTURE:RAIL_B"))
        val result = TravelTrackerPlanner(capabilities()) { response(listOf(rail)) }.plan(request("train"))
        val mapped = result["itineraries"].items().single().field("legs").items().single()
        assertEquals("RAIL", mapped.field("mode").text())
        assertEquals("FIXTURE:RAIL_A", mapped.field("from").field("stopId").text())
        assertEquals("scheduled", mapped.field("timingBasis").text())
    }

    @Test fun twoVehicleBoardingsRetainProviderTransferCountAndWaitingTime() = runBlocking {
        val mid = place("Transfer platform", 45.5138, -73.5736, "FIXTURE:MID")
        val first = leg("BUS", firstHalf, end = "2026-11-23T15:04:00Z", seconds = 240.0, to = mid)
        val second = leg("BUS", secondHalf, start = "2026-11-23T15:06:00Z", seconds = 240.0, from = mid)
        val itinerary = TravelTrackerPlanner { response(listOf(first, second), transfers = 1) }.plan(request("bus"))["itineraries"].items().single()
        assertEquals(1.0, itinerary.field("transferCount").number()!!, 0.0)
        assertEquals(600.0, itinerary.field("durationSeconds").number()!!, 0.0)
    }

    @Test fun legacyResponseFallbackDoesNotCountAnInterlinedSameVehicleAsATransfer() = runBlocking {
        val mid = place("Through platform", 45.5138, -73.5736)
        val first = leg("BUS", firstHalf, end = "2026-11-23T15:04:00Z", seconds = 240.0, to = mid)
        val second = JsonObject(leg("BUS", secondHalf, start = "2026-11-23T15:06:00Z", seconds = 240.0, from = mid) +
            ("interlineWithPreviousLeg" to JsonPrimitive(true)))
        val itinerary = TravelTrackerPlanner { response(listOf(first, second), transfers = null) }.plan(request("bus"))["itineraries"].items().single()
        assertEquals(0.0, itinerary.field("transferCount").number()!!, 0.0)
    }

    @Test fun invalidTransferCountIsAProviderFailureNotANoRouteResult() = runBlocking {
        rejectsProvider(response(listOf(leg("BUS")), transfers = -1))
    }

    @Test fun impossibleProviderStopCoordinatesAreNotBlamedOnValidUserInput() = runBlocking {
        rejectsProvider(response(listOf(leg("BUS", from = place("Bad provider point", 100.0, -73.5686)))))
    }

    @Test fun oneMissingProviderCoordinateDoesNotCreateAHybridInventedLocation() = runBlocking {
        val partial = obj("name" to "Partial point", "lat" to 45.5088)
        rejectsProvider(response(listOf(leg("BUS", from = partial))))
    }

    @Test fun providerStopFarFromItsGeometryIsRejected() = runBlocking {
        rejectsProvider(response(listOf(leg("BUS", from = place("Wrong platform", 44.0, -73.0)))))
    }

    @Test fun absentIntermediateStopMetadataStaysUnknownInsteadOfInventingAList() = runBlocking {
        val result = TravelTrackerPlanner { response() }.plan(request())
        assertEquals(JsonNull, result["itineraries"].items().single().field("legs").items().single().field("intermediateStops"))
    }

    @Test fun routerReportedUnavailableServiceDateIsExplicitDespiteLoadedRailRoutes() = runBlocking {
        val result = TravelTrackerPlanner(capabilities()) { noRoute("OUTSIDE_SERVICE_PERIOD") }.plan(request("train"))
        assertEquals("no_route", result["status"].text())
        assertEquals("outside_service_period", result["reason"].text())
        assertEquals("2027-01-03", result["coverage"].field("endDate").text())
    }

    @Test fun noNearbyTransitStopIsDistinctFromAMetadataOutage() = runBlocking {
        val result = TravelTrackerPlanner(capabilities()) { noRoute("NO_STOPS_IN_RANGE") }.plan(request("bus"))
        assertEquals("no_stops_in_range", result["reason"].text())
        assertEquals("no_route", result["status"].text())
    }

    @Test fun carDestinationOutsideTheLoadedStreetGraphIsCoverageRatherThanAnOutage() = runBlocking {
        val longRequest = JsonObject(request() + ("destination" to obj("lat" to 46.3432, "lon" to -72.5477)))
        val planner = TravelTrackerPlanner(capabilities()) { query ->
            assertEquals(46.3432, query["variables"].field("destinationLat").number()!!, 0.0)
            // Exact routing error observed from the Montreal graph for Montreal to Trois-Rivieres.
            obj("data" to obj("planConnection" to obj("routingErrors" to listOf(
                obj("code" to "OUTSIDE_BOUNDS", "inputField" to "TO")), "edges" to emptyList<Any>())))
        }
        val result = planner.plan(longRequest)
        assertEquals("no_route", result["status"].text())
        assertEquals("outside_coverage", result["reason"].text())
        assertTrue(result["itineraries"].items().isEmpty())
        assertEquals("America/Toronto", result["timeZone"].text())
        assertEquals("2026-11-23T10:00-05:00", result["requestedDepartureTime"].text())
    }

    @Test fun originOutsideTheStreetGraphIsAlsoReportedAsCoverage() = runBlocking {
        val raw = obj("data" to obj("planConnection" to obj("routingErrors" to listOf(
            obj("code" to "OUTSIDE_BOUNDS", "inputField" to "FROM")), "edges" to emptyList<Any>())))
        val result = TravelTrackerPlanner { raw }.plan(request())
        assertEquals("no_route", result["status"].text())
        assertEquals("outside_coverage", result["reason"].text())
        assertTrue(result["itineraries"].items().isEmpty())
    }

    @Test fun disconnectedCarRoadsAreNoRouteRatherThanAProviderOutage() = runBlocking {
        val result = TravelTrackerPlanner { noRoute("NO_DIRECT_MODE_CONNECTION") }.plan(request())
        assertEquals("no_route", result["status"].text())
        assertEquals("no_route", result["reason"].text())
        assertTrue(result["itineraries"].items().isEmpty())
    }

    @Test fun outsideCoverageDoesNotHideAnAdditionalUnknownProviderError() = runBlocking {
        val raw = obj("data" to obj("planConnection" to obj("routingErrors" to listOf(
            obj("code" to "OUTSIDE_BOUNDS"), obj("code" to "INTERNAL_FAILURE")), "edges" to emptyList<Any>())))
        rejectsProvider(raw, "car")
    }

    @Test fun unknownRoutingErrorCannotBecomeAMisleadingNoRouteSuccess() = runBlocking {
        rejectsProvider(noRoute("INTERNAL_FAILURE"))
    }

    @Test fun partialRealtimePredictionIsRejectedInsteadOfSilentlyRelabeledScheduled() = runBlocking {
        val partial = JsonObject(leg("BUS") + mapOf("realTime" to JsonPrimitive(true),
            "start" to obj("scheduledTime" to "2026-11-23T15:00:00Z", "estimated" to obj("time" to "2026-11-23T15:02:00Z"))))
        rejectsProvider(response(listOf(partial)))
    }

    @Test fun initialVehicleWaitIsIncludedInRequestedDepartureElapsedTimeWithoutChangingOtpDuration() = runBlocking {
        val delayed = JsonObject(leg("BUS", seconds = 660.0) + mapOf("realTime" to JsonPrimitive(true),
            "start" to obj("scheduledTime" to "2026-11-23T15:00:00Z", "estimated" to obj("time" to "2026-11-23T15:02:00Z")),
            "end" to obj("scheduledTime" to "2026-11-23T15:10:00Z", "estimated" to obj("time" to "2026-11-23T15:13:00Z"))))
        val itinerary = TravelTrackerPlanner { response(listOf(delayed), 660.0) }.plan(request("bus"))["itineraries"].items().single()
        assertEquals(120.0, itinerary.field("initialWaitSeconds").number()!!, 0.0)
        assertEquals(780.0, itinerary.field("elapsedFromRequestedDepartureSeconds").number()!!, 0.0)
        assertEquals(660.0, itinerary.field("durationSeconds").number()!!, 0.0)
        assertEquals("2026-11-23T15:02:00Z", itinerary.field("departureTime").text())
        assertEquals("2026-11-23T15:00:00Z", itinerary.field("legs").items().single().field("scheduledDepartureTime").text())
    }

    @Test fun transitDateUnionDoesNotIncorrectlyDisableRoadRoutingAfterGtfsExpiry() = runBlocking {
        val road = leg(start = "2028-11-23T15:00:00Z", end = "2028-11-23T15:10:00Z")
        val result = TravelTrackerPlanner(capabilities()) { response(listOf(road)) }.plan(request(departure = "2028-11-23T10:00:00-05:00"))
        assertEquals("success", result["status"].text())
        assertEquals("2027-01-03", result["coverage"].field("endDate").text())
    }
}
