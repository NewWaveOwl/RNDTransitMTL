package com.example.rnd_transit_mtl

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.rnd_transit_mtl.model.Trip
import com.example.rnd_transit_mtl.model.TripReviewMode
import com.example.rnd_transit_mtl.state.TripActionResult
import com.example.rnd_transit_mtl.state.TripsStore

typealias RegisterReviewBackHandler = (() -> Unit) -> (() -> Unit)

val LocalTripNavigation = compositionLocalOf<TripNavigation> {
    error("TripNavigation must be provided by App.")
}

/**
 * Coordinates destination changes through the application's one Navigator.
 *
 * Trips and drafts remain in TripsStore. Review origins remain in route keys.
 * The temporary Back registration is never included in saved state.
 * Operations must run on the UI thread.
 */
class TripNavigation(
    private val navigator: Navigator,
    private val tripsStore: TripsStore
) {
    /** Transient popup request; trip records still come only from TripsStore. */
    var informationTripId by mutableStateOf<String?>(null)
        private set

    fun requestTripInformation(): Boolean {
        val current = navigator.current as? CurrentTripScreenKey ?: return false
        if (tripsStore.findTrip(current.trip.id) == null) return false
        informationTripId = current.trip.id
        return true
    }

    fun dismissTripInformation(tripId: String? = null) {
        if (tripId == null || informationTripId == tripId) {
            informationTripId = null
        }
    }

    private class ReviewBackRegistration(
        val key: TripReviewScreenKey,
        val onBack: () -> Unit
    )

    private var reviewBackRegistration: ReviewBackRegistration? = null

    /**
     * Connects shared header Back to the review screen's close operation.
     * An outgoing registration cannot unregister a newer registration.
     */
    fun registerReviewBackHandler(
        key: TripReviewScreenKey,
        onBack: () -> Unit
    ): () -> Unit {
        val registration = ReviewBackRegistration(key, onBack)
        reviewBackRegistration = registration

        return {
            if (reviewBackRegistration === registration) {
                reviewBackRegistration = null
            }
        }
    }

    fun openCurrentTrip(trip: Trip): Boolean {
        dismissTripInformation()
        val active = tripsStore.activeTrip ?: return false
        if (active.id != trip.id) return false
        if (!discardCurrentReview()) return false

        pruneCompletedSimulationEntries()

        val existing = navigator.findLast {
            it is CurrentTripScreenKey && it.trip.id == trip.id
        }

        if (existing != null) {
            navigator.popUntil(existing)
        } else {
            navigator.navigate(CurrentTripScreenKey(trip))
        }

        val current = navigator.current
        return current is CurrentTripScreenKey &&
                current.trip.id == trip.id
    }

    /** Restores the completed 100% presentation without running a simulation. */
    fun openCompletedTrip(tripId: String): Boolean {
        dismissTripInformation()
        val completed = tripsStore.findCompleted(tripId) ?: return false
        if (!discardCurrentReview()) return false

        val current = navigator.current
        if (current is CurrentTripScreenKey && current.trip.id == tripId) {
            return true
        }

        pruneCompletedSimulationEntries()
        navigator.navigate(CurrentTripScreenKey(completed))
        return (navigator.current as? CurrentTripScreenKey)?.trip?.id == tripId
    }

    fun resumeActiveTrip(): Boolean {
        val trip = tripsStore.activeTrip ?: return false
        return openCurrentTrip(trip)
    }

    /**
     * Establishes the matching initial review before acknowledging its event.
     * Repeated requests recognize the existing destination.
     */
    fun openInitialReview(tripId: String): Boolean {
        dismissTripInformation()
        if (tripsStore.findCompleted(tripId) == null) return false

        val destination = TripReviewScreenKey(
            tripId = tripId,
            mode = TripReviewMode.INITIAL
        )
        val current = navigator.current

        if (current == destination) {
            return acknowledgeMatchingPendingReview(tripId)
        }

        val fromMatchingTrip =
            current is CurrentTripScreenKey &&
                    current.trip.id == tripId
        val fromPendingEvent =
            tripsStore.pendingReviewTripId == tripId

        if (!fromMatchingTrip && !fromPendingEvent) return false

        if (
            current is TripReviewScreenKey &&
            current.tripId != tripId
        ) {
            return false
        }

        val existing = navigator.findLast { it == destination }

        when {
            existing != null -> navigator.popUntil(existing)
            fromMatchingTrip -> navigator.replace(destination)
            else -> navigator.navigate(destination)
        }

        pruneCompletedSimulationEntries()

        if (navigator.current != destination) return false
        return acknowledgeMatchingPendingReview(tripId)
    }

    fun recoverPendingReview(): Boolean {
        val tripId = tripsStore.pendingReviewTripId ?: return false
        val current = navigator.current

        if (
            current is TripReviewScreenKey &&
            (
                current.tripId != tripId ||
                        current.mode != TripReviewMode.INITIAL
                )
        ) {
            return false
        }

        return openInitialReview(tripId)
    }

    fun openDetails(tripId: String): Boolean {
        dismissTripInformation()
        if (tripsStore.findCompleted(tripId) == null) return false
        if (!discardCurrentReview()) return false

        pruneCompletedSimulationEntries()

        val destination = TripDetailsScreenKey(tripId)
        navigator.open(destination)
        return navigator.current == destination
    }

    fun openReview(
        tripId: String,
        origin: ReviewOrigin
    ): Boolean {
        if (tripsStore.findCompleted(tripId) == null) return false

        if (tripsStore.pendingReviewTripId == tripId) {
            return openInitialReview(tripId)
        }

        val destination = TripReviewScreenKey(
            tripId = tripId,
            mode = TripReviewMode.EDIT,
            origin = origin
        )

        if (navigator.current == destination) return true
        if (navigator.current != origin.destination()) return false

        navigator.open(destination)
        return navigator.current == destination
    }

    /**
     * Returns after the screen has saved or discarded its draft.
     * A deleted details origin recovers to History.
     */
    fun finishReview(key: TripReviewScreenKey): Boolean {
        val target = reviewReturnDestination(key)

        if (navigator.current != key) {
            return navigator.current == target
        }

        pruneCompletedSimulationEntries()
        replaceOrReturn(target)
        return navigator.current == target
    }

    /**
     * Closes an unsaved review before switching shared sections.
     * The closed review destination cannot remain underneath the new section.
     */
    fun openSection(destination: ScreenKey): Boolean {
        when (destination) {
            MainScreenKey,
            HistoryScreenKey,
            ProfileScreenKey,
            SettingsScreenKey,
            AboutScreenKey -> Unit
            else -> return false
        }

        if (!discardCurrentReview()) return false

        dismissTripInformation()
        pruneCompletedSimulationEntries()
        navigator.open(destination)
        return navigator.current == destination
    }

    fun returnToPlanner(): Boolean =
        openSection(MainScreenKey)

    /**
     * Used by shared header Back and NavDisplay's platform Back fallback.
     * A composed review can supply its own equivalent close operation.
     */
    fun back(): Boolean {
        dismissTripInformation()
        if (!navigator.hasPrevious()) return false

        val current = navigator.current

        if (current is TripReviewScreenKey) {
            val registration = reviewBackRegistration

            if (registration?.key == current) {
                registration.onBack()
                return true
            }

            return when (tripsStore.skipReview(current.tripId)) {
                TripActionResult.Applied,
                is TripActionResult.MissingTrip -> finishReview(current)
                else -> false
            }
        }

        /*
         * At the completion boundary, replace the finished destination
         * before pruning. Pruning first would expose the preceding page,
         * allowing replaceOrReturn() to replace that unrelated page.
         */
        if (
            current is CurrentTripScreenKey &&
            tripsStore.findCompleted(current.trip.id) != null
        ) {
            when (tripsStore.skipReview(current.trip.id)) {
                TripActionResult.Applied,
                is TripActionResult.MissingTrip -> Unit
                else -> return false
            }

            replaceOrReturn(HistoryScreenKey)
            pruneCompletedSimulationEntries()
            return navigator.current == HistoryScreenKey
        }

        pruneCompletedSimulationEntries()
        navigator.pop()
        return true
    }

    private fun reviewReturnDestination(
        key: TripReviewScreenKey
    ): ScreenKey {
        if (key.mode == TripReviewMode.INITIAL) {
            return HistoryScreenKey
        }

        val origin = key.origin
        return if (
            origin is ReviewOrigin.Details &&
            tripsStore.findCompleted(origin.tripId) == null
        ) {
            HistoryScreenKey
        } else {
            origin.destination()
        }
    }

    private fun replaceOrReturn(destination: ScreenKey) {
        if (navigator.findLast { it == destination } != null) {
            navigator.popUntil(destination)
        } else {
            navigator.replace(destination)
        }
    }

    /**
     * Discards unsaved feedback and closes its navigation destination.
     * Saved feedback and the completed record remain unchanged.
     */
    private fun discardCurrentReview(): Boolean {
        val key = navigator.current as? TripReviewScreenKey
            ?: return true

        return when (tripsStore.skipReview(key.tripId)) {
            TripActionResult.Applied,
            is TripActionResult.MissingTrip -> finishReview(key)
            else -> false
        }
    }

    private fun acknowledgeMatchingPendingReview(
        tripId: String
    ): Boolean {
        if (tripsStore.pendingReviewTripId != tripId) return true

        return tripsStore.acknowledgeReviewNavigation(tripId) ==
                TripActionResult.Applied
    }

    private fun pruneCompletedSimulationEntries() {
        navigator.removeWhere { key ->
            key is CurrentTripScreenKey &&
                    tripsStore.findCompleted(key.trip.id) != null
        }
    }
}
