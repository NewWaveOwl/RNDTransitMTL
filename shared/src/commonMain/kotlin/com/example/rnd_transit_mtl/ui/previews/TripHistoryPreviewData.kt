package com.example.rnd_transit_mtl.ui.previews

import com.example.rnd_transit_mtl.model.Trip
import com.example.rnd_transit_mtl.model.TripPoint
import com.example.rnd_transit_mtl.model.TripReview
import com.example.rnd_transit_mtl.model.TripRouteSnapshot
import com.example.rnd_transit_mtl.model.TripTransportSnapshot
import com.example.rnd_transit_mtl.state.TripsState
import com.example.rnd_transit_mtl.state.TripsStore
import kotlinx.serialization.json.Json
import kotlin.time.Instant

private val previewCreatedAt =
    Instant.parse("2026-10-04T14:00:00Z").toEpochMilliseconds()

internal val reviewedHistoryPreviewTrip = Trip(
    id = "preview-sunday-discovery",
    title = "Sunday discovery walk",
    description = "A relaxed trip combining a short bus ride " +
        "with a walk between two points on the bundled mock map.",
    imageUrl = "https://example.com/sunday-reference.jpg",
    start = TripPoint("Park entrance", 0.20f, 0.30f),
    destination = TripPoint("Riverside stop", 0.80f, 0.70f),
    plannedMinutes = 30,
    selectedTransports = listOf(
        TripTransportSnapshot(
            id = "preview-walking",
            label = "Walking",
            usesRoutes = false
        ),
        TripTransportSnapshot(
            id = "preview-bus",
            label = "Bus",
            usesRoutes = true
        )
    ),
    selectedRoutes = listOf(
        TripRouteSnapshot(
            id = "preview-bus-route",
            transportTypeId = "preview-bus",
            label = "Demo route 24"
        )
    ),
    attractionIntensity = 65f,
    distanceKm = 2.4,
    createdAtEpochMillis = previewCreatedAt,
    completedAtEpochMillis = previewCreatedAt + 10_000L,
    review = TripReview(
        overall = 4,
        quality = 4,
        interesting = 5,
        `fun` = 4,
        imageUrl = "https://example.com/review-photo.jpg",
        comment = "Nice route and interesting places. " +
            "The relaxed pace made it easy to enjoy the trip."
    )
)

internal val unreviewedHistoryPreviewTrip =
    reviewedHistoryPreviewTrip.copy(
        id = "preview-morning-trip",
        title = "Morning transit trip",
        description = "A completed mock trip whose review was skipped.",
        imageUrl = "https://example.com/morning-reference.jpg",
        start = TripPoint("Morning start", 0.25f, 0.65f),
        destination = TripPoint("Morning destination", 0.75f, 0.25f),
        plannedMinutes = 20,
        distanceKm = 1.8,
        createdAtEpochMillis = previewCreatedAt - 3_600_000L,
        completedAtEpochMillis = previewCreatedAt - 3_590_000L,
        review = null
    )

internal val partialReviewHistoryPreviewTrip =
    reviewedHistoryPreviewTrip.copy(
        id = "preview-afternoon-loop",
        title = "Afternoon discovery",
        description = "A completed trip with optional categories left unrated.",
        imageUrl = "https://example.com/afternoon-reference.jpg",
        plannedMinutes = 45,
        distanceKm = 3.1,
        createdAtEpochMillis = previewCreatedAt - 1_800_000L,
        completedAtEpochMillis = previewCreatedAt - 1_790_000L,
        review = TripReview(
            overall = 3,
            interesting = 4
        )
    )

/**
 * Deliberately unsorted to exercise History's presentation ordering.
 * These values do not populate the real application store.
 */
internal val historyPreviewTrips = listOf(
    unreviewedHistoryPreviewTrip,
    reviewedHistoryPreviewTrip,
    partialReviewHistoryPreviewTrip
)

internal fun createHistoryPreviewStore(
    trips: List<Trip> = historyPreviewTrips
): TripsStore {
    val encoded = Json.encodeToString(
        TripsState.serializer(),
        TripsState(completedTrips = trips)
    )

    return checkNotNull(
        TripsStore.fromSavedStateJson(
            encoded = encoded,
            nowEpochMillis = { previewCreatedAt + 20_000L }
        )
    )
}
