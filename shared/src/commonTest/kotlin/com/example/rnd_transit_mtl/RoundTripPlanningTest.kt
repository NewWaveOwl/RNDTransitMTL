package com.example.rnd_transit_mtl

import com.example.rnd_transit_mtl.ViewModels.main.*
import com.example.rnd_transit_mtl.ViewModels.current_trip.CurrentTripLocationViewModel
import com.example.rnd_transit_mtl.data.location.LocationUpdate
import com.example.rnd_transit_mtl.state.trip.TripsStore
import com.example.rnd_transit_mtl.data.generation.*
import com.example.rnd_transit_mtl.data.remote.dto.*
import com.example.rnd_transit_mtl.data.routing.*
import com.example.rnd_transit_mtl.model.geo.GeoPoint
import com.example.rnd_transit_mtl.model.ids.TransportId
import com.example.rnd_transit_mtl.model.transport.TransportType
import com.example.rnd_transit_mtl.model.trip.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlin.test.*

/** Closed journeys preserve two independently routed directions and legacy one-way restoration. */
class RoundTripPlanningTest {
    private val start = Place("Original pin", GeoPoint(45.5, -73.6))
    private val away = Place("Turnaround", GeoPoint(45.505, -73.6))
    private val walk = TransportId("walk")
    private val types = listOf(TransportType(walk, "Walk", false))
    private val input = TripGenerationInput("Round adventure", "Out and back", "", 30,
        listOf(walk), emptyList(), 50f, originPlace = start, roundTrip = true)
    private fun otp(from: Place, to: Place, longitude: Double): JsonObject {
        var lat = 0; var lon = 0
        fun delta(number: Int): String {
            var value = if (number < 0) (number shl 1).inv() else number shl 1
            return buildString {
                while (value >= 32) { append(((32 or (value and 31)) + 63).toChar()); value = value ushr 5 }
                append((value + 63).toChar())
            }
        }
        val points = listOf(from.point, GeoPoint(45.5025, longitude), to.point)
        val polyline = points.joinToString("") {
            val a = kotlin.math.round(it.latitude * 1e5).toInt(); val b = kotlin.math.round(it.longitude * 1e5).toInt()
            val text = delta(a - lat) + delta(b - lon); lat = a; lon = b; text
        }
        return buildJsonObject { put("data", buildJsonObject { put("planConnection", buildJsonObject {
            put("routingErrors", JsonArray(emptyList()))
            put("edges", buildJsonArray { add(buildJsonObject { put("node", buildJsonObject {
                put("duration", 600); put("waitingTime", 0)
                put("legs", buildJsonArray { add(buildJsonObject {
                    put("mode", "WALK"); put("distance", 700); put("duration", 600)
                    put("legGeometry", buildJsonObject { put("length", 3); put("points", polyline) })
                }) })
            }) }) })
        }) }) }
    }
    private fun response() = BackendGenerationResponse("success", origin = start, destination = start,
        elapsedSeconds = 1284, otp = otp(start, away, -73.601), travelTimeSeconds = 1200,
        orientationBufferSeconds = 84, roundTrip = true, turnaround = away,
        returnOtp = otp(away, start, -73.602))

    @Test fun acceptsBothDirectionsAndRestoresTheirJourneyAndPlannerChoice() = runTest {
        var failure: Exception? = null
        val result = BackendTripGenerator(post = { body, _ ->
            assertTrue(Json.decodeFromString<BackendGenerationRequest>(body).roundTrip)
            assertEquals(1, Json.parseToJsonElement(body).jsonObject["interestZoneCount"]?.jsonPrimitive?.int)
            Json.encodeToString(response())
        }, reportFailure = { failure = it }).generate(input, types, emptySet())
        val trip = assertIs<TripGenerationResult.Success>(result, failure?.stackTraceToString()).trip
        assertEquals(start, trip.destination)
        assertEquals(away, trip.journey?.turnaround)
        assertEquals(2, trip.legs.size)
        assertTrue(trip.legs.all { it.isRouted })
        assertNotEquals(trip.legs.first().geometry.reversed(), trip.legs.last().geometry)
        assertEquals(trip, trip.toDto().toDomainOrNull())
        assertEquals(trip, Json.decodeFromString<Trip>(Json.encodeToString(trip)))
        assertEquals(0f, trip.routePosition(start.point)?.progress)
        assertEquals(1f, trip.routePosition(start.point, 0.99f)?.progress)
        val store = TripsStore { trip.createdAt }.also { it.start(trip) }
        CurrentTripLocationViewModel(trip, store, nowEpochMillis = { trip.createdAt })
            .receive(LocationUpdate.Fix(start.point, 8.0, timestampEpochMillis = trip.createdAt))
        assertNotNull(store.activeTrip, "The original start is also the finish; the first GPS fix must not complete the loop.")
        assertTrue(store.completedTrips.isEmpty())
        val planner = MainPlannerViewModel()
        planner.copyTripPreferences(trip, liveMap = true)
        assertTrue(planner.uiState.value.roundTrip)
        assertEquals(start, planner.uiState.value.originPlace)
        val restored = Json.decodeFromString<MainPlannerState>(Json.encodeToString(planner.uiState.value))
        assertTrue(MainPlannerViewModel(restored).uiState.value.roundTrip)
        assertFalse(Json.decodeFromString<MainPlannerState>("{}").roundTrip)
    }

    @Test fun missingWrongOrOverBudgetReturnIsRejected() = runTest {
        for (invalid in listOf(response().copy(returnOtp = null), response().copy(destination = away),
            response().copy(roundTrip = false), response().copy(elapsedSeconds = 1801))) {
            assertIs<TripGenerationResult.RoutingUnavailable>(BackendTripGenerator(post = { _, _ -> Json.encodeToString(invalid) })
                .generate(input, types, emptySet()))
        }
        assertIs<TripGenerationResult.RoutingUnavailable>(BackendTripGenerator(post = { _, _ -> Json.encodeToString(response()) })
            .generate(input.copy(roundTrip = false), types, emptySet()))
    }

    @Test fun sampleAlsoRequestsBothDirectionsAndSplitsItsAvailableTime() = runTest {
        val requests = mutableListOf<TripRequest>()
        val generator = MockTripGenerator(routingService = object : RoutingService {
            override suspend fun route(request: TripRequest): RoutingResult {
                requests += request
                assertTrue(request.validate(setOf(walk)).isEmpty())
                return RoutingResult.Success(listOf(RouteLeg.straightLine(walk, request.origin.point,
                    requireNotNull(request.destination).point, request.durationBudgetSeconds ?: request.plannedMinutes * 60L)))
            }
        })
        val trip = assertIs<TripGenerationResult.Success>(generator.generate(input.copy(plannedMinutes = 5),
            types, emptyList(), emptySet())).trip
        assertEquals(2, requests.size)
        assertEquals(requests.first().origin, requests.last().destination)
        assertEquals(requests.first().destination, requests.last().origin)
        assertEquals(300L, trip.legs.sumOf { it.durationSeconds })
        assertEquals(TripJourneyKind.RoundTrip, trip.journey?.kind)
        assertTrue(trip.legs.none { it.isRouted })
        val defaultSample = assertIs<TripGenerationResult.Success>(MockTripGenerator().generate(
            input.copy(plannedMinutes = 5, originPlace = null), types, emptyList(), emptySet())).trip
        assertEquals(300L, defaultSample.legs.sumOf { it.durationSeconds })
        assertTrue(TripRequest(null, start, away, 5, listOf(walk), durationBudgetSeconds = 301)
            .validate(setOf(walk)).any { it is TripRequestError.InvalidDurationBudget })
    }
    @Test fun legacySingleTurnaroundCannotSatisfyAMultiStopRequest() = runTest {
        assertIs<TripGenerationResult.RoutingUnavailable>(BackendTripGenerator(post = { _, _ -> Json.encodeToString(response()) })
            .generate(input.copy(interestZoneCount = 2), types, emptySet()))
    }

    @Test fun selectedInterestBoxesDetermineExactCountWithOneAsTheMinimum() {
        assertEquals(1, explorationZoneCount(0))
        (1..6).forEach { assertEquals(it, explorationZoneCount(it)) }
    }

    @Test fun multiStopLoopIsDecodedSavedAndRejectsMissingOrReorderedSegments() = runTest {
        val second = Place("Exploration zone 2", GeoPoint(45.505, -73.59))
        val segments = listOf(BackendLoopSegment(start, away, otp(start, away, -73.601)),
            BackendLoopSegment(away, second, otp(away, second, -73.595)),
            BackendLoopSegment(second, start, otp(second, start, -73.592)))
        val loop = BackendGenerationResponse("success", origin = start, destination = start,
            elapsedSeconds = 1926, travelTimeSeconds = 1800, orientationBufferSeconds = 126,
            roundTrip = true, turnaround = away, waypoints = listOf(away, second), segments = segments)
        val loopInput = input.copy(plannedMinutes = 40, interestZoneCount = 2)
        val generator = BackendTripGenerator(post = { body, _ ->
            assertEquals(2, Json.decodeFromString<BackendGenerationRequest>(body).interestZoneCount)
            Json.encodeToString(loop)
        })
        val trip = assertIs<TripGenerationResult.Success>(generator.generate(loopInput, types, emptySet())).trip
        assertEquals(listOf(away, second), trip.explorationWaypoints)
        assertEquals(start, trip.destination)
        assertEquals(3, trip.legs.size)
        assertEquals(trip, trip.toDto().toDomainOrNull())
        assertEquals(trip, Json.decodeFromString<Trip>(Json.encodeToString(trip)))
        for (bad in listOf(loop.copy(segments = segments.dropLast(1)),
            loop.copy(segments = segments.reversed()), loop.copy(waypoints = listOf(away, away)))) {
            assertIs<TripGenerationResult.RoutingUnavailable>(BackendTripGenerator(post = { _, _ -> Json.encodeToString(bad) })
                .generate(loopInput, types, emptySet()))
        }
    }

    @Test fun offlineSamplesAlsoVisitTheRequestedNumberOfStops() = runTest {
        val trip = assertIs<TripGenerationResult.Success>(MockTripGenerator().generate(
            input.copy(plannedMinutes = 5, originPlace = null, interestZoneCount = 3), types, emptyList(), emptySet())).trip
        assertEquals(3, trip.explorationWaypoints.size)
        assertEquals(4, trip.legs.size)
        assertEquals(trip.origin, trip.destination)
        assertEquals(300L, trip.legs.sumOf { it.durationSeconds })
    }

}
