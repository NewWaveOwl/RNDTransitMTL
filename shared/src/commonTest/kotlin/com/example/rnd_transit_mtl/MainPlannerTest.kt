package com.example.rnd_transit_mtl

import com.example.rnd_transit_mtl.ViewModels.main.MainPlannerState
import com.example.rnd_transit_mtl.ViewModels.main.MainPlannerViewModel
import com.example.rnd_transit_mtl.data.MockTripGenerator
import com.example.rnd_transit_mtl.data.TripGenerationInput
import com.example.rnd_transit_mtl.data.TripGenerationResult
import com.example.rnd_transit_mtl.model.TransportType
import com.example.rnd_transit_mtl.model.Trip
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Checks draft restoration and zone requests without geographic-routing assumptions. */
class MainPlannerTest {
    private val types = listOf(
        TransportType("walk", "Walk", false),
        TransportType("bus", "Bus", true)
    )

    @Test
    fun transportModesToggleIndependentlyAndRetainHiddenZoneChoices() {
        val planner = MainPlannerViewModel()
        val available = types.map { it.id }.toSet()
        planner.toggleZone("B")
        planner.toggleTransport("bus", available)
        assertEquals(listOf("walk", "bus"), planner.uiState.value.selectedTransportIds)
        planner.toggleTransport("bus", available)
        planner.toggleTransport("missing", available)
        planner.toggleZone("missing")
        assertEquals(listOf("walk"), planner.uiState.value.selectedTransportIds)
        assertEquals(listOf("A", "B"), planner.uiState.value.selectedZoneIds)
        planner.toggleTransport("bus", available)
        assertEquals(listOf("A", "B"), planner.uiState.value.selectedZoneIds)
    }

    @Test
    fun draftRestorationRetainsSelectionsAndBoundedInputs() {
        val planner = MainPlannerViewModel()
        planner.setMinutes(37)
        planner.setIntensity(150f)
        planner.setIntensity(Float.NaN)
        planner.toggleZone("D")
        val restored = MainPlannerViewModel(Json.decodeFromString<MainPlannerState>(
            Json.encodeToString(planner.uiState.value)
        ))
        assertEquals(planner.uiState.value, restored.uiState.value)
        assertEquals(35, restored.uiState.value.minutes)
        assertEquals(100f, restored.uiState.value.intensity)
    }

    @Test
    fun transitRequiresUniqueSupportedZonesBeforeAllocatingAnId() {
        var idsAllocated = 0
        val generator = MockTripGenerator(idFactory = { idsAllocated++; "trip" })
        listOf(emptyList(), listOf("E"), listOf("A", "A")).forEach { zones ->
            assertIs<TripGenerationResult.InvalidInput>(generator.generate(
                input(zones), types, emptyList(), emptySet()
            ))
        }
        assertEquals(0, idsAllocated)
    }

    @Test
    fun transitGeneratesWithoutRouteNumbersAndSnapshotsZones() {
        val zones = mutableListOf("A", "B")
        val trip = assertIs<TripGenerationResult.Success>(MockTripGenerator(
            random = Random(7), idFactory = { "zone-trip" }, nowEpochMillis = { 1_000L }
        ).generate(input(zones), types, emptyList(), emptySet())).trip
        zones.clear()
        assertEquals(listOf("A", "B"), trip.selectedZoneIds)
        assertEquals(emptyList(), trip.selectedRoutes)
        assertEquals(trip, Json.decodeFromString<Trip>(Json.encodeToString(trip)))
    }

    @Test
    fun walkingNeedsNoZonesAndOldTripRecordsStillDecode() {
        val trip = assertIs<TripGenerationResult.Success>(MockTripGenerator(
            random = Random(7), idFactory = { "walk-trip" }, nowEpochMillis = { 1_000L }
        ).generate(input(emptyList()).copy(selectedTransportIds = listOf("walk")),
            types, emptyList(), emptySet())).trip
        // Default-valued fields are omitted, matching records saved before zone support.
        val oldRecord = Json.encodeToString(trip)
        assertEquals(emptyList(), Json.decodeFromString<Trip>(oldRecord).selectedZoneIds)
    }

    private fun input(zones: List<String>) = TripGenerationInput(
        title = "Zone trip",
        description = "A sample transit request.",
        imageUrl = "",
        plannedMinutes = 30,
        selectedTransportIds = listOf("bus"),
        selectedRouteIds = emptyList(),
        attractionIntensity = 90f,
        selectedZoneIds = zones
    )
}
