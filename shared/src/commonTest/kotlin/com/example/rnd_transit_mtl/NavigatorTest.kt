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
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NavigatorTest {
    @Test
    fun aboutReturnsToProfileThenHomeAndRootCannotBePopped() {
        val stack = NavBackStack<NavKey>(MainScreenKey)
        val navigator = Navigator(stack)

        navigator.navigate(ProfileScreenKey)
        navigator.navigate(AboutScreenKey)
        assertTrue(navigator.hasPrevious())

        navigator.pop()
        assertEquals(ProfileScreenKey, navigator.current)

        navigator.pop()
        navigator.pop()
        assertEquals(listOf<NavKey>(MainScreenKey), stack.toList())
        assertFalse(navigator.hasPrevious())
    }

    @Test
    fun tripSettingsReturnsHomeAndRemovesIntermediatePages() {
        val stack = NavBackStack<NavKey>(
            MainScreenKey,
            ProfileScreenKey,
            AboutScreenKey,
            SettingsScreenKey
        )
        val navigator = Navigator(stack)

        navigator.popUntil(MainScreenKey)

        assertEquals(listOf<NavKey>(MainScreenKey), stack.toList())
        assertFalse(navigator.hasPrevious())
    }

    @Test
    fun popUntilMissingKeyLeavesNavigationUnchanged() {
        val stack = NavBackStack<NavKey>(
            MainScreenKey,
            HistoryScreenKey
        )

        Navigator(stack).popUntil(AboutScreenKey)

        assertEquals(
            listOf<NavKey>(MainScreenKey, HistoryScreenKey),
            stack.toList()
        )
    }

    @Test
    fun popUntilKeepsTheMostRecentMatchingEntry() {
        val stack = NavBackStack<NavKey>(
            MainScreenKey,
            ProfileScreenKey,
            AboutScreenKey,
            ProfileScreenKey,
            HistoryScreenKey
        )

        Navigator(stack).popUntil(ProfileScreenKey)

        assertEquals(
            listOf<NavKey>(
                MainScreenKey,
                ProfileScreenKey,
                AboutScreenKey,
                ProfileScreenKey
            ),
            stack.toList()
        )
    }

    @Test
    fun replaceKeepsPreviousPageForBackNavigation() {
        val stack = NavBackStack<NavKey>(
            MainScreenKey,
            SettingsScreenKey
        )
        val navigator = Navigator(stack)

        navigator.replace(HistoryScreenKey)
        assertEquals(
            listOf<NavKey>(MainScreenKey, HistoryScreenKey),
            stack.toList()
        )

        navigator.pop()
        assertEquals(MainScreenKey, navigator.current)
    }

    @Test
    fun replaceCanInitializeAnEmptyStack() {
        val stack = NavBackStack<NavKey>()
        val navigator = Navigator(stack)

        navigator.replace(MainScreenKey)

        assertEquals(MainScreenKey, navigator.current)
        assertFalse(navigator.hasPrevious())
    }

    @Test
    fun replaceCannotRemoveRootAndRepeatedTapsDoNotDuplicateTop() {
        val stack = NavBackStack<NavKey>(MainScreenKey)
        val navigator = Navigator(stack)

        navigator.replace(HistoryScreenKey)
        navigator.navigate(HistoryScreenKey)
        navigator.open(HistoryScreenKey)

        assertEquals(
            listOf<NavKey>(MainScreenKey, HistoryScreenKey),
            stack.toList()
        )

        navigator.pop()
        assertEquals(MainScreenKey, navigator.current)
    }

    @Test
    fun startPauseResumeCompletionAndSkipUseOneTripAndNoReplayEntry() {
        val trip = testTrip()
        val store = testStore()
        val stack = NavBackStack<NavKey>(MainScreenKey)
        val navigator = Navigator(stack)
        val navigation = TripNavigation(navigator, store)

        assertEquals(TripActionResult.Applied, store.start(trip))
        assertTrue(navigation.openCurrentTrip(trip))
        assertTrue(navigation.openCurrentTrip(trip))
        assertEquals(
            listOf<NavKey>(MainScreenKey, CurrentTripScreenKey(trip)),
            stack.toList()
        )

        store.updateElapsed(trip.id, 5_000L)
        navigation.back()

        assertEquals(MainScreenKey, navigator.current)
        assertEquals(5_000L, store.elapsedMillis)
        assertTrue(navigation.resumeActiveTrip())
        assertEquals(trip.id, store.activeTrip?.id)
        assertEquals(5_000L, store.elapsedMillis)

        store.updateElapsed(trip.id, 10_000L)
        assertTrue(navigation.openInitialReview(trip.id))
        assertTrue(navigation.openInitialReview(trip.id))

        val reviewKey = TripReviewScreenKey(
            trip.id,
            TripReviewMode.INITIAL
        )
        assertEquals(
            listOf<NavKey>(MainScreenKey, reviewKey),
            stack.toList()
        )
        assertEquals(1, store.completedTrips.size)
        assertNull(store.activeTrip)
        assertNull(store.pendingReviewTripId)

        navigation.back()

        assertEquals(
            listOf<NavKey>(MainScreenKey, HistoryScreenKey),
            stack.toList()
        )
        assertNull(store.findCompleted(trip.id)?.review)

        navigation.back()
        assertEquals(MainScreenKey, navigator.current)
        assertEquals(1, store.completedTrips.size)
    }

    @Test
    fun editingFromDetailsSavesIntoSameIdAndCancelPreservesSavedReview() {
        val trip = testTrip()
        val store = testStore()
        completeTrip(store, trip)
        store.skipReview(trip.id)
        store.saveReview(trip.id, TripReview(overall = 4))

        val stack = NavBackStack<NavKey>(
            MainScreenKey,
            HistoryScreenKey
        )
        val navigator = Navigator(stack)
        val navigation = TripNavigation(navigator, store)

        assertTrue(navigation.openDetails(trip.id))
        val details = TripDetailsScreenKey(trip.id)

        assertTrue(
            navigation.openReview(
                trip.id,
                ReviewOrigin.Details(trip.id)
            )
        )
        val edit = assertNotNull(
            navigator.current as? TripReviewScreenKey
        )

        store.beginReview(trip.id)
        store.updateReviewDraft(
            trip.id,
            ReviewDraft(overall = 5, comment = "Updated review")
        )
        assertEquals(4, store.findCompleted(trip.id)?.review?.overall)

        store.saveReview(trip.id)
        assertTrue(navigation.finishReview(edit))
        assertEquals(details, navigator.current)
        assertEquals(1, store.completedTrips.size)
        assertEquals(5, store.findCompleted(trip.id)?.review?.overall)

        navigation.openReview(
            trip.id,
            ReviewOrigin.Details(trip.id)
        )
        store.beginReview(trip.id)
        store.updateReviewDraft(
            trip.id,
            ReviewDraft(overall = 1, comment = "Unsaved")
        )

        navigation.back()

        assertEquals(details, navigator.current)
        assertEquals(5, store.findCompleted(trip.id)?.review?.overall)
        assertEquals(
            "Updated review",
            store.findCompleted(trip.id)?.review?.comment
        )
        assertFalse(trip.id in store.reviewDrafts)
    }

    @Test
    fun deletingAnEditedTripRecoversToHistoryWithoutRecreatingIt() {
        val trip = testTrip()
        val store = testStore()
        completeTrip(store, trip)
        store.skipReview(trip.id)

        val stack = NavBackStack<NavKey>(
            MainScreenKey,
            HistoryScreenKey
        )
        val navigator = Navigator(stack)
        val navigation = TripNavigation(navigator, store)

        navigation.openDetails(trip.id)
        navigation.openReview(
            trip.id,
            ReviewOrigin.Details(trip.id)
        )
        val edit = assertNotNull(
            navigator.current as? TripReviewScreenKey
        )

        store.removeCompleted(trip.id)
        assertTrue(
            store.saveReview(trip.id, TripReview(5)) is
                    TripActionResult.MissingTrip
        )
        assertTrue(navigation.finishReview(edit))

        assertEquals(
            listOf<NavKey>(MainScreenKey, HistoryScreenKey),
            stack.toList()
        )
        assertNull(store.findTrip(trip.id))
        assertFalse(navigation.openDetails(trip.id))
    }

    @Test
    fun cancellationReturnsHomeWithoutAddingHistory() {
        val trip = testTrip()
        val store = testStore()
        val stack = NavBackStack<NavKey>(MainScreenKey)
        val navigator = Navigator(stack)
        val navigation = TripNavigation(navigator, store)

        store.start(trip)
        navigation.openCurrentTrip(trip)
        store.updateElapsed(trip.id, 3_000L)
        store.cancel(trip.id)
        navigation.returnToPlanner()

        assertEquals(listOf<NavKey>(MainScreenKey), stack.toList())
        assertNull(store.activeTrip)
        assertTrue(store.completedTrips.isEmpty())
        assertFalse(navigation.resumeActiveTrip())
    }

    @Test
    fun restoredPendingCompletionOpensReviewOnceAndPrunesFinishedEntry() {
        val trip = testTrip()
        val originalStore = testStore()
        completeTrip(originalStore, trip)

        val restoredStore = assertNotNull(
            TripsStore.fromSavedStateJson(
                originalStore.toSavedStateJson(),
                nowEpochMillis = { 20_000L }
            )
        )
        val stack = NavBackStack<NavKey>(
            MainScreenKey,
            CurrentTripScreenKey(trip),
            HistoryScreenKey
        )
        val navigator = Navigator(stack)
        val navigation = TripNavigation(navigator, restoredStore)

        assertTrue(navigation.recoverPendingReview())
        assertFalse(navigation.recoverPendingReview())

        assertEquals(
            listOf<NavKey>(
                MainScreenKey,
                HistoryScreenKey,
                TripReviewScreenKey(trip.id, TripReviewMode.INITIAL)
            ),
            stack.toList()
        )
        assertEquals(1, restoredStore.completedTrips.size)
        assertNull(restoredStore.pendingReviewTripId)

        navigation.back()
        assertEquals(HistoryScreenKey, navigator.current)
        assertTrue(stack.none { it is CurrentTripScreenKey })
    }

    @Test
    fun restoredReviewDestinationAcknowledgesPendingWithoutAnotherPush() {
        val trip = testTrip()
        val store = testStore()
        completeTrip(store, trip)

        val key = TripReviewScreenKey(
            trip.id,
            TripReviewMode.INITIAL
        )
        val stack = NavBackStack<NavKey>(MainScreenKey, key)
        val navigation = TripNavigation(Navigator(stack), store)

        assertTrue(navigation.recoverPendingReview())
        assertEquals(listOf<NavKey>(MainScreenKey, key), stack.toList())
        assertNull(store.pendingReviewTripId)
    }

    @Test
    fun outgoingBackRegistrationCannotRemoveNewerRegistration() {
        val trip = testTrip()
        val store = testStore()
        completeTrip(store, trip)
        store.skipReview(trip.id)

        val key = TripReviewScreenKey(
            trip.id,
            TripReviewMode.EDIT
        )
        val stack = NavBackStack<NavKey>(
            MainScreenKey,
            HistoryScreenKey,
            key
        )
        val navigation = TripNavigation(Navigator(stack), store)
        var oldCalls = 0
        var newCalls = 0

        val disposeOld = navigation.registerReviewBackHandler(key) {
            oldCalls++
        }
        val disposeNew = navigation.registerReviewBackHandler(key) {
            newCalls++
        }

        disposeOld()
        navigation.back()

        assertEquals(0, oldCalls)
        assertEquals(1, newCalls)
        assertEquals(key, stack.last())

        disposeNew()
        navigation.back()
        assertEquals(HistoryScreenKey, stack.last())
    }

    @Test
    fun everyConcreteRouteAndReviewOriginRoundTripsThroughRegistration() {
        val trip = testTrip()
        val routes: List<NavKey> = listOf(
            MainScreenKey,
            ProfileScreenKey,
            SettingsScreenKey,
            AboutScreenKey,
            HistoryScreenKey,
            CurrentTripScreenKey(trip),
            TripDetailsScreenKey(trip.id),
            TripReviewScreenKey(trip.id, TripReviewMode.INITIAL),
            TripReviewScreenKey(
                trip.id,
                TripReviewMode.EDIT,
                ReviewOrigin.Details(trip.id)
            )
        )
        val json = Json {
            serializersModule = backStackConfig.serializersModule
        }
        val serializer = ListSerializer(
            PolymorphicSerializer(NavKey::class)
        )

        val restored = json.decodeFromString(
            serializer,
            json.encodeToString(serializer, routes)
        )

        assertEquals(routes, restored)
        val parameter = assertNotNull(
            restored.filterIsInstance<CurrentTripScreenKey>()
                .singleOrNull()
        )
        assertEquals(trip.start, parameter.trip.start)
        assertEquals(trip.destination, parameter.trip.destination)
    }
}

private fun testStore(): TripsStore =
    TripsStore(nowEpochMillis = { 20_000L })

private fun completeTrip(
    store: TripsStore,
    trip: Trip
) {
    assertEquals(TripActionResult.Applied, store.start(trip))
    assertEquals(
        TripActionResult.Applied,
        store.updateElapsed(trip.id, 10_000L)
    )
}

private fun testTrip(): Trip = Trip(
    id = "navigation-test-trip",
    title = "Navigation test",
    description = "A fixed mock trip for navigation verification.",
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
