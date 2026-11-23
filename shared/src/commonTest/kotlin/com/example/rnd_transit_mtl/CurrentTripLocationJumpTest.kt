package com.example.rnd_transit_mtl

import com.example.rnd_transit_mtl.ViewModels.current_trip.CurrentTripLocationViewModel
import com.example.rnd_transit_mtl.ViewModels.current_trip.TripLocationAvailability
import com.example.rnd_transit_mtl.data.location.LocationJumpFilter
import com.example.rnd_transit_mtl.data.location.LocationUpdate
import com.example.rnd_transit_mtl.model.geo.GeoPoint
import com.example.rnd_transit_mtl.state.trip.TripsStore
import kotlin.test.*

class CurrentTripLocationJumpTest {
    @Test
    fun relocationAtDestinationCannotInventTravelOrCompleteWhileStationary() {
        var now = 50_000L
        val trip = guidanceTestTrip()
        val store = TripsStore { now }.also { it.start(trip) }
        val model = CurrentTripLocationViewModel(trip, store) { now }
        model.receive(LocationUpdate.Fix(trip.origin.point, 5.0, now, 0.0))
        repeat(70) {
            now += 2_000
            model.receive(LocationUpdate.Fix(trip.destination.point, 5.0, now, 0.0))
            assertEquals(0f, store.progress)
            assertTrue(store.completedTrips.isEmpty())
        }
        // Fresh, plausible movement away from the recovered point can resume progress.
        now += 30_000
        model.receive(LocationUpdate.Fix(GeoPoint(45.5028, -73.6), 5.0, now, 1.4))
        assertTrue(store.progress in 0.9f..0.98f)
        now += 30_000
        model.receive(LocationUpdate.Fix(trip.destination.point, 5.0, now, 1.4))
        assertEquals(trip.id, store.completedTrips.single().id)
    }

    @Test
    fun approachingATransferAllowsTheNextLegSpeedWithoutFreezingTheFirstBusFix() {
        var now = 50_000L
        val trip = guidanceTestTrip()
        val store = TripsStore { now }.also { it.start(trip) }
        val model = CurrentTripLocationViewModel(trip, store) { now }
        model.receive(LocationUpdate.Fix(GeoPoint(45.50085, -73.6), 5.0, now))
        now += 2_000
        val bus = LocationUpdate.Fix(GeoPoint(45.5016, -73.6), 5.0, now, 35.0)
        model.receive(bus)
        assertEquals(bus.point, model.state.value.displayedPosition)
        assertEquals(TripLocationAvailability.Available, model.state.value.availability)
        assertEquals(0.5333f, store.progress, 0.001f)
        assertTrue(store.completedTrips.isEmpty())
    }

    @Test
    fun nearbyReturnBranchCannotJumpProgressToTheEndOrConfirmArrival() {
        var now = 50_000L
        val base = guidanceTestTrip()
        val geometry = listOf(GeoPoint(45.5, -73.6), GeoPoint(45.505, -73.6),
            GeoPoint(45.505, -73.59995), GeoPoint(45.5, -73.59995))
        val trip = base.copy(origin = base.origin.copy(point = geometry.first()),
            destination = base.destination.copy(point = geometry.last()),
            legs = listOf(base.legs.first().copy(geometry = geometry)),
            selectedTransports = base.selectedTransports.take(1), selectedRoutes = emptyList())
        val store = TripsStore { now }.also { it.start(trip) }
        val model = CurrentTripLocationViewModel(trip, store) { now }
        model.receive(LocationUpdate.Fix(GeoPoint(45.5001, -73.6), 3.0, now))
        val progress = store.progress
        now += 2_000
        val adjacent = GeoPoint(45.50015, -73.59995)
        model.receive(LocationUpdate.Fix(adjacent, 3.0, now))
        assertEquals(adjacent, model.state.value.displayedPosition, "The measured movement itself is plausible")
        assertEquals(progress, store.progress, "A nearby return street is far away along the actual route")
        assertTrue(store.completedTrips.isEmpty())
    }

    @Test
    fun suddenDestinationFixCannotTeleportTheMarkerAdvanceProgressOrFinishTheTrip() {
        var now = 50_000L
        val trip = guidanceTestTrip()
        val store = TripsStore { now }.also { it.start(trip) }
        val shared = LocationJumpFilter()
        val model = CurrentTripLocationViewModel(trip, store, jumpFilter = shared) { now }
        val first = LocationUpdate.Fix(trip.origin.point, 5.0, now)
        shared.evaluate(first, now) // Planner observes each reading before the trip does.
        model.receive(first)
        val progress = store.progress
        now += 2_000
        val jump = LocationUpdate.Fix(trip.destination.point, 5.0, now)
        assertFalse(assertNotNull(shared.evaluate(jump, now)).accepted)
        model.receive(jump)
        assertEquals(jump, model.state.value.fix, "Keep the raw measurement separate")
        assertEquals(first.point, model.state.value.displayedPosition)
        assertEquals(TripLocationAvailability.LastKnown, model.state.value.availability)
        assertTrue(assertNotNull(model.state.value.uncertaintyRadiusMeters) > 300.0)
        assertEquals(progress, store.progress)
        assertTrue(store.completedTrips.isEmpty())
        now += 2_000
        val recovered = LocationUpdate.Fix(GeoPoint(45.50005, -73.6), 5.0, now)
        shared.evaluate(recovered, now)
        model.receive(recovered)
        assertEquals(recovered.point, model.state.value.displayedPosition)
        assertEquals(TripLocationAvailability.Available, model.state.value.availability)
        assertTrue(store.progress > progress)
    }

    @Test
    fun repeatedFreshOutliersRetainTheMarkerWithoutAFalseNotUpdatingWarning() {
        var now = 50_000L
        val trip = guidanceTestTrip()
        val store = TripsStore { now }.also { it.start(trip) }
        val model = CurrentTripLocationViewModel(trip, store) { now }
        model.receive(LocationUpdate.Fix(trip.origin.point, 5.0, now))
        repeat(20) { index ->
            now += 2_000
            val latitude = if (index % 2 == 0) 45.6 else 45.4
            model.receive(LocationUpdate.Fix(GeoPoint(latitude, -73.6), 200.0, now))
            model.refreshFreshness()
            assertEquals(trip.origin.point, model.state.value.displayedPosition)
            assertEquals(TripLocationAvailability.LastKnown, model.state.value.availability)
            assertEquals(0f, store.progress)
        }
        now += 30_001
        model.refreshFreshness()
        assertEquals(TripLocationAvailability.Stale, model.state.value.availability)
        assertEquals(trip.origin.point, model.state.value.displayedPosition)
    }

    @Test
    fun consistentPreciseOffRouteReadingsCanConfirmARealRelocation() {
        var now = 50_000L
        val trip = guidanceTestTrip()
        val store = TripsStore { now }.also { it.start(trip) }
        val model = CurrentTripLocationViewModel(trip, store) { now }
        model.receive(LocationUpdate.Fix(trip.origin.point, 5.0, now))
        val offRoute = GeoPoint(45.51, -73.61)
        repeat(3) { index ->
            now += 2_000
            model.receive(LocationUpdate.Fix(offRoute, 5.0, now))
            assertEquals(if (index < 2) trip.origin.point else offRoute, model.state.value.displayedPosition)
        }
        assertTrue(model.state.value.offRoute)
        assertEquals(0f, store.progress)
        assertTrue(store.completedTrips.isEmpty())
    }

    @Test
    fun rejectedWeakRouteMatchesHoldTheVisiblePointUntilConsistentReadingsConfirmIt() {
        var now = 50_000L
        val trip = guidanceTestTrip()
        val store = TripsStore { now }.also { it.start(trip) }
        val model = CurrentTripLocationViewModel(trip, store) { now }
        model.receive(LocationUpdate.Fix(trip.origin.point, 5.0, now))
        // Plausible displacement, but too far from the route to produce a confident match.
        val outside = GeoPoint(45.5, -73.5985)
        now += 30_000
        repeat(3) { index ->
            now += 2_000
            model.receive(LocationUpdate.Fix(outside, 150.0, now))
            assertEquals(if (index < 2) trip.origin.point else outside, model.state.value.displayedPosition)
        }
        assertEquals(0f, store.progress)
        assertTrue(store.completedTrips.isEmpty())
    }
}
