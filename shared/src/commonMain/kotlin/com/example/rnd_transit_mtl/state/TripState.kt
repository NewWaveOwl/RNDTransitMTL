package com.example.rnd_transit_mtl.state

import com.example.rnd_transit_mtl.model.Trip
import kotlinx.serialization.Serializable

/**
 * Fixed active-time duration of the mock simulation.
 *
 * Planned minutes remain part of the Trip record and do not change
 * this duration.
 */
const val TRIP_SIMULATION_DURATION_MILLIS: Long = 10_000L

/**
 * An unfinished Trip and its accumulated active simulation time.
 *
 * No running flag, clock mark, coroutine, or lifecycle owner is saved.
 * The future CurrentTripScreen determines whether timing is eligible.
 */
@Serializable
data class ActiveTripState(
    val trip: Trip,
    val elapsedMillis: Long = 0L
) {
    init {
        require(trip.completedAtEpochMillis == null) {
            "An active trip must not already be completed."
        }
        require(trip.review == null) {
            "An active trip must not have a saved review."
        }
        require(elapsedMillis in 0L..TRIP_SIMULATION_DURATION_MILLIS) {
            "Elapsed simulation time must be between 0 and 10000 milliseconds."
        }
    }
}

/**
 * The single serializable snapshot owned by TripsStore.
 *
 * completedTrips is the only completed collection. History, details,
 * and review screens must read these records through LocalTripsStore.
 *
 * pendingReviewTripId identifies one automatic review request that
 * navigation has not yet acknowledged. A new trip cannot start until
 * that request has been handled, preventing it from being overwritten.
 *
 * reviewDrafts holds unsaved feedback, not additional Trip records.
 */
@Serializable
data class TripsState(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val activeTrip: ActiveTripState? = null,
    val completedTrips: List<Trip> = emptyList(),
    val pendingReviewTripId: String? = null,
    val reviewDrafts: Map<String, ReviewDraft> = emptyMap()
) {
    init {
        require(schemaVersion == CURRENT_SCHEMA_VERSION) {
            "Unsupported trip saved-state version."
        }

        val completedIds = completedTrips.map { it.id }

        require(completedIds.distinct().size == completedIds.size) {
            "Completed trip IDs must be unique."
        }
        require(completedTrips.all {
            it.completedAtEpochMillis != null
        }) {
            "Completed collection must contain only completed trips."
        }
        require(
            activeTrip == null ||
                    activeTrip.trip.id !in completedIds
        ) {
            "A trip cannot be active and completed simultaneously."
        }
        require(
            pendingReviewTripId == null ||
                    pendingReviewTripId in completedIds
        ) {
            "Pending review must reference a completed trip."
        }
        require(
            activeTrip == null ||
                    pendingReviewTripId == null
        ) {
            "Handle the pending review before starting another trip."
        }
        require(reviewDrafts.keys.all { it in completedIds }) {
            "Review drafts must reference existing completed trips."
        }
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION: Int = 1
    }
}