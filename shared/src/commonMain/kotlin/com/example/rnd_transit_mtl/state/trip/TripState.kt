package com.example.rnd_transit_mtl.state.trip

import com.example.rnd_transit_mtl.model.ids.TripId
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.model.trip.TripStatus
import kotlinx.serialization.Serializable
import com.example.rnd_transit_mtl.state.review.ReviewDraft

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
        require(trip.status == TripStatus.Active) {
            "An active trip must have the Active status."
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
 * cancelledTrips holds cancelled trips the user chose to keep in History.
 * They cannot be reviewed, so drafts and pending reviews never reference them.
 *
 * reviewDrafts holds unsaved feedback, not additional Trip records.
 */
@Serializable
data class TripsState(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val activeTrip: ActiveTripState? = null,
    val completedTrips: List<Trip> = emptyList(),
    val cancelledTrips: List<Trip> = emptyList(),
    val pendingReviewTripId: TripId? = null,
    val reviewDrafts: Map<TripId, ReviewDraft> = emptyMap()
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
            it.status == TripStatus.Completed
        }) {
            "Completed collection must contain only completed trips."
        }
        require(
            activeTrip == null ||
                    activeTrip.trip.id !in completedIds
        ) {
            "A trip cannot be active and completed simultaneously."
        }

        val cancelledIds = cancelledTrips.map { it.id }
        require(cancelledIds.distinct().size == cancelledIds.size) {
            "Cancelled trip IDs must be unique."
        }
        require(cancelledTrips.all { it.status == TripStatus.Cancelled }) {
            "Cancelled collection must contain only cancelled trips."
        }
        require(cancelledIds.none { it in completedIds || it == activeTrip?.trip?.id }) {
            "A cancelled trip cannot also be active or completed."
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
        /** 2: trips store places, legs, status and owner instead of map points and a demo distance. */
        const val CURRENT_SCHEMA_VERSION: Int = 2
    }
}
