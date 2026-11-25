package com.example.rnd_transit_mtl

import com.example.rnd_transit_mtl.ViewModels.current_trip.*
import com.example.rnd_transit_mtl.data.generation.*
import com.example.rnd_transit_mtl.data.location.LocationUpdate
import com.example.rnd_transit_mtl.data.remote.dto.toDto
import com.example.rnd_transit_mtl.data.remote.dto.toDomainOrNull
import com.example.rnd_transit_mtl.model.geo.GeoPoint
import com.example.rnd_transit_mtl.model.ids.TripId
import com.example.rnd_transit_mtl.model.ids.TransportId
import com.example.rnd_transit_mtl.model.trip.*
import com.example.rnd_transit_mtl.state.trip.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

/** Return proposals never mutate the saved original or its pending review before acceptance. */
@OptIn(ExperimentalCoroutinesApi::class)
class ReturnTripViewModelTest {
    private fun completedStore() = TripsStore { 100_000L }.also {
        assertEquals(TripActionResult.Applied, it.start(guidanceTestTrip()))
        assertEquals(TripActionResult.Applied, it.updateElapsed(guidanceTestTrip().id, TRIP_SIMULATION_DURATION_MILLIS))
    }
    private fun proposal(input: TripGenerationInput, id: String = "return-1"): Trip {
        val origin = requireNotNull(input.originPlace)
        val destination = requireNotNull(input.destinationPlace)
        val walk = TransportId("walk")
        val bend = GeoPoint((origin.point.latitude + destination.point.latitude) / 2, origin.point.longitude + .001)
        return Trip(TripId(id), status = TripStatus.Generated, title = input.title, description = input.description,
            imageUrl = "", origin = origin, destination = destination,
            legs = listOf(RouteLeg(walk, geometry = listOf(origin.point, bend, destination.point),
                distanceMeters = 500.0, durationSeconds = 600, isRouted = true)),
            plannedMinutes = input.plannedMinutes,
            selectedTransports = (input.selectedTransportIds + walk).distinct().map { id ->
                val transport = ReturnTransportCatalogue.first { it.id == id }
                TripTransportSnapshot(id, transport.label, transport.usesRoutes)
            }, selectedRoutes = emptyList(), attractionIntensity = input.attractionIntensity,
            createdAt = 100_000, routedDurationSeconds = 600,
            selectedZoneIds = input.selectedZoneIds, selectedZonesByTransport = input.selectedZonesByTransport,
            requestedTransportIds = input.selectedTransportIds)
    }

    @Test fun failureRetryAndStartKeepTheCompletedOutboundIdentityAndOrigin() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val store = completedStore()
            val original = assertNotNull(store.findCompleted(guidanceTestTrip().id))
            val before = store.toSavedStateJson()
            var calls = 0
            val model = ReturnTripViewModel(store, original.id, { input, _ ->
                assertEquals(original.origin, input.destinationPlace)
                assertEquals(original.destination, input.originPlace)
                assertTrue(input.compareModes)
                if (++calls == 1) TripGenerationResult.NoRoute else TripGenerationResult.Success(proposal(input))
            }) { 100_000 }
            model.findReturn(ReturnTransportOption.Fastest)
            advanceUntilIdle()
            assertEquals(ReturnPhase.Unavailable, model.state.value.phase)
            assertEquals(before, store.toSavedStateJson())
            model.findReturn(ReturnTransportOption.Fastest)
            advanceUntilIdle()
            assertEquals(ReturnPhase.Ready, model.state.value.phase)
            assertEquals(before, store.toSavedStateJson())
            val route = assertNotNull(model.state.value.proposal)
            assertEquals(original.id, route.journey?.outboundTripId)
            assertEquals(TripActionResult.Applied, model.startReturn())
            assertEquals(TripActionResult.Applied, model.startReturn(), "Repeated Start only reopens the same return")
            assertEquals(route.id, store.activeTrip?.id)
            assertNull(store.pendingReviewTripId)
            assertEquals(original, store.findCompleted(original.id))
            assertEquals(route, Json.decodeFromString<Trip>(Json.encodeToString(route)))
            assertEquals(route, route.toDto().toDomainOrNull())
        } finally { Dispatchers.resetMain() }
    }

    @Test fun cancelIgnoresLateProviderResultsAndPreservesAllOutboundState() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val store = completedStore()
            val before = store.toSavedStateJson()
            val response = CompletableDeferred<Unit>()
            var calls = 0
            val model = ReturnTripViewModel(store, guidanceTestTrip().id, { input, _ ->
                calls++
                withContext(NonCancellable) { response.await() }
                TripGenerationResult.Success(proposal(input))
            })
            model.findReturn(ReturnTransportOption.SameTransport)
            runCurrent()
            model.findReturn(ReturnTransportOption.SameTransport)
            assertEquals(1, calls, "A pending comparison cannot submit duplicate routing requests")
            model.cancelReturn()
            response.complete(Unit)
            advanceUntilIdle()
            assertEquals(ReturnPhase.Arrival, model.state.value.phase)
            assertNull(model.state.value.proposal)
            assertEquals(before, store.toSavedStateJson())
        } finally { Dispatchers.resetMain() }
    }

    @Test fun sameTransportRetainsZonesAndAnInvalidReturnCannotClearTheReview() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val store = completedStore()
            val original = assertNotNull(store.findCompleted(guidanceTestTrip().id))
            val before = store.toSavedStateJson()
            val model = ReturnTripViewModel(store, original.id, { input, _ ->
                assertFalse(input.compareModes)
                assertEquals(original.selectedTransports.map { it.id }, input.selectedTransportIds)
                assertTrue(input.selectedRouteIds.isEmpty(), "Return routing must choose valid lines in the opposite direction")
                TripGenerationResult.Success(proposal(input))
            })
            model.findReturn(ReturnTransportOption.SameTransport,
                LocationUpdate.Fix(GeoPoint(45.6, -73.6), 5.0, 1))
            advanceUntilIdle()
            val route = assertNotNull(model.state.value.proposal)
            val invalid = route.copy(destination = route.destination.copy(label = "Different destination"))
            assertIs<TripActionResult.InvalidInput>(store.startReturn(invalid, original.id))
            assertEquals(before, store.toSavedStateJson())
            assertEquals(TripActionResult.Applied, store.startReturn(route, original.id))
        } finally { Dispatchers.resetMain() }
    }

    @Test fun aFreshFixAtTheOriginalStartDoesNotGenerateAnotherRoute() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val store = completedStore()
            val original = assertNotNull(store.findCompleted(guidanceTestTrip().id))
            val before = store.toSavedStateJson()
            val model = ReturnTripViewModel(store, original.id, { _, _ -> error("Already at start") }) { 100_000 }
            model.findReturn(ReturnTransportOption.Fastest, LocationUpdate.Fix(original.origin.point, 5.0, 99_000))
            assertEquals(ReturnPhase.AtStart, model.state.value.phase)
            assertEquals(before, store.toSavedStateJson())
        } finally { Dispatchers.resetMain() }
    }
}
