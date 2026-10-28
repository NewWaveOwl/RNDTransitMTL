package com.example.rnd_transit_mtl

import com.example.rnd_transit_mtl.ViewModels.main.MainPlannerState
import com.example.rnd_transit_mtl.ViewModels.main.MainPlannerViewModel
import com.example.rnd_transit_mtl.data.generation.MockTripGenerator
import com.example.rnd_transit_mtl.data.generation.TripGenerationInput
import com.example.rnd_transit_mtl.data.generation.TripGenerationResult
import com.example.rnd_transit_mtl.model.transport.TransportType
import com.example.rnd_transit_mtl.model.trip.Trip
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
        planner.toggleTransport("bus", available)
        planner.toggleZone("bus", "B")
        assertEquals(listOf("walk", "bus"), planner.uiState.value.selectedTransportIds)
        planner.toggleTransport("bus", available)
        planner.toggleTransport("missing", available)
        planner.toggleZone("bus", "missing")
        assertEquals(listOf("walk"), planner.uiState.value.selectedTransportIds)
        assertEquals(listOf("A", "B"), planner.uiState.value.zonesForTransport("bus"))
        planner.toggleTransport("bus", available)
        assertEquals(listOf("A", "B"), planner.uiState.value.zonesForTransport("bus"))
    }

    @Test
    fun draftRestorationRetainsSelectionsAndBoundedInputs() {
        val planner = MainPlannerViewModel()
        planner.setMinutes(37)
        planner.setIntensity(150f)
        planner.setIntensity(Float.NaN)
        planner.toggleZone("bus", "D")
        val restored = MainPlannerViewModel(Json.decodeFromString<MainPlannerState>(
            Json.encodeToString(planner.uiState.value)
        ))
        assertEquals(planner.uiState.value, restored.uiState.value)
        assertEquals(35, restored.uiState.value.minutes)
        assertEquals(100f, restored.uiState.value.intensity)
    }

    @Test
    fun walkingAndAttractionChoicesRestoreWithoutChangingOtherPlannerValues() {
        val planner = MainPlannerViewModel()
        planner.setWalkingPreference("Dash")
        planner.toggleAttraction("Culture")
        planner.toggleAttraction("Nature")
        planner.setWalkingPreference("unsupported")
        planner.toggleAttraction("unsupported")
        val restored = MainPlannerViewModel(Json.decodeFromString<MainPlannerState>(
            Json.encodeToString(planner.uiState.value)
        ))
        assertEquals("Dash", restored.uiState.value.walkingPreference)
        assertEquals(listOf("Surprise", "Culture"), restored.uiState.value.selectedAttractionIds)
        assertEquals(listOf("walk"), restored.uiState.value.selectedTransportIds)
        assertEquals(listOf("A"), restored.uiState.value.selectedZoneIds)
        assertEquals(30, restored.uiState.value.minutes)
    }

    @Test
    fun walkingPacesRestoreAndOlderStylesReceiveAValidPace() {
        assertEquals(listOf("Amble", "Walk", "Dash"), MainPlannerViewModel.walkingPreferences)
        MainPlannerViewModel.walkingPreferences.forEach { pace ->
            val planner = MainPlannerViewModel()
            planner.setWalkingPreference(pace)
            val restored = MainPlannerViewModel(Json.decodeFromString<MainPlannerState>(
                Json.encodeToString(planner.uiState.value)
            ))
            assertEquals(pace, restored.uiState.value.walkingPreference)
        }
        mapOf("Scenic" to "Amble", "Direct" to "Walk", "Normal" to "Walk", "Stroll" to "Walk", "Fast" to "Dash", "unknown" to "Amble").forEach { (old, pace) ->
            val restored = MainPlannerViewModel(MainPlannerState(walkingPreference = old))
            assertEquals(pace, restored.uiState.value.walkingPreference)
            restored.setWalkingPreference("Direct")
            assertEquals(pace, restored.uiState.value.walkingPreference)
        }
    }

    @Test
    fun bikeSpeedClampsRestoresAndRetainsValueWhileDisabled() {
        val planner = MainPlannerViewModel()
        assertEquals(18, planner.uiState.value.bikeSpeedKmh)
        planner.setBikeSpeedKmh(0)
        assertEquals(8, planner.uiState.value.bikeSpeedKmh)
        planner.setBikeSpeedKmh(100)
        assertEquals(32, planner.uiState.value.bikeSpeedKmh)
        planner.setBikeSpeedKmh(23)
        planner.toggleTransport("bike", setOf("walk", "bike"))
        planner.toggleTransport("bike", setOf("walk", "bike"))
        val restored = MainPlannerViewModel(Json.decodeFromString<MainPlannerState>(Json.encodeToString(planner.uiState.value)))
        assertEquals(23, restored.uiState.value.bikeSpeedKmh)
        assertEquals(listOf("walk"), restored.uiState.value.selectedTransportIds)
        assertEquals(32, MainPlannerViewModel(MainPlannerState(bikeSpeedKmh = 99)).uiState.value.bikeSpeedKmh)
        mapOf(8 to "Slow", 13 to "Slow", 14 to "Normal", 19 to "Normal", 20 to "Steady", 25 to "Steady", 26 to "Fast", 32 to "Fast").forEach { (speed, band) ->
            assertEquals(band, MainPlannerViewModel.bikePaceLabel(speed))
        }
    }

    @Test
    fun earlierDraftsReceiveTheNewPreferenceDefaults() {
        val restored = Json.decodeFromString<MainPlannerState>(
            """{"minutes":45,"selectedTransportIds":["bus"],"selectedZoneIds":["B"],"intensity":60.0}"""
        )
        assertEquals("Amble", restored.walkingPreference)
        assertEquals(18, restored.bikeSpeedKmh)
        assertEquals(listOf("Nature", "Surprise"), restored.selectedAttractionIds)
        assertEquals(listOf("B"), restored.selectedZoneIds)
        assertEquals(45, restored.minutes)
    }

    @Test
    fun transitZonesStayIndependentAcrossTogglingAndDraftRestoration() {
        val planner = MainPlannerViewModel()
        val available = setOf("walk", "bus", "metro", "train", "rem")
        planner.toggleTransport("bus", available)
        planner.toggleTransport("metro", available)
        planner.toggleZone("bus", "B")
        planner.toggleZone("metro", "A")
        planner.toggleZone("metro", "C")
        planner.toggleZone("train", "D")
        planner.toggleZone("rem", "B")
        planner.toggleTransport("metro", available)
        planner.toggleTransport("metro", available)
        planner.toggleZone("walk", "D")
        val restored = MainPlannerViewModel(Json.decodeFromString<MainPlannerState>(
            Json.encodeToString(planner.uiState.value)
        )).uiState.value
        assertEquals(listOf("A", "B"), restored.zonesForTransport("bus"))
        assertEquals(listOf("C"), restored.zonesForTransport("metro"))
        assertEquals(listOf("D"), restored.zonesForTransport("train"))
        assertEquals(listOf("B"), restored.zonesForTransport("rem"))
        assertEquals(false, restored.selectedZonesByTransport.containsKey("walk"))
    }

    @Test
    fun choosingTheFirstZoneIncludesTheModeAndClearingItsLastZoneRemovesOnlyThatMode() {
        val planner = MainPlannerViewModel()
        assertEquals(emptyList(), planner.uiState.value.zonesForTransport("bus"))
        planner.toggleZone("bus", "B")
        planner.toggleZone("metro", "C")
        assertEquals(listOf("walk", "bus", "metro"), planner.uiState.value.selectedTransportIds)
        planner.toggleZone("bus", "B")
        assertEquals(listOf("walk", "metro"), planner.uiState.value.selectedTransportIds)
        assertEquals(emptyList(), planner.uiState.value.zonesForTransport("bus"))
        assertEquals(listOf("C"), planner.uiState.value.zonesForTransport("metro"))
        planner.toggleZone("bus", "D")
        assertEquals(listOf("D"), planner.uiState.value.zonesForTransport("bus"))
    }

    @Test
    fun oldSharedZonesSeedEachModeUntilItIsEditedIndependently() {
        val oldDraft = Json.decodeFromString<MainPlannerState>(
            """{"selectedTransportIds":["bus","metro"],"selectedZoneIds":["B"]}"""
        )
        val planner = MainPlannerViewModel(oldDraft)
        planner.toggleZone("bus", "C")
        assertEquals(listOf("B", "C"), planner.uiState.value.zonesForTransport("bus"))
        assertEquals(listOf("B"), planner.uiState.value.zonesForTransport("metro"))
        planner.toggleZone("bus", "B")
        planner.toggleZone("bus", "C")
        assertEquals(emptyList(), planner.uiState.value.zonesForTransport("bus"))
        assertEquals(listOf("B"), planner.uiState.value.zonesForTransport("metro"))
    }

    @Test
    fun generatedTripPreservesIndependentZonesAndRejectsIncompleteOrInvalidMaps() {
        val types = types + TransportType("metro", "Metro", true)
        val busZones = mutableListOf("A", "B")
        val metroZones = mutableListOf("C")
        val zones = mutableMapOf("bus" to busZones, "metro" to metroZones)
        val request = input(listOf("A", "B", "C")).copy(
            selectedTransportIds = listOf("bus", "metro"), selectedZonesByTransport = zones
        )
        var ids = 0
        val generator = MockTripGenerator(idFactory = { "trip-${++ids}" }, nowEpochMillis = { 1_000L })
        val trip = assertIs<TripGenerationResult.Success>(
            generator.generate(request, types, emptyList(), emptySet())
        ).trip
        listOf(
            mapOf("bus" to listOf("A")),
            mapOf("bus" to emptyList(), "metro" to listOf("C")),
            mapOf("bus" to listOf("A", "A"), "metro" to listOf("C")),
            mapOf("bus" to listOf("E"), "metro" to listOf("C")),
            mapOf("bus" to listOf("A"), "walk" to listOf("C"))
        ).forEach { invalid ->
            assertIs<TripGenerationResult.InvalidInput>(generator.generate(
                request.copy(selectedZonesByTransport = invalid), types, emptyList(), emptySet()
            ))
        }
        assertEquals(1, ids)
        busZones.clear()
        metroZones.clear()
        zones.clear()
        assertEquals(mapOf("bus" to listOf("A", "B"), "metro" to listOf("C")), trip.selectedZonesByTransport)
        assertEquals(trip, Json.decodeFromString<Trip>(Json.encodeToString(trip)))
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
