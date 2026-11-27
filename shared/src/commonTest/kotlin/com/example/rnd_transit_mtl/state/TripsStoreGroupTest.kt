package com.example.rnd_transit_mtl.state

import com.example.rnd_transit_mtl.model.ids.GroupTripId
import com.example.rnd_transit_mtl.model.ids.TransportId
import com.example.rnd_transit_mtl.model.ids.TripId
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.model.trip.TripStatus
import com.example.rnd_transit_mtl.model.trip.TripTransportSnapshot
import com.example.rnd_transit_mtl.state.trip.TripActionResult
import com.example.rnd_transit_mtl.state.trip.TripsStore
import com.example.rnd_transit_mtl.testDestination
import com.example.rnd_transit_mtl.testLegs
import com.example.rnd_transit_mtl.testOrigin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class TripsStoreGroupTest {
    private val shared = GroupTripId("shared-route")
    private val trip = Trip(
        id = TripId("host-trip"),
        status = TripStatus.Generated,
        title = "Old Port walk",
        description = "Test fixture",
        imageUrl = "",
        origin = testOrigin,
        destination = testDestination,
        legs = testLegs(TransportId("walk")),
        plannedMinutes = 30,
        selectedTransports = listOf(TripTransportSnapshot(TransportId("walk"), "Walk", false)),
        selectedRoutes = emptyList(),
        attractionIntensity = 50f,
        createdAt = 1_000L,
    )

    @Test
    fun theActiveTripIsLinkedToTheGroupTripItWasSharedAs() {
        val store = TripsStore(nowEpochMillis = { 2_000L })
        store.start(trip)

        assertEquals(TripActionResult.Applied, store.attachGroupTrip(trip.id, shared))
        assertEquals(shared, store.snapshot().activeTrip?.trip?.groupTripId)
    }

    @Test
    fun onlyTheActiveTripCanBeLinkedAndOnlyOnce() {
        val store = TripsStore(nowEpochMillis = { 2_000L })
        assertIs<TripActionResult.MissingTrip>(store.attachGroupTrip(trip.id, shared))

        store.start(trip)
        assertIs<TripActionResult.MissingTrip>(store.attachGroupTrip(TripId("other-trip"), shared))
        store.attachGroupTrip(trip.id, shared)
        assertIs<TripActionResult.InvalidInput>(store.attachGroupTrip(trip.id, GroupTripId("second-route")))
        assertEquals(shared, store.snapshot().activeTrip?.trip?.groupTripId)
    }

    @Test
    fun theGroupLinkSurvivesSavedStateRestoration() {
        val store = TripsStore(nowEpochMillis = { 2_000L })
        store.start(trip.copy(groupTripId = shared))

        val restored = assertNotNull(TripsStore.fromSavedStateJson(store.toSavedStateJson()))

        assertEquals(shared, restored.snapshot().activeTrip?.trip?.groupTripId)
    }
}
