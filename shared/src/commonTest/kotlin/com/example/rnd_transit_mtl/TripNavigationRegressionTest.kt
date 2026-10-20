package com.example.rnd_transit_mtl

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.example.rnd_transit_mtl.model.Trip
import com.example.rnd_transit_mtl.model.TripPoint
import com.example.rnd_transit_mtl.model.TripReview
import com.example.rnd_transit_mtl.model.TripReviewMode
import com.example.rnd_transit_mtl.model.TripTransportSnapshot
import com.example.rnd_transit_mtl.state.ReviewDraft
import com.example.rnd_transit_mtl.state.TripActionResult
import com.example.rnd_transit_mtl.state.TripsStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TripNavigationRegressionTest {
    @Test
    fun completionBoundaryBackPreservesThePrecedingPage() {
        val trip = fixtureTrip()
        val store = fixtureStore()
        complete(store, trip)

        val stack = NavBackStack<NavKey>(
            MainScreenKey,
            ProfileScreenKey,
            CurrentTripScreenKey(trip)
        )
        val navigator = Navigator(stack)
        val navigation = TripNavigation(navigator, store)

        assertTrue(navigation.back())

        assertEquals(
            listOf<NavKey>(
                MainScreenKey,
                ProfileScreenKey,
                HistoryScreenKey
            ),
            stack.toList()
        )
        assertEquals(1, store.completedTrips.size)
        assertNull(store.pendingReviewTripId)
        assertNull(store.findCompleted(trip.id)?.review)

        assertTrue(navigation.back())
        assertEquals(ProfileScreenKey, navigator.current)

        assertTrue(navigation.back())
        assertEquals(MainScreenKey, navigator.current)
        assertFalse(navigation.back())
    }

    @Test
    fun headerShortcutClosesInitialReviewBeforeOpeningAnotherSection() {
        val trip = fixtureTrip()
        val store = fixtureStore()
        complete(store, trip)

        val reviewKey = TripReviewScreenKey(
            tripId = trip.id,
            mode = TripReviewMode.INITIAL
        )
        val stack = NavBackStack<NavKey>(MainScreenKey, reviewKey)
        val navigator = Navigator(stack)
        val navigation = TripNavigation(navigator, store)

        assertEquals(TripActionResult.Applied, store.beginReview(trip.id))
        assertEquals(
            TripActionResult.Applied,
            store.updateReviewDraft(
                trip.id,
                ReviewDraft(overall = 3, comment = "Unsaved")
            )
        )

        assertTrue(navigation.openSection(ProfileScreenKey))

        assertEquals(
            listOf<NavKey>(
                MainScreenKey,
                HistoryScreenKey,
                ProfileScreenKey
            ),
            stack.toList()
        )
        assertNull(store.reviewDrafts[trip.id])
        assertNull(store.pendingReviewTripId)
        assertNull(store.findCompleted(trip.id)?.review)

        assertTrue(navigation.back())
        assertEquals(HistoryScreenKey, navigator.current)
        assertTrue(stack.none { it is TripReviewScreenKey })
    }

    @Test
    fun headerShortcutClosesEditingAndPreservesSavedReviewAndOrigin() {
        val trip = fixtureTrip()
        val store = fixtureStore()
        complete(store, trip)

        val savedReview = TripReview(overall = 4, comment = "Saved")
        assertEquals(
            TripActionResult.Applied,
            store.saveReview(trip.id, savedReview)
        )
        assertEquals(TripActionResult.Applied, store.beginReview(trip.id))
        assertEquals(
            TripActionResult.Applied,
            store.updateReviewDraft(
                trip.id,
                ReviewDraft(overall = 1, comment = "Unsaved")
            )
        )

        val detailsKey = TripDetailsScreenKey(trip.id)
        val reviewKey = TripReviewScreenKey(
            tripId = trip.id,
            mode = TripReviewMode.EDIT,
            origin = ReviewOrigin.Details(trip.id)
        )
        val stack = NavBackStack<NavKey>(
            MainScreenKey,
            HistoryScreenKey,
            detailsKey,
            reviewKey
        )
        val navigator = Navigator(stack)
        val navigation = TripNavigation(navigator, store)

        assertTrue(navigation.openSection(SettingsScreenKey))

        assertEquals(
            listOf<NavKey>(
                MainScreenKey,
                HistoryScreenKey,
                detailsKey,
                SettingsScreenKey
            ),
            stack.toList()
        )
        assertEquals(savedReview, store.findCompleted(trip.id)?.review)
        assertNull(store.reviewDrafts[trip.id])

        assertTrue(navigation.back())
        assertEquals(detailsKey, navigator.current)
        assertTrue(stack.none { it is TripReviewScreenKey })
    }

    private fun fixtureStore(): TripsStore =
        TripsStore(nowEpochMillis = { 12_000L })

    private fun complete(store: TripsStore, trip: Trip) {
        assertEquals(TripActionResult.Applied, store.start(trip))
        assertEquals(
            TripActionResult.Applied,
            store.updateElapsed(trip.id, 10_000L)
        )
    }

    private fun fixtureTrip(): Trip = Trip(
        id = "navigation-regression-trip",
        title = "Navigation regression",
        description = "Fixed record for navigation boundary verification.",
        imageUrl = "https://example.com/reference.jpg",
        start = TripPoint("Start", 0.20f, 0.30f),
        destination = TripPoint("Destination", 0.80f, 0.70f),
        plannedMinutes = 30,
        selectedTransports = listOf(
            TripTransportSnapshot("walk", "Walk", false)
        ),
        selectedRoutes = emptyList(),
        attractionIntensity = 65f,
        distanceKm = 2.4,
        createdAtEpochMillis = 1_000L
    )
}
