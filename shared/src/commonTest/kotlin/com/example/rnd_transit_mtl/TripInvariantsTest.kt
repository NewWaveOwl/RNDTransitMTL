package com.example.rnd_transit_mtl

import com.example.rnd_transit_mtl.data.MockTripGenerator
import com.example.rnd_transit_mtl.data.TripGenerationInput
import com.example.rnd_transit_mtl.data.TripGenerationResult
import com.example.rnd_transit_mtl.model.TransportType
import com.example.rnd_transit_mtl.model.Trip
import com.example.rnd_transit_mtl.model.TripPoint
import com.example.rnd_transit_mtl.model.TripReview
import com.example.rnd_transit_mtl.model.TripTransportSnapshot
import com.example.rnd_transit_mtl.state.ReviewDraft
import com.example.rnd_transit_mtl.state.ReviewStep
import com.example.rnd_transit_mtl.state.TripActionResult
import com.example.rnd_transit_mtl.state.TripsStore
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TripInvariantsTest {
    @Test
    fun repeatedCompletionPreservesOneRecordAndItsOriginalTimestamp() {
        var now = 12_000L
        val store = TripsStore(nowEpochMillis = { now })
        val trip = fixtureTrip("once")

        complete(store, trip)
        val completed = assertNotNull(store.findCompleted(trip.id))

        now = 99_000L

        assertEquals(TripActionResult.AlreadyCompleted, store.complete(trip.id))
        assertEquals(TripActionResult.AlreadyCompleted, store.complete(trip.id))
        assertEquals(
            TripActionResult.AlreadyCompleted,
            store.updateElapsed(trip.id, 20_000L)
        )

        assertEquals(1, store.completedTrips.count { it.id == trip.id })
        assertEquals(completed, store.completedTrips.single())
        assertNull(store.activeTrip)
        assertEquals(1f, store.progressFor(trip.id))
        assertEquals(trip.id, store.pendingReviewTripId)
    }

    @Test
    fun reviewReplacementPreservesIdsAndMissingUpdatesCannotRecreateRecords() {
        val first = fixtureTrip("first")
        val second = fixtureTrip("second")
        val store = completedStore(first, second)
        val unchangedSecond = assertNotNull(store.findCompleted(second.id))

        assertEquals(
            TripActionResult.Applied,
            store.saveReview(first.id, TripReview(overall = 3))
        )
        assertEquals(
            TripActionResult.Applied,
            store.saveReview(
                first.id,
                TripReview(overall = 5, quality = 4, comment = "Updated")
            )
        )

        assertEquals(listOf(first.id, second.id), store.completedTrips.map { it.id })
        assertEquals(2, store.completedTrips.size)
        assertEquals(5, store.findCompleted(first.id)?.review?.overall)
        assertEquals(unchangedSecond, store.findCompleted(second.id))

        assertEquals(
            TripActionResult.Applied,
            store.removeCompleted(first.id)
        )
        val afterRemoval = store.snapshot()

        assertIs<TripActionResult.MissingTrip>(
            store.saveReview(first.id, TripReview(overall = 1))
        )
        assertIs<TripActionResult.MissingTrip>(store.saveReview(first.id))
        assertIs<TripActionResult.MissingTrip>(store.beginReview(first.id))

        assertEquals(afterRemoval, store.snapshot())
        assertEquals(listOf(second.id), store.completedTrips.map { it.id })
    }

    @Test
    fun removalClearsOnlyTheMatchingPendingEventAndDraft() {
        val first = fixtureTrip("retained")
        val second = fixtureTrip("removed")
        val store = TripsStore(nowEpochMillis = { 12_000L })

        complete(store, first)
        assertEquals(
            TripActionResult.Applied,
            store.saveReview(first.id, TripReview(overall = 4))
        )
        assertEquals(TripActionResult.Applied, store.beginReview(first.id))

        val retainedTrip = assertNotNull(store.findCompleted(first.id))
        val retainedDraft = assertNotNull(store.reviewDrafts[first.id])

        complete(store, second)
        assertEquals(TripActionResult.Applied, store.beginReview(second.id))
        assertEquals(second.id, store.pendingReviewTripId)

        assertEquals(
            TripActionResult.Applied,
            store.removeCompleted(second.id)
        )

        assertEquals(listOf(retainedTrip), store.completedTrips)
        assertEquals(retainedDraft, store.reviewDrafts[first.id])
        assertNull(store.reviewDrafts[second.id])
        assertNull(store.pendingReviewTripId)
        assertNull(store.findTrip(second.id))
    }

    @Test
    fun snapshotRestoresActiveElapsedCompletedRecordAndReviewDraftTogether() {
        val completedTrip = fixtureTrip("completed")
        val activeTrip = fixtureTrip("active")
        val store = completedStore(completedTrip)

        val savedReview = TripReview(overall = 5, comment = "Saved feedback")
        assertEquals(
            TripActionResult.Applied,
            store.saveReview(completedTrip.id, savedReview)
        )
        assertEquals(
            TripActionResult.Applied,
            store.beginReview(completedTrip.id)
        )

        val draft = ReviewDraft(
            overall = 3,
            interesting = 4,
            comment = "Unsaved feedback",
            step = ReviewStep.INTERESTING
        )
        assertEquals(
            TripActionResult.Applied,
            store.updateReviewDraft(completedTrip.id, draft)
        )
        assertEquals(TripActionResult.Applied, store.start(activeTrip))
        assertEquals(
            TripActionResult.Applied,
            store.updateElapsed(activeTrip.id, 4_321L)
        )

        val before = store.snapshot()
        val restored = assertNotNull(
            TripsStore.fromSavedStateJson(
                encoded = store.toSavedStateJson(),
                nowEpochMillis = { 90_000L }
            )
        )

        assertEquals(before, restored.snapshot())
        assertEquals(4_321L, restored.elapsedMillis)
        assertEquals(0.4321f, restored.progress)
        assertEquals(activeTrip.start, restored.activeTrip?.start)
        assertEquals(activeTrip.destination, restored.activeTrip?.destination)
        assertEquals(savedReview, restored.findCompleted(completedTrip.id)?.review)
        assertEquals(draft, restored.reviewDrafts[completedTrip.id])
    }

    @Test
    fun pendingCompletionRestoresAndAcknowledgementRemainsIdempotent() {
        val trip = fixtureTrip("pending")
        val store = TripsStore(nowEpochMillis = { 12_000L })
        complete(store, trip)

        val restored = assertNotNull(
            TripsStore.fromSavedStateJson(
                store.toSavedStateJson(),
                nowEpochMillis = { 90_000L }
            )
        )

        assertEquals(store.snapshot(), restored.snapshot())
        assertEquals(trip.id, restored.pendingReviewTripId)
        assertNull(restored.activeTrip)
        assertEquals(1f, restored.progressFor(trip.id))

        assertEquals(
            TripActionResult.Applied,
            restored.acknowledgeReviewNavigation(trip.id)
        )
        assertEquals(
            TripActionResult.Applied,
            restored.acknowledgeReviewNavigation(trip.id)
        )

        assertNull(restored.pendingReviewTripId)
        assertEquals(1, restored.completedTrips.size)

        val acknowledgedRestoration = assertNotNull(
            TripsStore.fromSavedStateJson(restored.toSavedStateJson())
        )
        assertNull(acknowledgedRestoration.pendingReviewTripId)
        assertEquals(restored.completedTrips, acknowledgedRestoration.completedTrips)
    }

    @Test
    fun seededGenerationIsRepeatableAndEndpointsRespectMarginsAndSeparation() {
        assertEquals(
            generatedTrip(Random(17), "seeded"),
            generatedTrip(Random(17), "seeded")
        )

        repeat(32) { seed ->
            val trip = generatedTrip(Random(seed), "seed-$seed")

            listOf(trip.start, trip.destination).forEach { point ->
                assertTrue(point.x.isFinite())
                assertTrue(point.y.isFinite())
                assertTrue(
                    point.x in MockTripGenerator.SAFE_MIN..MockTripGenerator.SAFE_MAX
                )
                assertTrue(
                    point.y in MockTripGenerator.SAFE_MIN..MockTripGenerator.SAFE_MAX
                )
            }

            assertTrue(
                trip.start.normalizedDistanceTo(trip.destination) >=
                        MockTripGenerator.MIN_NORMALIZED_SEPARATION
            )
            assertTrue(trip.distanceKm.isFinite() && trip.distanceKm > 0.0)
            assertEquals(30, trip.plannedMinutes)
        }
    }

    @Test
    fun repeatedIdenticalCandidatesUseTheBoundedFallback() {
        val random = ZeroRandom()
        val trip = generatedTrip(random, "fallback")

        assertEquals(MockTripGenerator.SAFE_MIN, trip.start.x)
        assertEquals(MockTripGenerator.SAFE_MIN, trip.start.y)
        assertEquals(MockTripGenerator.SAFE_MAX, trip.destination.x)
        assertEquals(MockTripGenerator.SAFE_MAX, trip.destination.y)
        assertTrue(
            trip.start.normalizedDistanceTo(trip.destination) >=
                    MockTripGenerator.MIN_NORMALIZED_SEPARATION
        )
        assertTrue(random.calls > 0)
    }

    @Test
    fun invalidCoordinatesRatingsAndPlannerValuesAreRejected() {
        assertFailsWith<IllegalArgumentException> {
            TripPoint("Invalid", Float.NaN, 0.5f)
        }
        assertFailsWith<IllegalArgumentException> {
            TripPoint("Invalid", 1.1f, 0.5f)
        }
        assertFailsWith<IllegalArgumentException> {
            TripReview(overall = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            TripReview(overall = 3, `fun` = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            fixtureTrip("invalid-minutes").copy(plannedMinutes = 7)
        }
        assertFailsWith<IllegalArgumentException> {
            fixtureTrip("invalid-intensity").copy(attractionIntensity = 101f)
        }
    }

    @Test
    fun minuteSelectorCapsAt360AndStepsBackTo355() {
        assertEquals(360, Trip.coercePlannedMinutes(Int.MAX_VALUE))
        assertEquals(0, Trip.coercePlannedMinutes(Int.MIN_VALUE))
        assertEquals(350, Trip.coercePlannedMinutes(354))
        assertEquals(360, Trip.adjustPlannedMinutes(355, 1))
        assertEquals(360, Trip.adjustPlannedMinutes(360, 1))
        assertEquals(355, Trip.adjustPlannedMinutes(360, -1))
        assertEquals(350, Trip.adjustPlannedMinutes(355, -1))
        assertEquals(0, Trip.adjustPlannedMinutes(5, -1))
        assertEquals(0, Trip.adjustPlannedMinutes(0, -1))
    }

    @Test
    fun maximumPlannedMinutesRestoreWithoutChangingSimulationDuration() {
        val trip = fixtureTrip("maximum-minutes").copy(plannedMinutes = 360)
        val store = TripsStore(nowEpochMillis = { 12_000L })
        assertEquals(TripActionResult.Applied, store.start(trip))
        assertEquals(TripActionResult.Applied, store.updateElapsed(trip.id, 5_000L))

        val restored = assertNotNull(
            TripsStore.fromSavedStateJson(
                store.toSavedStateJson(),
                nowEpochMillis = { 90_000L }
            )
        )
        assertEquals(360, restored.activeTrip?.plannedMinutes)
        assertEquals(0.5f, restored.progress)
        assertEquals(TripActionResult.Applied, restored.updateElapsed(trip.id, 10_000L))
        assertEquals(360, restored.findCompleted(trip.id)?.plannedMinutes)
        assertEquals(1f, restored.progressFor(trip.id))
        assertFailsWith<IllegalArgumentException> {
            trip.copy(plannedMinutes = 365)
        }
    }

    private fun completedStore(vararg trips: Trip): TripsStore {
        val store = TripsStore(nowEpochMillis = { 12_000L })

        trips.forEach { trip ->
            complete(store, trip)
            assertEquals(
                TripActionResult.Applied,
                store.acknowledgeReviewNavigation(trip.id)
            )
        }

        return store
    }

    private fun complete(store: TripsStore, trip: Trip) {
        assertEquals(TripActionResult.Applied, store.start(trip))
        assertEquals(
            TripActionResult.Applied,
            store.updateElapsed(trip.id, 10_000L)
        )
    }

    private fun generatedTrip(random: Random, id: String): Trip {
        val generator = MockTripGenerator(
            random = random,
            idFactory = { id },
            nowEpochMillis = { 1_000L }
        )

        return assertIs<TripGenerationResult.Success>(
            generator.generate(
                input = TripGenerationInput(
                    title = "Generator verification",
                    description = "A predictable mock trip.",
                    imageUrl = "https://example.com/reference.jpg",
                    plannedMinutes = 30,
                    selectedTransportIds = listOf("walk"),
                    selectedRouteIds = emptyList(),
                    attractionIntensity = 65f
                ),
                transportTypes = listOf(
                    TransportType("walk", "Walk", false)
                ),
                transportRoutes = emptyList(),
                existingTripIds = emptySet()
            )
        ).trip
    }

    private fun fixtureTrip(id: String): Trip = Trip(
        id = id,
        title = "Trip $id",
        description = "Fixed record for state invariant verification.",
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

    /**
     * Produces identical random candidates.
     * The guard makes an accidentally unbounded retry fail promptly.
     */
    private class ZeroRandom : Random() {
        var calls: Int = 0
            private set

        override fun nextBits(bitCount: Int): Int {
            calls++
            check(calls <= 1_000) {
                "Endpoint generation exceeded the bounded retry allowance."
            }
            return 0
        }
    }
}
