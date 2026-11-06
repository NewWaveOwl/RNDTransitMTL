package com.example.rnd_transit_mtl

import com.example.rnd_transit_mtl.data.generation.MockTripGenerator
import com.example.rnd_transit_mtl.data.generation.TripGenerationInput
import com.example.rnd_transit_mtl.data.generation.TripGenerationResult
import com.example.rnd_transit_mtl.model.geo.GeoPoint
import com.example.rnd_transit_mtl.model.ids.TransportId
import com.example.rnd_transit_mtl.model.ids.TripId
import com.example.rnd_transit_mtl.model.ids.UserId
import com.example.rnd_transit_mtl.model.transport.TransportType
import com.example.rnd_transit_mtl.model.trip.RouteLeg
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.model.trip.TripStatus
import com.example.rnd_transit_mtl.model.trip.TripTransportSnapshot
import com.example.rnd_transit_mtl.state.trip.TripActionResult
import com.example.rnd_transit_mtl.state.trip.TripsStore
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

/** Rules the Trip record gained with places, legs, status and owner. */
class TripRecordTest {
    private val walk = TransportId("walk")
    private val bus = TransportId("bus")
    private val middle = GeoPoint(45.51, -73.59)

    private fun trip(
        legs: List<RouteLeg> = testLegs(walk),
        status: TripStatus = TripStatus.Generated,
        completedAt: Long? = null
    ) = Trip(
        id = TripId("record-test"),
        status = status,
        title = "Record test",
        description = "Fixed record for Trip rules.",
        imageUrl = "",
        origin = testOrigin,
        destination = testDestination,
        legs = legs,
        plannedMinutes = 30,
        selectedTransports = listOf(
            TripTransportSnapshot(walk, "Walk", false),
            TripTransportSnapshot(bus, "Bus", true)
        ),
        selectedRoutes = emptyList(),
        attractionIntensity = 50f,
        createdAt = 1_000L,
        completedAt = completedAt
    )

    @Test
    fun distanceIsTheSumOfTheLegs() {
        val twoLegs = listOf(
            RouteLeg.straightLine(walk, testOrigin.point, middle, 600),
            RouteLeg.straightLine(bus, middle, testDestination.point, 1200)
        )

        val record = trip(legs = twoLegs)

        assertEquals(twoLegs.sumOf { it.distanceMeters } / 1000.0, record.distanceKm)
        assertFalse(record.legs.any { it.isRouted })
    }

    @Test
    fun legsMustConnectFromOriginToDestination() {
        assertFailsWith<IllegalArgumentException> { trip(legs = emptyList()) }
        assertFailsWith<IllegalArgumentException> {
            trip(legs = testLegs(walk, from = testDestination, to = testOrigin))
        }
        assertFailsWith<IllegalArgumentException> {
            trip(legs = listOf(
                RouteLeg.straightLine(walk, testOrigin.point, middle, 600),
                RouteLeg.straightLine(bus, GeoPoint(45.52, -73.58), testDestination.point, 600)
            ))
        }
    }

    @Test
    fun legsMustUseSelectedTransports() {
        assertFailsWith<IllegalArgumentException> { trip(legs = testLegs(TransportId("metro"))) }
    }

    @Test
    fun completionTimeMatchesCompletedStatus() {
        assertFailsWith<IllegalArgumentException> { trip(status = TripStatus.Completed) }
        assertFailsWith<IllegalArgumentException> { trip(status = TripStatus.Active, completedAt = 2_000L) }
        trip(status = TripStatus.Completed, completedAt = 2_000L)
    }

    @Test
    fun storeMovesStatusThroughStartAndFinish() {
        val store = TripsStore(nowEpochMillis = { 20_000L })
        val generated = trip()

        assertEquals(TripActionResult.Applied, store.start(generated))
        assertEquals(TripStatus.Active, store.activeTrip?.status)

        assertEquals(TripActionResult.Applied, store.updateElapsed(generated.id, 10_000L))
        assertNull(store.activeTrip)
        val completed = store.completedTrips.single()
        assertEquals(TripStatus.Completed, completed.status)
        assertEquals(20_000L, completed.completedAt)
    }

    @Test
    fun cancellationTimeMatchesCancelledStatus() {
        assertFailsWith<IllegalArgumentException> { trip(status = TripStatus.Cancelled) }
        assertFailsWith<IllegalArgumentException> { trip().copy(cancelledAt = 2_000L) }
        assertFailsWith<IllegalArgumentException> { trip().copy(status = TripStatus.Cancelled, cancelledAt = 500L) }
        trip().copy(status = TripStatus.Cancelled, cancelledAt = 2_000L)
    }

    @Test
    fun cancellingWithSaveKeepsTheTripInHistory() {
        val store = TripsStore(nowEpochMillis = { 30_000L })
        val generated = trip()
        store.start(generated)

        assertEquals(TripActionResult.Applied, store.cancel(generated.id, keepInHistory = true))

        assertNull(store.activeTrip)
        assertTrue(store.completedTrips.isEmpty())
        val kept = store.cancelledTrips.single()
        assertEquals(TripStatus.Cancelled, kept.status)
        assertEquals(30_000L, kept.cancelledAt)
        assertNull(kept.completedAt)
        assertEquals(kept, store.findInHistory(generated.id))
        assertNull(store.findCompleted(generated.id))
        assertNull(store.pendingReviewTripId)
    }

    @Test
    fun cancellingWithoutSaveDiscardsTheTrip() {
        val store = TripsStore(nowEpochMillis = { 30_000L })
        val generated = trip()
        store.start(generated)

        assertEquals(TripActionResult.Applied, store.cancel(generated.id, keepInHistory = false))

        assertNull(store.activeTrip)
        assertTrue(store.cancelledTrips.isEmpty())
        assertNull(store.findInHistory(generated.id))
    }

    @Test
    fun keptCancelledTripsSurviveRestorationAndCanBeRemoved() {
        val store = TripsStore(nowEpochMillis = { 30_000L })
        val generated = trip()
        store.start(generated)
        store.cancel(generated.id, keepInHistory = true)

        val restored = assertNotNull(TripsStore.fromSavedStateJson(store.toSavedStateJson()))
        assertEquals(store.cancelledTrips, restored.cancelledTrips)

        // A kept cancelled trip still reserves its ID.
        assertIs<TripActionResult.InvalidInput>(restored.start(generated))

        assertEquals(TripActionResult.Applied, restored.removeFromHistory(generated.id))
        assertTrue(restored.cancelledTrips.isEmpty())
        assertIs<TripActionResult.MissingTrip>(restored.removeFromHistory(generated.id))
    }

    @Test
    fun storeOnlyStartsGeneratedTrips() {
        val store = TripsStore(nowEpochMillis = { 20_000L })

        assertIs<TripActionResult.InvalidInput>(store.start(trip(status = TripStatus.Active)))
        assertIs<TripActionResult.InvalidInput>(store.start(trip(status = TripStatus.Completed, completedAt = 2_000L)))
        assertNull(store.activeTrip)
    }

    @Test
    fun generatorCreatesAGeneratedOwnedTripWithOneStraightLeg() = runTest {
        val owner = UserId("user-7")
        val result = MockTripGenerator(
            random = Random(3),
            idFactory = { "owned-trip" },
            nowEpochMillis = { 1_000L }
        ).generate(
            input = TripGenerationInput(
                title = "Owned",
                description = "Owned trip.",
                imageUrl = "",
                plannedMinutes = 45,
                selectedTransportIds = listOf(walk),
                selectedRouteIds = emptyList(),
                attractionIntensity = 50f,
                ownerId = owner
            ),
            transportTypes = listOf(TransportType(walk, "Walk", usesRoutes = false)),
            transportRoutes = emptyList(),
            existingTripIds = emptySet()
        )

        val generated = assertIs<TripGenerationResult.Success>(result).trip
        assertEquals(owner, generated.ownerId)
        assertEquals(TripStatus.Generated, generated.status)
        val leg = generated.legs.single()
        assertEquals(walk, leg.transportId)
        assertFalse(leg.isRouted)
        assertEquals(45 * 60L, leg.durationSeconds)
        assertEquals(generated.origin.point.distanceMetersTo(generated.destination.point), leg.distanceMeters)
    }
}
