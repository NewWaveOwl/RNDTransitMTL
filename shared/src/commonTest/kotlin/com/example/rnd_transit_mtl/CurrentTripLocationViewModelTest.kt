package com.example.rnd_transit_mtl

import com.example.rnd_transit_mtl.ViewModels.current_trip.CurrentTripLocationViewModel
import com.example.rnd_transit_mtl.ViewModels.current_trip.TripLocationAvailability
import com.example.rnd_transit_mtl.data.location.LocationUpdate
import com.example.rnd_transit_mtl.model.geo.GeoPoint
import com.example.rnd_transit_mtl.model.trip.compactGuidanceAt
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.model.trip.pointAlongRoute
import com.example.rnd_transit_mtl.state.trip.TripsStore
import com.example.rnd_transit_mtl.simulation.ActiveTripSimulation
import kotlin.time.TestTimeSource
import kotlin.time.Duration.Companion.seconds
import kotlin.test.*

/** Verifies real-position progress, freshness and restoration without advancing a playback clock. */
class CurrentTripLocationViewModelTest {
    @Test
    fun gpsMovesAlongTheRouteButStaleDeniedAndInaccurateReadingsCannotCompleteIt() {
        var now = 1_000L
        val trip = guidanceTestTrip()
        val store = TripsStore(nowEpochMillis = { now })
        store.start(trip)
        val model = CurrentTripLocationViewModel(trip, store) { now }
        model.receive(LocationUpdate.Fix(trip.origin.point, 5.0, now))
        assertEquals("Start on Rue Sherbrooke", trip.compactGuidanceAt(store.progress, trip.origin.point))
        now += 2_000
        val halfway = GeoPoint(45.5015, -73.6)
        model.receive(LocationUpdate.Fix(halfway, 5.0, now))
        assertEquals(halfway, model.state.value.fix?.point)
        assertEquals(0.5f, store.progress, 0.001f)
        assertEquals(0L, store.elapsedMillis)
        assertContains(trip.compactGuidanceAt(store.progress, halfway), "Ride Bus 24")
        val savedProgress = store.progress
        now += 30_001
        model.refreshFreshness()
        assertEquals(TripLocationAvailability.Stale, model.state.value.availability)
        assertEquals(savedProgress, store.progress)
        model.receive(LocationUpdate.Fix(trip.destination.point, 5.0, now - 20_000))
        assertTrue(store.completedTrips.isEmpty())
        model.receive(LocationUpdate.Fix(trip.destination.point, 200.0, now))
        assertEquals(savedProgress, store.progress)
        assertTrue(store.completedTrips.isEmpty())
        now += 1_000
        model.receive(LocationUpdate.Fix(GeoPoint(45.6, -73.7), 5.0, now))
        assertTrue(model.state.value.offRoute)
        assertEquals(savedProgress, store.progress)
        model.receive(LocationUpdate.PermissionDenied)
        assertNull(model.state.value.fix)
        assertEquals(TripLocationAvailability.PermissionDenied, model.state.value.availability)
        assertTrue(store.completedTrips.isEmpty())
        val restored = assertNotNull(TripsStore.fromSavedStateJson(store.toSavedStateJson(), { now }))
        assertEquals(savedProgress, restored.progress)
        assertEquals(trip.id, restored.activeTrip?.id)
    }

    private fun walkingTrip(points: List<GeoPoint>): Trip {
        val trip = guidanceTestTrip()
        return trip.copy(
            origin = trip.origin.copy(point = points.first()),
            destination = trip.destination.copy(point = points.last()),
            legs = listOf(trip.legs.first().copy(geometry = points,
                distanceMeters = points.zipWithNext().sumOf { (a, b) -> a.distanceMetersTo(b) })),
            selectedTransports = trip.selectedTransports.take(1),
            selectedRoutes = emptyList(),
        )
    }

    @Test
    fun gapFollowsARouteBendWarnsAfterThirtySecondsAndRecoversFromGps() {
        var now = 1_000L
        val bend = GeoPoint(45.50015, -73.6)
        val trip = walkingTrip(listOf(GeoPoint(45.5, -73.6), bend, GeoPoint(45.50015, -73.599)))
        val store = TripsStore { now }.also { it.start(trip) }
        val model = CurrentTripLocationViewModel(trip, store) { now }
        model.receive(LocationUpdate.Fix(trip.origin.point, 3.0, now, 1.4))
        now += 8_000
        val lastFix = LocationUpdate.Fix(GeoPoint(45.5001, -73.6), 3.0, now, 1.4)
        model.receive(lastFix)
        val measuredProgress = store.progress
        val lastFixAt = now
        now += 4_000
        model.refreshFreshness()
        assertEquals(TripLocationAvailability.Available, model.state.value.availability)
        now += 6_000
        model.receive(LocationUpdate.Unavailable)
        val estimated = model.state.value
        assertEquals(TripLocationAvailability.Estimated, estimated.availability)
        assertEquals(lastFix, estimated.fix, "An estimate must never masquerade as a measured fix")
        val point = assertNotNull(estimated.estimatedPosition)
        assertEquals(bend.latitude, point.latitude, 0.000001, "The estimate must turn with the geometry")
        assertTrue(point.longitude > bend.longitude && point.longitude < trip.destination.point.longitude)
        assertTrue(assertNotNull(estimated.estimatedProgress) > measuredProgress)
        assertEquals(measuredProgress, store.progress, "Display-only estimates must not be persisted as GPS")
        now = lastFixAt + 30_000
        model.refreshFreshness()
        assertEquals(TripLocationAvailability.Estimated, model.state.value.availability)
        val thirtySecondEstimate = assertNotNull(model.state.value.estimatedProgress)
        now++
        model.refreshFreshness()
        assertEquals(TripLocationAvailability.Stale, model.state.value.availability)
        assertTrue(assertNotNull(model.state.value.estimatedProgress) >= thirtySecondEstimate)
        now += 5_000
        model.receive(LocationUpdate.Unavailable)
        assertEquals(TripLocationAvailability.Stale, model.state.value.availability, "Repeated outages must not reset the grace period")
        assertTrue(assertNotNull(model.state.value.estimatedProgress) > thirtySecondEstimate,
            "The vector should continue along the current section while the warning is visible")
        now += 100_000
        model.refreshFreshness()
        val finalEstimate = model.state.value.estimatedPosition
        now += 100_000
        model.refreshFreshness()
        assertEquals(finalEstimate, model.state.value.estimatedPosition, "The estimate must stop before arrival")
        assertTrue(store.completedTrips.isEmpty())
        val recovered = GeoPoint(bend.latitude, -73.5998)
        model.receive(LocationUpdate.Fix(recovered, 3.0, now))
        assertEquals(TripLocationAvailability.Available, model.state.value.availability)
        assertEquals(recovered, model.state.value.fix?.point)
        assertNull(model.state.value.estimatedPosition)
        assertNull(model.state.value.estimatedProgress)
        assertTrue(store.progress > measuredProgress)
    }

    @Test
    fun waitingAndUnavailableWithoutAnyFixHaveAThirtySecondGracePeriod() {
        var now = 1_000L
        val trip = guidanceTestTrip()
        val store = TripsStore { now }.also { it.start(trip) }
        val model = CurrentTripLocationViewModel(trip, store) { now }
        model.startTracking()
        repeat(3) {
            now += 10_000
            model.receive(LocationUpdate.Unavailable)
            assertEquals(TripLocationAvailability.Waiting, model.state.value.availability)
        }
        now++
        model.refreshFreshness()
        assertEquals(TripLocationAvailability.Stale, model.state.value.availability)
        assertNull(model.state.value.fix)
        assertNull(model.state.value.estimatedPosition)
        assertEquals(0f, store.progress)
        model.stop()
        now += 100_000
        model.startTracking()
        model.refreshFreshness()
        assertEquals(TripLocationAvailability.Waiting, model.state.value.availability)
    }

    @Test
    fun estimatesCannotCrossATransferOrCompleteATrip() {
        var now = 1_000L
        val trip = guidanceTestTrip()
        val store = TripsStore { now }.also { it.start(trip) }
        val model = CurrentTripLocationViewModel(trip, store) { now }
        model.receive(LocationUpdate.Fix(GeoPoint(45.5008, -73.6), 3.0, now, 1.4))
        now += 8_000
        model.receive(LocationUpdate.Fix(GeoPoint(45.5009, -73.6), 3.0, now, 1.4))
        now += 20_000
        model.refreshFreshness()
        assertTrue(assertNotNull(model.state.value.estimatedProgress) < 1f / 3f, "No estimated boarding")
        now += 1_000
        model.receive(LocationUpdate.Fix(GeoPoint(45.5028, -73.6), 3.0, now, 1.4))
        now += 8_000
        model.receive(LocationUpdate.Fix(GeoPoint(45.5029, -73.6), 3.0, now, 1.4))
        val measuredProgress = store.progress
        now += 30_001
        model.refreshFreshness()
        assertTrue(assertNotNull(model.state.value.estimatedProgress) < 1f)
        assertNotNull(store.activeTrip)
        assertTrue(store.completedTrips.isEmpty())
        assertEquals(measuredProgress, store.progress)
        now += 1_000
        model.receive(LocationUpdate.Fix(trip.destination.point, 3.0, now))
        assertEquals(trip.id, store.completedTrips.single().id)
    }

    @Test
    fun unreliableDuplicateAndOldReadingsCannotExtendTheGracePeriod() {
        var now = 1_000L
        val trip = guidanceTestTrip()
        val store = TripsStore { now }.also { it.start(trip) }
        val model = CurrentTripLocationViewModel(trip, store) { now }
        model.receive(LocationUpdate.Fix(trip.origin.point, 3.0, now))
        val goodFix = model.state.value.fix
        now += 30_001
        model.receive(LocationUpdate.Fix(trip.destination.point, 200.0, now))
        model.receive(LocationUpdate.Fix(trip.destination.point, 3.0, now - 20_000))
        model.receive(LocationUpdate.Fix(trip.destination.point, 3.0, 1_000L))
        assertEquals(TripLocationAvailability.Stale, model.state.value.availability)
        assertEquals(goodFix, model.state.value.fix)
        assertTrue(store.completedTrips.isEmpty())
    }

    @Test
    fun stationarySpeedOffRoutePermissionDenialAndStoppingCancelPrediction() {
        var now = 1_000L
        val trip = guidanceTestTrip()
        val store = TripsStore { now }.also { it.start(trip) }
        val model = CurrentTripLocationViewModel(trip, store) { now }
        model.receive(LocationUpdate.Fix(trip.origin.point, 3.0, now, 1.4))
        now += 8_000
        model.receive(LocationUpdate.Fix(GeoPoint(45.5001, -73.6), 3.0, now, 0.0))
        now += 10_000
        model.refreshFreshness()
        assertNull(model.state.value.estimatedPosition, "A stopped device must not drift from GPS jitter")
        model.receive(LocationUpdate.Fix(GeoPoint(45.6, -73.7), 3.0, now))
        now += 10_000
        model.refreshFreshness()
        assertTrue(model.state.value.offRoute)
        assertNull(model.state.value.estimatedPosition)
        model.receive(LocationUpdate.PermissionDenied)
        now += 31_000
        model.refreshFreshness()
        assertEquals(TripLocationAvailability.PermissionDenied, model.state.value.availability)
        assertNull(model.state.value.fix)
        model.stop()
        model.refreshFreshness()
        assertEquals(TripLocationAvailability.Waiting, model.state.value.availability)
        assertNull(model.state.value.estimatedPosition)
    }

    @Test
    fun signedVectorCanFollowTheRouteBackwardsButFallbackAndImplausibleJumpsDoNotCoast() {
        var now = 1_000L
        val trip = guidanceTestTrip()
        val store = TripsStore { now }.also { it.start(trip) }
        val model = CurrentTripLocationViewModel(trip, store) { now }
        model.receive(LocationUpdate.Fix(GeoPoint(45.5002, -73.6), 3.0, now))
        now += 8_000
        model.receive(LocationUpdate.Fix(GeoPoint(45.5001, -73.6), 3.0, now))
        val measuredProgress = store.progress
        now += 5_000
        model.refreshFreshness()
        assertTrue(assertNotNull(model.state.value.estimatedProgress) < measuredProgress)
        model.receive(LocationUpdate.Fix(GeoPoint(45.5009, -73.6), 3.0, now))
        now += 5_000
        model.refreshFreshness()
        assertNull(model.state.value.estimatedPosition, "Implausible walking jumps cannot create a vector")
        val fallback = trip.copy(legs = trip.legs.map { it.copy(isRouted = false) })
        val fallbackModel = CurrentTripLocationViewModel(fallback, store) { now }
        fallbackModel.receive(LocationUpdate.Fix(trip.origin.point, 3.0, now))
        now += 8_000
        fallbackModel.receive(LocationUpdate.Fix(GeoPoint(45.5001, -73.6), 3.0, now))
        now += 5_000
        fallbackModel.refreshFreshness()
        assertNull(fallbackModel.state.value.estimatedPosition)
    }

    @Test
    fun routeInterpolationHandlesZeroLengthSegmentsAndInvalidProgress() {
        val origin = GeoPoint(45.5, -73.6)
        val end = GeoPoint(45.501, -73.6)
        val trip = walkingTrip(listOf(origin, origin, end))
        assertEquals(origin, trip.pointAlongRoute(Float.NaN))
        assertEquals(origin, trip.pointAlongRoute(-1f))
        assertEquals(45.5005, trip.pointAlongRoute(0.5f).latitude, 0.000001)
        assertEquals(end, trip.pointAlongRoute(2f))
    }

    @Test
    fun destinationDoesNotCompleteOnInitialReadingButArrivalAfterTravellingCompletesOnce() {
        var now = 1_000L
        val trip = guidanceTestTrip()
        val store = TripsStore(nowEpochMillis = { now })
        store.start(trip)
        val model = CurrentTripLocationViewModel(trip, store) { now }
        repeat(2) {
            now += 1_000
            model.receive(LocationUpdate.Fix(trip.destination.point, 5.0, now))
        }
        assertTrue(store.completedTrips.isEmpty())
        now += 1_000
        model.receive(LocationUpdate.Fix(trip.origin.point, 5.0, now))
        now += 1_000
        model.receive(LocationUpdate.Fix(trip.destination.point, 5.0, now))
        assertEquals(trip.id, store.completedTrips.single().id)
        assertNull(store.activeTrip)
        model.receive(LocationUpdate.Fix(trip.destination.point, 5.0, now))
        assertEquals(1, store.completedTrips.size)
    }

    @Test
    fun compactHintUsesTheSavedStreetThenContinuesAndNeverInventsStreetNames() {
        val trip = guidanceTestTrip()
        assertEquals("Start on Rue Sherbrooke", trip.compactGuidanceAt(0f, trip.origin.point))
        assertEquals("Continue on Rue Sherbrooke", trip.compactGuidanceAt(0.1f, GeoPoint(45.5003, -73.6)))
        assertEquals("Go to Start", trip.compactGuidanceAt(0f, GeoPoint(45.51, -73.6)))
        val noStreets = trip.copy(legs = trip.legs.map { it.copy(navigation = null) })
        assertEquals("Start towards Park", noStreets.compactGuidanceAt(0f, noStreets.origin.point))
    }

    @Test
    fun debugPlaybackResumesAtTheGpsPositionOfTheSameStoredTrip() {
        val trip = guidanceTestTrip()
        val store = TripsStore(nowEpochMillis = { 1_000L })
        store.start(trip)
        store.updateRouteProgress(trip.id, 0.5f)
        val clock = TestTimeSource()
        val simulation = ActiveTripSimulation(store, trip.id, clock)
        val session = assertNotNull(simulation.resume())
        assertEquals(5_000L, store.elapsedMillis)
        assertNull(store.state.value.activeTrip?.routeProgress)
        clock += 1.seconds
        assertTrue(simulation.checkpoint(session))
        assertEquals(0.6f, store.progress)
        assertEquals(trip.id, store.activeTrip?.id)
    }
}
