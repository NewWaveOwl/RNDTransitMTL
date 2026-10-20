package com.example.rnd_transit_mtl

import androidx.navigation3.runtime.NavKey
import com.example.rnd_transit_mtl.data.MockTripGenerator
import com.example.rnd_transit_mtl.data.TripGenerationInput
import com.example.rnd_transit_mtl.data.TripGenerationResult
import com.example.rnd_transit_mtl.model.TransportRoute
import com.example.rnd_transit_mtl.model.TransportType
import com.example.rnd_transit_mtl.model.Trip
import com.example.rnd_transit_mtl.model.TripReviewMode
import com.example.rnd_transit_mtl.state.TRIP_SIMULATION_DURATION_MILLIS
import com.example.rnd_transit_mtl.state.TripActionResult
import com.example.rnd_transit_mtl.state.TripsStore
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TripFlowIntegrationTest {
    private val types = listOf(
        TransportType("walk", "Walk", false),
        TransportType("bus", "Bus", true)
    )
    private val routes = listOf(TransportRoute("bus:401", "bus", "401"))
    private val navigationJson = Json {
        serializersModule = backStackConfig.serializersModule
    }
    private val stackSerializer = ListSerializer(PolymorphicSerializer(NavKey::class))

    @Test
    fun registeredDestinationsRestoreTripParametersAndBothReviewModes() {
        val trip = generatedTrip()
        val stack: List<NavKey> = listOf(
            MainScreenKey, ProfileScreenKey, AboutScreenKey,
            SettingsScreenKey, HistoryScreenKey, CurrentTripScreenKey(trip),
            TripReviewScreenKey(trip.id, TripReviewMode.INITIAL),
            TripReviewScreenKey(trip.id, TripReviewMode.EDIT)
        )

        val restored = navigationJson.decodeFromString(
            stackSerializer,
            navigationJson.encodeToString(stackSerializer, stack)
        )

        assertEquals(stack, restored)
        assertEquals(trip, assertIs<CurrentTripScreenKey>(restored[5]).trip)
    }

    @Test
    fun restoredActiveTripRetainsPlannerSnapshotAndElapsedProgress() {
        val trip = generatedTrip()
        val store = TripsStore(nowEpochMillis = { 12_000L })
        assertEquals(TripActionResult.Applied, store.start(trip))
        assertEquals(TripActionResult.Applied, store.updateElapsed(trip.id, 4_000L))

        val restored = assertNotNull(TripsStore.fromSavedStateJson(
            store.toSavedStateJson(), nowEpochMillis = { 12_000L }
        ))

        assertEquals(trip, restored.activeTrip)
        assertEquals(4_000L, restored.elapsedMillis)
        assertEquals(TripActionResult.ActiveTripExists(trip.id), restored.start(trip))
        assertEquals(TripActionResult.Applied, restored.updateElapsed(
            trip.id, TRIP_SIMULATION_DURATION_MILLIS
        ))
        assertNull(restored.activeTrip)
        assertEquals(trip.id, restored.pendingReviewTripId)
        assertEquals(trip.start, restored.completedTrips.single().start)
        assertEquals(trip.destination, restored.completedTrips.single().destination)
        assertEquals(trip.selectedRoutes, restored.completedTrips.single().selectedRoutes)
        assertEquals(TripActionResult.AlreadyCompleted, restored.updateElapsed(
            trip.id, TRIP_SIMULATION_DURATION_MILLIS
        ))
        assertEquals(1, restored.completedTrips.size)
    }

    @Test
    fun invalidPlannerInputDoesNotAllocateTripIds() {
        var idsAllocated = 0
        val generator = MockTripGenerator(
            random = Random(7),
            idFactory = { idsAllocated++; "trip-$idsAllocated" },
            nowEpochMillis = { 1_000L }
        )
        val invalidInputs = listOf(
            input().copy(title = " "),
            input().copy(description = " "),
            input().copy(imageUrl = "http://example.com/trip.png"),
            input().copy(imageUrl = "https:///trip.png"),
            input().copy(selectedTransportIds = emptyList(), selectedRouteIds = emptyList()),
            input().copy(plannedMinutes = 7)
        )

        invalidInputs.forEach { invalid ->
            assertIs<TripGenerationResult.InvalidInput>(
                generator.generate(invalid, types, routes, emptySet())
            )
        }
        assertEquals(0, idsAllocated)
    }

    @Test
    fun retainedNavigationParameterCannotRecreateRemovedCompletedTrip() {
        val trip = generatedTrip()
        val key = CurrentTripScreenKey(trip)
        val store = TripsStore(nowEpochMillis = { 12_000L })
        assertEquals(TripActionResult.Applied, store.start(trip))
        assertEquals(TripActionResult.Applied, store.updateElapsed(
            trip.id, TRIP_SIMULATION_DURATION_MILLIS
        ))
        assertEquals(TripActionResult.Applied, store.removeCompleted(trip.id))

        val restored = assertNotNull(TripsStore.fromSavedStateJson(store.toSavedStateJson()))
        assertEquals(trip, key.trip)
        assertNull(restored.findTrip(key.trip.id))
        assertNull(restored.pendingReviewTripId)
        assertEquals(emptyList(), restored.completedTrips)
    }

    private fun generatedTrip(): Trip = assertIs<TripGenerationResult.Success>(
        MockTripGenerator(
            random = Random(7),
            idFactory = { "generated-trip" },
            nowEpochMillis = { 1_000L }
        ).generate(input(), types, routes, emptySet())
    ).trip

    private fun input() = TripGenerationInput(
        title = "Discovery trip",
        description = "Walk and ride the bus.",
        imageUrl = "https://example.com/trip.png",
        plannedMinutes = 30,
        selectedTransportIds = listOf("walk", "bus"),
        selectedRouteIds = listOf("bus:401"),
        attractionIntensity = 65f
    )
}
