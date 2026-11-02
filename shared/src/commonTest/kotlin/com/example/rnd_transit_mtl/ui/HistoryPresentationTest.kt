package com.example.rnd_transit_mtl.ui

import com.example.rnd_transit_mtl.model.ids.TripId
import com.example.rnd_transit_mtl.model.ids.TransportId
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.model.trip.TripPoint
import com.example.rnd_transit_mtl.model.review.TripReview
import com.example.rnd_transit_mtl.model.trip.TripTransportSnapshot
import com.example.rnd_transit_mtl.state.trip.TRIP_SIMULATION_DURATION_MILLIS
import com.example.rnd_transit_mtl.state.trip.TripActionResult
import com.example.rnd_transit_mtl.state.trip.TripsStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import com.example.rnd_transit_mtl.View.history.TripHistoryFilter
import com.example.rnd_transit_mtl.View.history.firstReviewSentence
import com.example.rnd_transit_mtl.View.history.historyTrips

class HistoryPresentationTest {
    @Test
    fun completionTimeDeterminesNewestFirst() {
        val newestCreated = completedTrip(
            id = "created-later",
            createdAt = 3_000L,
            completedAt = 10_000L
        )
        val newestCompleted = completedTrip(
            id = "completed-later",
            createdAt = 1_000L,
            completedAt = 12_000L
        )
        val middle = completedTrip(
            id = "middle",
            createdAt = 2_000L,
            completedAt = 11_000L
        )

        val result = historyTrips(
            listOf(newestCreated, newestCompleted, middle),
            TripHistoryFilter.ALL
        )

        assertEquals(
            listOf("completed-later", "middle", "created-later"),
            result.map { it.id.value }
        )
    }

    @Test
    fun reviewedMeansSavedReviewEvenWithOptionalValuesMissing() {
        val reviewed = completedTrip(
            id = "reviewed",
            review = TripReview(overall = 3)
        )
        val skipped = completedTrip(id = "skipped")

        assertEquals(
            listOf("reviewed"),
            historyTrips(
                listOf(skipped, reviewed),
                TripHistoryFilter.REVIEWED
            ).map { it.id.value }
        )

        assertEquals(
            listOf("skipped"),
            historyTrips(
                listOf(skipped, reviewed),
                TripHistoryFilter.NOT_REVIEWED
            ).map { it.id.value }
        )
    }

    @Test
    fun sharedReviewUpdateAndRemovalChangeDerivedHistory() {
        val store = TripsStore(nowEpochMillis = { 12_000L })
        val trip = completedTrip(id = "shared-trip").copy(
            completedAtEpochMillis = null
        )

        assertEquals(TripActionResult.Applied, store.start(trip))
        assertEquals(
            TripActionResult.Applied,
            store.updateElapsed(
                trip.id,
                TRIP_SIMULATION_DURATION_MILLIS
            )
        )

        assertEquals(
            1,
            historyTrips(
                store.completedTrips,
                TripHistoryFilter.NOT_REVIEWED
            ).size
        )

        assertEquals(
            TripActionResult.Applied,
            store.saveReview(trip.id, TripReview(overall = 4))
        )

        assertTrue(
            historyTrips(
                store.completedTrips,
                TripHistoryFilter.NOT_REVIEWED
            ).isEmpty()
        )
        assertEquals(
            1,
            historyTrips(
                store.completedTrips,
                TripHistoryFilter.REVIEWED
            ).size
        )

        assertEquals(
            TripActionResult.Applied,
            store.removeCompleted(trip.id)
        )
        assertTrue(
            historyTrips(
                store.completedTrips,
                TripHistoryFilter.ALL
            ).isEmpty()
        )
    }

    @Test
    fun collapsedCommentKeepsFirstSentenceAndDecimalPunctuation() {
        assertEquals(
            "A pleasant 2.4 km trip.",
            firstReviewSentence(
                "A pleasant 2.4 km trip. I would do it again."
            )
        )
        assertEquals(
            "First line",
            firstReviewSentence("First line\nSecond line")
        )
    }

    private fun completedTrip(
        id: String,
        createdAt: Long = 1_000L,
        completedAt: Long = 10_000L,
        review: TripReview? = null
    ): Trip = Trip(
        id = TripId(id),
        title = "History test",
        description = "Fixed record for History verification.",
        imageUrl = "https://example.com/reference.jpg",
        start = TripPoint("Start", 0.20f, 0.30f),
        destination = TripPoint("Destination", 0.80f, 0.70f),
        plannedMinutes = 30,
        selectedTransports = listOf(
            TripTransportSnapshot(
                id = TransportId("walking"),
                label = "Walking",
                usesRoutes = false
            )
        ),
        selectedRoutes = emptyList(),
        attractionIntensity = 50f,
        distanceKm = 2.4,
        createdAtEpochMillis = createdAt,
        completedAtEpochMillis = completedAt,
        review = review
    )
}
