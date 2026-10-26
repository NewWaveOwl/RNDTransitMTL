package com.example.rnd_transit_mtl.state

import com.example.rnd_transit_mtl.model.Trip
import com.example.rnd_transit_mtl.model.TripPoint
import com.example.rnd_transit_mtl.model.TripReview
import com.example.rnd_transit_mtl.model.TripTransportSnapshot
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TripReviewStateTest {
    @Test
    fun overallIsRequiredAndUnselectedOptionalRatingsStayNull() {
        val store = completedStore()
        val id = sampleTrip().id

        assertEquals(TripActionResult.Applied, store.beginReview(id))

        val initial = assertNotNull(store.reviewDrafts[id])

        assertNull(initial.overall)
        assertNull(initial.quality)
        assertNull(initial.interesting)
        assertNull(initial.`fun`)
        assertEquals(ReviewStep.OVERALL, initial.step)

        assertIs<TripActionResult.InvalidInput>(store.saveReview(id))
        assertNull(store.findCompleted(id)?.review)

        assertEquals(
            TripActionResult.Applied,
            store.updateReviewDraft(
                id,
                initial.copy(
                    overall = 3,
                    step = ReviewStep.FUN
                )
            )
        )

        assertEquals(TripActionResult.Applied, store.saveReview(id))

        val review = assertNotNull(store.findCompleted(id)?.review)

        assertEquals(3, review.overall)
        assertNull(review.quality)
        assertNull(review.interesting)
        assertNull(review.`fun`)
        assertEquals("", review.comment)
        assertEquals("", review.imageUrl)
        assertEquals(1, store.completedTrips.size)
        assertNull(store.reviewDrafts[id])
    }

    @Test
    fun cancellingEditsPreservesSavedReviewAndReopeningLoadsIt() {
        val store = completedStore()
        val id = sampleTrip().id
        val saved = TripReview(
            overall = 5,
            quality = 4,
            interesting = 3,
            `fun` = 2,
            comment = "Saved feedback",
            imageUrl = "https://example.com/saved-review.jpg"
        )

        assertEquals(
            TripActionResult.Applied,
            store.saveReview(id, saved)
        )
        assertEquals(TripActionResult.Applied, store.beginReview(id))

        val draft = assertNotNull(store.reviewDrafts[id])

        assertEquals(
            TripActionResult.Applied,
            store.updateReviewDraft(
                id,
                draft.copy(
                    overall = 1,
                    comment = "Unsaved changes",
                    imageUrl = "https://example.com/unsaved-review.jpg",
                    step = ReviewStep.INTERESTING
                )
            )
        )

        assertEquals(saved, store.findCompleted(id)?.review)

        assertEquals(TripActionResult.Applied, store.skipReview(id))
        assertEquals(saved, store.findCompleted(id)?.review)
        assertNull(store.reviewDrafts[id])

        assertEquals(TripActionResult.Applied, store.beginReview(id))

        val reopened = assertNotNull(store.reviewDrafts[id])

        assertEquals(saved.overall, reopened.overall)
        assertEquals(saved.comment, reopened.comment)
        assertEquals(saved.imageUrl, reopened.imageUrl)
        assertEquals(ReviewStep.OVERALL, reopened.step)
    }

    @Test
    fun restorationKeepsQuestionAndDraftButDeletedTripCannotBeSaved() {
        val store = completedStore()
        val id = sampleTrip().id

        assertEquals(TripActionResult.Applied, store.beginReview(id))

        val draft = ReviewDraft(
            overall = 4,
            interesting = 5,
            comment = "Draft survives rotation",
            imageUrl = "https://example.com/draft.jpg",
            step = ReviewStep.FUN
        )

        assertEquals(
            TripActionResult.Applied,
            store.updateReviewDraft(id, draft)
        )

        val restored = assertNotNull(
            TripsStore.fromSavedStateJson(
                encoded = store.toSavedStateJson(),
                nowEpochMillis = { 2_000L }
            )
        )

        assertEquals(draft, restored.reviewDrafts[id])
        assertNull(restored.findCompleted(id)?.review)

        assertEquals(
            TripActionResult.Applied,
            restored.removeCompleted(id)
        )

        assertIs<TripActionResult.MissingTrip>(
            restored.saveReview(id)
        )
        assertNull(restored.findCompleted(id))
        assertNull(restored.reviewDrafts[id])
        assertEquals(0, restored.completedTrips.size)
    }

    @Test
    fun reviewImageLinkIsTrimmedSavedAndRestoredWithoutAddingATrip() {
        val store = completedStore()
        val id = sampleTrip().id
        store.beginReview(id)
        val url = "https://example.com/review.jpg"
        store.updateReviewDraft(id, ReviewDraft(overall = 4, imageUrl = "  $url  "))
        assertNull(store.findCompleted(id)?.review)

        assertEquals(TripActionResult.Applied, store.saveReview(id))
        val restored = assertNotNull(
            TripsStore.fromSavedStateJson(store.toSavedStateJson(), nowEpochMillis = { 2_000L })
        )
        assertEquals(1, restored.completedTrips.size)
        assertEquals(id, restored.completedTrips.single().id)
        assertEquals(url, restored.completedTrips.single().review?.imageUrl)
    }

    @Test
    fun thumbnailQueryUrlCanBeSavedButOverallIsStillRequired() {
        val store = completedStore()
        val id = sampleTrip().id
        val url = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTgjJV2ADwJIiu9ObRGmB9WgBAFTeuvQrcHyoVY3zcnQkwXAlmkbiCP0fo&s=10"
        store.beginReview(id)
        store.updateReviewDraft(id, ReviewDraft(imageUrl = url))

        assertIs<TripActionResult.InvalidInput>(store.saveReview(id))
        assertNull(store.findCompleted(id)?.review)

        store.updateReviewDraft(id, ReviewDraft(overall = 3, imageUrl = url))
        assertEquals(TripActionResult.Applied, store.saveReview(id))
        assertEquals(url, store.findCompleted(id)?.review?.imageUrl)
        assertEquals(1, store.completedTrips.size)
    }

    @Test
    fun invalidImageLinkPreservesSavedFeedbackAndOldJsonHasNoImage() {
        val store = completedStore()
        val id = sampleTrip().id
        val saved = TripReview(overall = 5, imageUrl = "https://example.com/saved.jpg")
        store.saveReview(id, saved)
        store.beginReview(id)
        store.updateReviewDraft(id, ReviewDraft(overall = 2, imageUrl = "not a link"))

        assertIs<TripActionResult.InvalidInput>(store.saveReview(id))
        assertEquals(saved, store.findCompleted(id)?.review)
        assertEquals("not a link", store.reviewDrafts[id]?.imageUrl)
        assertEquals("", Json.decodeFromString(TripReview.serializer(), "{\"overall\":4}").imageUrl)
        assertEquals("", Json.decodeFromString(ReviewDraft.serializer(), "{}").imageUrl)
    }

    private fun completedStore(): TripsStore {
        val store = TripsStore(nowEpochMillis = { 2_000L })
        val trip = sampleTrip()

        assertEquals(TripActionResult.Applied, store.start(trip))
        assertEquals(
            TripActionResult.Applied,
            store.updateElapsed(
                tripId = trip.id,
                elapsedMillis = TRIP_SIMULATION_DURATION_MILLIS
            )
        )

        return store
    }

    private fun sampleTrip(): Trip = Trip(
        id = "review-state-test-trip",
        title = "Review test",
        description = "Fixed completed trip for review verification.",
        imageUrl = "https://example.com/reference.jpg",
        start = TripPoint("Start", 0.20f, 0.30f),
        destination = TripPoint("Destination", 0.80f, 0.70f),
        plannedMinutes = 30,
        selectedTransports = listOf(
            TripTransportSnapshot(
                id = "walking",
                label = "Walking",
                usesRoutes = false
            )
        ),
        selectedRoutes = emptyList(),
        attractionIntensity = 50f,
        distanceKm = 10.0,
        createdAtEpochMillis = 1_000L
    )
}
