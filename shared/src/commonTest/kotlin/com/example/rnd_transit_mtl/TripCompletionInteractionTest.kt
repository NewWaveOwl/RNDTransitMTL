package com.example.rnd_transit_mtl

import com.example.rnd_transit_mtl.model.ids.TripId
import com.example.rnd_transit_mtl.model.ids.TransportId
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.example.rnd_transit_mtl.data.generation.MockTripGenerator
import com.example.rnd_transit_mtl.data.generation.TripGenerationInput
import com.example.rnd_transit_mtl.data.generation.TripGenerationResult
import com.example.rnd_transit_mtl.model.transport.TransportType
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.model.review.TripReviewMode
import com.example.rnd_transit_mtl.state.trip.TripActionResult
import com.example.rnd_transit_mtl.state.trip.TripsStore
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.example.rnd_transit_mtl.navigation.CurrentTripScreenKey
import com.example.rnd_transit_mtl.navigation.HistoryScreenKey
import com.example.rnd_transit_mtl.navigation.MainScreenKey
import com.example.rnd_transit_mtl.navigation.Navigator
import com.example.rnd_transit_mtl.navigation.TripNavigation
import com.example.rnd_transit_mtl.navigation.TripReviewScreenKey

class TripCompletionInteractionTest {
    @Test
    fun bundledMapTripNeedsNoReferenceUrl() {
        val trip = generatedTrip()
        assertEquals("", trip.imageUrl)
        assertTrue(trip.start.normalizedDistanceTo(trip.destination) >=
            MockTripGenerator.MIN_NORMALIZED_SEPARATION)
        assertEquals(30, trip.plannedMinutes)
        assertEquals(listOf(TransportId("walk")), trip.selectedTransports.map { it.id })
    }

    @Test
    fun completionWaitsForReviewActionAndRepeatedRequestsDoNotDuplicate() {
        val trip = generatedTrip()
        val store = TripsStore(nowEpochMillis = { 20_000L })
        val stack = NavBackStack<NavKey>(MainScreenKey)
        val navigator = Navigator(stack)
        val navigation = TripNavigation(navigator, store)

        assertEquals(TripActionResult.Applied, store.start(trip))
        assertTrue(navigation.openCurrentTrip(trip))
        assertEquals(TripActionResult.Applied, store.updateElapsed(trip.id, 10_000L))
        assertEquals(CurrentTripScreenKey(trip), navigator.current)
        assertEquals(1f, store.progressFor(trip.id))
        assertEquals(trip.id, store.pendingReviewTripId)
        assertEquals(1, store.completedTrips.size)
        assertNull(store.activeTrip)

        // This explicit call represents pressing the completed progress bar.
        assertTrue(navigation.openInitialReview(trip.id))
        assertTrue(navigation.openInitialReview(trip.id))
        val review = TripReviewScreenKey(trip.id, TripReviewMode.INITIAL)
        assertEquals(review, navigator.current)
        assertEquals(1, stack.count { it == review })
        assertNull(store.pendingReviewTripId)
        assertEquals(1, store.completedTrips.size)

        assertEquals(TripActionResult.Applied, store.skipReview(trip.id))
        assertTrue(navigation.finishReview(review))
        assertEquals(HistoryScreenKey, navigator.current)
        assertNull(store.completedTrips.single().review)
        assertEquals(trip.id, store.completedTrips.single().id)
    }

    @Test
    fun restoredCompletionReopensAt100WithoutRestartingOrOpeningReview() {
        val trip = generatedTrip()
        val original = TripsStore(nowEpochMillis = { 20_000L })
        original.start(trip)
        original.updateElapsed(trip.id, 10_000L)

        val restored = assertNotNull(TripsStore.fromSavedStateJson(
            original.toSavedStateJson(), nowEpochMillis = { 30_000L }
        ))
        val stack = NavBackStack<NavKey>(MainScreenKey)
        val navigator = Navigator(stack)
        val navigation = TripNavigation(navigator, restored)

        assertTrue(navigation.openCompletedTrip(trip.id))
        val current = assertIs<CurrentTripScreenKey>(navigator.current)
        assertEquals(trip.id, current.trip.id)
        assertEquals(trip.start, current.trip.start)
        assertEquals(trip.destination, current.trip.destination)
        assertEquals(1f, restored.progressFor(trip.id))
        assertNull(restored.activeTrip)
        assertEquals(trip.id, restored.pendingReviewTripId)
        assertTrue(stack.none { it is TripReviewScreenKey })
        assertTrue(navigation.openCompletedTrip(trip.id))
        assertEquals(1, stack.count { it is CurrentTripScreenKey })
        assertEquals(1, restored.completedTrips.size)

        restored.removeCompleted(trip.id)
        assertFalse(navigation.openCompletedTrip(trip.id))
        assertFalse(navigation.openInitialReview(trip.id))
        assertNull(restored.pendingReviewTripId)
        assertTrue(restored.completedTrips.isEmpty())
    }

    @Test
    fun informationPopupRequiresCurrentTripAndClosesWhenReturningToGO() {
        val trip = generatedTrip()
        val store = TripsStore(nowEpochMillis = { 20_000L })
        val stack = NavBackStack<NavKey>(MainScreenKey)
        val navigator = Navigator(stack)
        val navigation = TripNavigation(navigator, store)

        store.start(trip)
        assertFalse(navigation.requestTripInformation())
        assertNull(navigation.informationTripId)
        assertTrue(navigation.openCurrentTrip(trip))
        assertNull(navigation.informationTripId)
        assertTrue(navigation.requestTripInformation())
        assertEquals(trip.id, navigation.informationTripId)
        assertEquals(trip, store.activeTrip)

        // A stale popup for another ID cannot dismiss the current popup.
        navigation.dismissTripInformation(TripId("different-trip"))
        assertEquals(trip.id, navigation.informationTripId)
        assertTrue(navigation.returnToPlanner())
        assertNull(navigation.informationTripId)
        assertEquals(MainScreenKey, navigator.current)
        assertEquals(trip.id, store.activeTrip?.id)
        assertTrue(store.completedTrips.isEmpty())
    }

    private fun generatedTrip(): Trip =
        assertIs<TripGenerationResult.Success>(
            MockTripGenerator(
                random = Random(7),
                idFactory = { "bundled-map-test" },
                nowEpochMillis = { 1_000L }
            ).generate(
                input = TripGenerationInput(
                    title = "Trip 1",
                    description = "Random mock route with 30 planned minutes.",
                    imageUrl = "",
                    plannedMinutes = 30,
                    selectedTransportIds = listOf(TransportId("walk")),
                    selectedRouteIds = emptyList(),
                    attractionIntensity = 90f
                ),
                transportTypes = listOf(TransportType(TransportId("walk"), "Walk", false)),
                transportRoutes = emptyList(),
                existingTripIds = emptySet()
            )
        ).trip
}
