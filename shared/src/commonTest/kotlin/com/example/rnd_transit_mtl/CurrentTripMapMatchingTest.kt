package com.example.rnd_transit_mtl

import com.example.rnd_transit_mtl.ViewModels.current_trip.CurrentTripLocationViewModel
import com.example.rnd_transit_mtl.ViewModels.current_trip.TripLocationAvailability
import com.example.rnd_transit_mtl.data.location.LocationUpdate
import com.example.rnd_transit_mtl.model.geo.GeoPoint
import com.example.rnd_transit_mtl.model.geo.uncertaintyRing
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.model.trip.TripRouteGeometry
import com.example.rnd_transit_mtl.state.trip.TripsStore
import kotlin.test.*

class CurrentTripMapMatchingTest {
    private fun walkingTrip(points: List<GeoPoint>): Trip {
        val trip = guidanceTestTrip()
        return trip.copy(origin = trip.origin.copy(point = points.first()), destination = trip.destination.copy(point = points.last()),
            legs = listOf(trip.legs.first().copy(geometry = points,
                distanceMeters = points.zipWithNext().sumOf { (a, b) -> a.distanceMetersTo(b) })),
            selectedTransports = trip.selectedTransports.take(1), selectedRoutes = emptyList())
    }

    @Test
    fun weakGpsSnapsToTheCurrentLegWhileKeepingRawGpsAndConfirmedProgressSeparate() {
        var now = 50_000L
        val trip = walkingTrip(listOf(GeoPoint(45.5, -73.6), GeoPoint(45.505, -73.6)))
        val store = TripsStore { now }.also { it.start(trip) }
        val model = CurrentTripLocationViewModel(trip, store) { now }
        model.receive(LocationUpdate.Fix(GeoPoint(45.5002, -73.6), 5.0, now))
        val confirmed = store.progress
        now += 10_000
        val coarse = LocationUpdate.Fix(GeoPoint(45.5003, -73.5997), 80.0, now)
        model.receive(coarse)
        model.refreshFreshness()
        assertEquals(coarse, model.state.value.fix)
        assertEquals(TripLocationAvailability.LowAccuracy, model.state.value.availability)
        val displayed = assertNotNull(model.state.value.displayedPosition)
        assertEquals(-73.6, displayed.longitude, 0.000001)
        assertEquals(45.5003, displayed.latitude, 0.000001)
        assertTrue(assertNotNull(model.state.value.displayedProgress) > confirmed)
        assertTrue(assertNotNull(model.state.value.uncertaintyRadiusMeters) >= coarse.accuracyMeters + coarse.point.distanceMetersTo(displayed) - 0.01)
        assertEquals(confirmed, store.progress)
        val weakRadius = assertNotNull(model.state.value.uncertaintyRadiusMeters)
        now += 30_001
        model.receive(LocationUpdate.Unavailable)
        assertEquals(displayed, model.state.value.displayedPosition)
        assertEquals(TripLocationAvailability.Stale, model.state.value.availability)
        assertTrue(assertNotNull(model.state.value.uncertaintyRadiusMeters) > weakRadius)
        now++
        val recovered = LocationUpdate.Fix(GeoPoint(45.5004, -73.6), 5.0, now)
        model.receive(recovered)
        assertEquals(recovered.point, model.state.value.displayedPosition)
        assertNull(model.state.value.matchedPosition)
        assertNull(model.state.value.estimatedPosition)
        assertEquals(TripLocationAvailability.Available, model.state.value.availability)
        assertTrue(assertNotNull(model.state.value.uncertaintyRadiusMeters) < weakRadius)
    }

    @Test
    fun matchingPrefersRecentProgressOverANearerReturnBranchAndRejectsAmbiguity() {
        var now = 50_000L
        val trip = walkingTrip(listOf(GeoPoint(45.5, -73.6), GeoPoint(45.501, -73.6),
            GeoPoint(45.501, -73.5997), GeoPoint(45.5, -73.5997)))
        val store = TripsStore { now }.also { it.start(trip) }
        val model = CurrentTripLocationViewModel(trip, store) { now }
        model.receive(LocationUpdate.Fix(GeoPoint(45.5001, -73.6), 5.0, now))
        now += 6_000
        model.receive(LocationUpdate.Fix(GeoPoint(45.50015, -73.59975), 80.0, now))
        assertEquals(-73.6, assertNotNull(model.state.value.matchedPosition).longitude, 0.000001)
        assertTrue(assertNotNull(model.state.value.matchedProgress) < 0.15f)
        val geometry = TripRouteGeometry(trip)
        assertNull(geometry.matchOnLeg(GeoPoint(45.5002, -73.59985), 0, 0.5f, 0.5f, 500.0, 100.0),
            "Equally plausible distant branches must not be forced onto the route")
    }

    @Test
    fun weakFixCannotJumpToAnotherLegOrConfirmArrival() {
        var now = 50_000L
        val trip = guidanceTestTrip()
        val store = TripsStore { now }.also { it.start(trip) }
        val model = CurrentTripLocationViewModel(trip, store) { now }
        model.receive(LocationUpdate.Fix(GeoPoint(45.5009, -73.6), 5.0, now))
        val confirmed = store.progress
        now += 3_000
        model.receive(LocationUpdate.Fix(GeoPoint(45.5011, -73.5999), 80.0, now))
        val matched = assertNotNull(model.state.value.matchedProgress)
        assertTrue(matched < 1f / 3f, "Only reliable GPS may confirm entering the bus leg")
        assertEquals(confirmed, store.progress)
        now += 3_000
        val farLeg = LocationUpdate.Fix(trip.destination.point, 80.0, now)
        val retained = model.state.value.displayedPosition
        model.receive(farLeg)
        assertNull(model.state.value.matchedPosition)
        assertEquals(retained, model.state.value.displayedPosition)
        assertTrue(store.completedTrips.isEmpty())

        val walk = walkingTrip(listOf(trip.origin.point, trip.legs.first().end))
        val walkStore = TripsStore { now }.also { it.start(walk) }
        val walkModel = CurrentTripLocationViewModel(walk, walkStore) { now }
        walkModel.receive(LocationUpdate.Fix(GeoPoint(45.5009, -73.6), 5.0, now))
        now += 3_000
        walkModel.receive(LocationUpdate.Fix(walk.destination.point, 80.0, now))
        assertTrue(assertNotNull(walkModel.state.value.matchedProgress) < 1f)
        assertTrue(walkStore.completedTrips.isEmpty())
        now++
        walkModel.receive(LocationUpdate.Fix(walk.destination.point, 5.0, now))
        assertEquals(walk.id, walkStore.completedTrips.single().id)
    }

    @Test
    fun remoteReadingsKeepTheMarkerUntilConfirmedAndOffRouteOrFallbackPositionsRemainUnsnapped() {
        var now = 50_000L
        val trip = walkingTrip(listOf(GeoPoint(45.5, -73.6), GeoPoint(45.505, -73.6)))
        val store = TripsStore { now }.also { it.start(trip) }
        val model = CurrentTripLocationViewModel(trip, store) { now }
        model.receive(LocationUpdate.Fix(trip.origin.point, 5.0, now))
        now += 2_000
        val distant = LocationUpdate.Fix(GeoPoint(45.51, -73.61), 200.0, now)
        model.receive(distant)
        assertNull(model.state.value.matchedPosition)
        assertEquals(trip.origin.point, model.state.value.displayedPosition)
        assertTrue(assertNotNull(model.state.value.uncertaintyRadiusMeters) >= 200.0)
        repeat(3) {
            now += 2_000
            model.receive(distant.copy(accuracyMeters = 5.0, timestampEpochMillis = now))
        }
        assertEquals(distant.point, model.state.value.displayedPosition)
        now++
        model.receive(LocationUpdate.Fix(trip.origin.point, 80.0, now))
        assertNull(model.state.value.matchedPosition, "An observed off-route user must not be pulled back onto the route")
        val fallback = trip.copy(legs = trip.legs.map { it.copy(isRouted = false) })
        val fallbackModel = CurrentTripLocationViewModel(fallback, store) { now }
        fallbackModel.receive(LocationUpdate.Fix(trip.origin.point, 80.0, now))
        assertNull(fallbackModel.state.value.matchedPosition)
        assertEquals(trip.origin.point, fallbackModel.state.value.displayedPosition)
    }

    @Test
    fun pausesAndPermissionLossFreezeTheLastVisibleEstimateWithoutRemovingTheMarker() {
        var now = 50_000L
        val trip = walkingTrip(listOf(GeoPoint(45.5, -73.6), GeoPoint(45.505, -73.6)))
        val store = TripsStore { now }.also { it.start(trip) }
        val model = CurrentTripLocationViewModel(trip, store) { now }
        assertNull(model.state.value.displayedPosition, "A location must never be fabricated before the first fix")
        model.receive(LocationUpdate.Fix(trip.origin.point, 3.0, now, 1.4))
        now += 8_000
        model.receive(LocationUpdate.Fix(GeoPoint(45.5001, -73.6), 3.0, now, 1.4))
        now += 10_000
        model.refreshFreshness()
        val estimate = assertNotNull(model.state.value.estimatedPosition)
        val confirmed = store.progress
        model.stop()
        now += 60_000
        model.refreshFreshness()
        assertEquals(estimate, model.state.value.displayedPosition)
        model.receive(LocationUpdate.PermissionDenied)
        assertEquals(estimate, model.state.value.displayedPosition)
        assertEquals(TripLocationAvailability.PermissionDenied, model.state.value.availability)
        assertTrue(assertNotNull(model.state.value.uncertaintyRadiusMeters) >= 75.0)
        model.unsupported()
        assertEquals(estimate, model.state.value.displayedPosition)
        assertEquals(confirmed, store.progress)
    }

    @Test
    fun uncertaintyRingHasTheRequestedGroundRadiusIncludingNearTheDateLineAndPoles() {
        for (center in listOf(GeoPoint(45.5, -73.6), GeoPoint(0.0, 179.9999), GeoPoint(89.999, 0.0))) {
            val ring = center.uncertaintyRing(120.0)
            assertEquals(49, ring.size)
            assertEquals(ring.first(), ring.last())
            ring.forEach { assertEquals(120.0, center.distanceMetersTo(it), 0.1) }
        }
        assertTrue(GeoPoint(45.5, -73.6).uncertaintyRing(Double.NaN).isEmpty())
    }
}
