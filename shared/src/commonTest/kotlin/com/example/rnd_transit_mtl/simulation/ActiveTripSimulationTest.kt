package com.example.rnd_transit_mtl.simulation

import com.example.rnd_transit_mtl.model.Trip
import com.example.rnd_transit_mtl.model.TripPoint
import com.example.rnd_transit_mtl.model.TripTransportSnapshot
import com.example.rnd_transit_mtl.state.TripActionResult
import com.example.rnd_transit_mtl.state.TripsStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource

class ActiveTripSimulationTest {
    @Test
    fun irregularFramesUseActualElapsedTimeAndCompleteOnce() {
        val trip = sampleTrip()
        val store = TripsStore(nowEpochMillis = { 2_000L })
        val timeSource = TestTimeSource()

        assertEquals(TripActionResult.Applied, store.start(trip))
        assertEquals(0f, store.progressFor(trip.id))

        val simulation = ActiveTripSimulation(
            tripsStore = store,
            tripId = trip.id,
            timeSource = timeSource
        )
        val session = assertNotNull(simulation.resume())

        timeSource += 5_000.milliseconds

        assertTrue(simulation.checkpoint(session))
        assertEquals(5_000L, store.elapsedMillis)
        assertEquals(0.5f, store.progressFor(trip.id))

        /*
         * A late frame goes beyond ten seconds. The store must
         * clamp completion instead of retaining an oversized value.
         */
        timeSource += 6_000.milliseconds

        assertFalse(simulation.checkpoint(session))
        assertNull(store.activeTrip)
        assertEquals(1, store.completedTrips.size)
        assertEquals(trip.id, store.completedTrips.single().id)
        assertEquals(1f, store.progressFor(trip.id))
        assertEquals(trip.id, store.pendingReviewTripId)

        simulation.pause(session)

        assertEquals(
            TripActionResult.AlreadyCompleted,
            store.complete(trip.id)
        )
        assertEquals(1, store.completedTrips.size)
    }

    @Test
    fun pausedTimeIsExcludedAndRestorationKeepsElapsedAndEndpoints() {
        val trip = sampleTrip()
        val store = TripsStore(nowEpochMillis = { 2_000L })
        val timeSource = TestTimeSource()

        assertEquals(TripActionResult.Applied, store.start(trip))

        val simulation = ActiveTripSimulation(
            tripsStore = store,
            tripId = trip.id,
            timeSource = timeSource
        )
        val firstSession = assertNotNull(simulation.resume())

        timeSource += 2_000.milliseconds
        simulation.pause(firstSession)

        assertEquals(2_000L, store.elapsedMillis)

        timeSource += 20_000.milliseconds

        assertEquals(2_000L, store.elapsedMillis)

        val secondSession = assertNotNull(simulation.resume())
        timeSource += 3_000.milliseconds
        simulation.pause(secondSession)

        assertEquals(5_000L, store.elapsedMillis)

        val restoredStore = assertNotNull(
            TripsStore.fromSavedStateJson(
                encoded = store.toSavedStateJson(),
                nowEpochMillis = { 2_000L }
            )
        )

        assertEquals(5_000L, restoredStore.elapsedMillis)
        assertEquals(trip.id, restoredStore.activeTrip?.id)
        assertEquals(trip.start, restoredStore.activeTrip?.start)
        assertEquals(
            trip.destination,
            restoredStore.activeTrip?.destination
        )

        /*
         * Time outside a resumed session is excluded after restoration.
         */
        timeSource += 30_000.milliseconds

        val restoredSimulation = ActiveTripSimulation(
            tripsStore = restoredStore,
            tripId = trip.id,
            timeSource = timeSource
        )
        val restoredSession =
            assertNotNull(restoredSimulation.resume())

        timeSource += 5_000.milliseconds

        assertFalse(
            restoredSimulation.checkpoint(restoredSession)
        )
        assertEquals(1f, restoredStore.progressFor(trip.id))
        assertEquals(1, restoredStore.completedTrips.size)
        assertEquals(
            trip.start,
            restoredStore.completedTrips.single().start
        )
        assertEquals(
            trip.destination,
            restoredStore.completedTrips.single().destination
        )
    }

    @Test
    fun staleCancellationCannotStopNewSessionAndCancelAddsNoHistory() {
        val trip = sampleTrip()
        val store = TripsStore(nowEpochMillis = { 2_000L })
        val timeSource = TestTimeSource()

        assertEquals(TripActionResult.Applied, store.start(trip))

        val simulation = ActiveTripSimulation(
            tripsStore = store,
            tripId = trip.id,
            timeSource = timeSource
        )

        val oldSession = assertNotNull(simulation.resume())
        timeSource += 1_000.milliseconds
        simulation.pause(oldSession)

        val newSession = assertNotNull(simulation.resume())
        timeSource += 1_000.milliseconds

        /*
         * Represents a delayed finally block from the old effect.
         */
        simulation.pause(oldSession)

        assertTrue(simulation.checkpoint(newSession))
        assertEquals(2_000L, store.elapsedMillis)

        assertEquals(
            TripActionResult.Applied,
            store.cancel(trip.id)
        )

        timeSource += 5_000.milliseconds

        assertFalse(simulation.checkpoint(newSession))
        assertNull(store.findTrip(trip.id))
        assertTrue(store.completedTrips.isEmpty())
        assertNull(store.pendingReviewTripId)
    }

    private fun sampleTrip(): Trip = Trip(
        id = "simulation-test-trip",
        title = "Simulation test",
        description = "A fixed trip for timing verification.",
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