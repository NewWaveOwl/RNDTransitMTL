package com.example.rnd_transit_mtl.state.trip

import com.example.rnd_transit_mtl.data.assets.toSampleMapPlace
import com.example.rnd_transit_mtl.model.ids.TransportId
import com.example.rnd_transit_mtl.model.ids.TripId
import com.example.rnd_transit_mtl.model.ids.ZoneId
import com.example.rnd_transit_mtl.model.review.TripReview
import com.example.rnd_transit_mtl.model.trip.RouteLeg
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.model.trip.TripPoint
import com.example.rnd_transit_mtl.model.trip.TripRouteSnapshot
import com.example.rnd_transit_mtl.model.trip.TripStatus
import com.example.rnd_transit_mtl.model.trip.TripTransportSnapshot
import com.example.rnd_transit_mtl.state.review.ReviewDraft
import kotlinx.serialization.Serializable

/*
 * Saved-state schema 1, as written by builds before issue #27.
 *
 * These classes only decode old snapshots. They skip schema 1's checks:
 * the converted Trip and TripsState run the current ones, so a snapshot
 * that schema 1 would also have rejected still restores as a fresh store.
 */

/** Schema 1 trip: map points and a demo distance instead of places and legs. */
@Serializable
internal data class TripV1(
    val id: TripId,
    val title: String,
    val description: String,
    val imageUrl: String,
    val start: TripPoint,
    val destination: TripPoint,
    val plannedMinutes: Int,
    val selectedTransports: List<TripTransportSnapshot>,
    val selectedRoutes: List<TripRouteSnapshot>,
    val attractionIntensity: Float,
    val distanceKm: Double,
    val createdAtEpochMillis: Long,
    val completedAtEpochMillis: Long? = null,
    val review: TripReview? = null,
    val selectedZoneIds: List<ZoneId> = emptyList(),
    val selectedZonesByTransport: Map<TransportId, List<ZoneId>> = emptyMap()
)

@Serializable
internal data class ActiveTripStateV1(
    val trip: TripV1,
    val elapsedMillis: Long = 0L
)

@Serializable
internal data class TripsStateV1(
    val schemaVersion: Int,
    val activeTrip: ActiveTripStateV1? = null,
    val completedTrips: List<TripV1> = emptyList(),
    val pendingReviewTripId: TripId? = null,
    val reviewDrafts: Map<TripId, ReviewDraft> = emptyMap()
)

/**
 * Converts a schema 1 snapshot to the current schema.
 *
 * IDs, timestamps, reviews, the pending review and drafts are kept.
 * Schema 1 had no status or cancelled trips, so the active trip becomes
 * Active and every completed trip becomes Completed.
 *
 * @throws IllegalArgumentException if a trip or the snapshot breaks a current rule.
 */
internal fun TripsStateV1.migrate(): TripsState = TripsState(
    activeTrip = activeTrip?.let {
        ActiveTripState(it.trip.migrate(TripStatus.Active), it.elapsedMillis)
    },
    completedTrips = completedTrips.map { it.migrate(TripStatus.Completed) },
    pendingReviewTripId = pendingReviewTripId,
    reviewDrafts = reviewDrafts
)

/**
 * Converts the stored map points to real places joined by one unrouted leg,
 * the same shape FakeRoutingService gives new trips.
 *
 * The leg uses the first selected mode and lasts the planned minutes.
 * Schema 1's distanceKm was a synthetic demo value and is dropped; the
 * trip's distance becomes the straight-line distance between the places.
 * Old trips have no recorded owner.
 *
 * @param status Status implied by where the trip was stored.
 */
private fun TripV1.migrate(status: TripStatus): Trip {
    val origin = start.toSampleMapPlace()
    val destinationPlace = destination.toSampleMapPlace()
    val mode = requireNotNull(selectedTransports.firstOrNull()) {
        "Choose at least one transport type."
    }

    return Trip(
        id = id,
        ownerId = null,
        status = status,
        title = title,
        description = description,
        imageUrl = imageUrl,
        origin = origin,
        destination = destinationPlace,
        legs = listOf(
            RouteLeg.straightLine(
                transportId = mode.id,
                from = origin.point,
                to = destinationPlace.point,
                durationSeconds = plannedMinutes * 60L
            )
        ),
        plannedMinutes = plannedMinutes,
        selectedTransports = selectedTransports,
        selectedRoutes = selectedRoutes,
        attractionIntensity = attractionIntensity,
        createdAt = createdAtEpochMillis,
        completedAt = completedAtEpochMillis,
        review = review,
        selectedZoneIds = selectedZoneIds,
        selectedZonesByTransport = selectedZonesByTransport
    )
}
