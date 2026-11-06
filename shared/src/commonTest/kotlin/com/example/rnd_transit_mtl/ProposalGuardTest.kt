package com.example.rnd_transit_mtl

import com.example.rnd_transit_mtl.model.ids.TransportId
import com.example.rnd_transit_mtl.model.ids.TripId
import com.example.rnd_transit_mtl.View.main.ProposalGuard
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.model.trip.TripPoint
import com.example.rnd_transit_mtl.model.trip.TripTransportSnapshot
import com.example.rnd_transit_mtl.state.trip.TripActionResult
import com.example.rnd_transit_mtl.state.trip.TripsStore
import kotlin.test.*
import com.example.rnd_transit_mtl.model.trip.TripStatus

class ProposalGuardTest {
    private fun trip(id: String) = Trip(
        id = TripId(id),
        status = TripStatus.Generated,
        title = "Sample trip",
        description = "Test fixture",
        imageUrl = "",
        origin = testOrigin,
        destination = testDestination,
        legs = testLegs(TransportId("walk")),
        plannedMinutes = 30,
        selectedTransports = listOf(
            TripTransportSnapshot(TransportId("walk"), "Walk", false)
        ),
        selectedRoutes = emptyList(),
        attractionIntensity = 50f,
        createdAt = 1_000L
    )

    @Test
    fun failureCannotActivateATrip() {
        val store = TripsStore(nowEpochMillis = { 2_000L })
        val guard = ProposalGuard()
        val token = guard.begin()

        assertTrue(guard.complete(token, null))
        assertIs<TripActionResult.InvalidInput>(
            guard.start(store, trip("failed"))
        )
        assertNull(store.activeTrip)
    }

    @Test
    fun cancelledAndSupersededResultsAreIgnored() {
        val guard = ProposalGuard()
        val old = guard.begin()
        guard.cancel()

        assertFalse(guard.complete(old, TripId("cancelled")))

        val current = guard.begin()
        assertFalse(guard.complete(old, TripId("old")))
        assertTrue(guard.complete(current, TripId("current")))
        assertFalse(guard.canStart(TripId("old")))
        assertTrue(guard.canStart(TripId("current")))
    }

    @Test
    fun successfulPreviewDoesNotStartUntilExplicitlyConfirmed() {
        val store = TripsStore(nowEpochMillis = { 2_000L })
        val guard = ProposalGuard()
        val proposed = trip("accepted")

        val token = guard.begin()
        assertTrue(guard.complete(token, proposed.id))
        assertNull(store.activeTrip)

        assertEquals(
            TripActionResult.Applied,
            guard.start(store, proposed)
        )
        assertEquals(proposed.id, store.activeTrip?.id)

        assertIs<TripActionResult.InvalidInput>(
            guard.start(store, proposed)
        )
    }
}
