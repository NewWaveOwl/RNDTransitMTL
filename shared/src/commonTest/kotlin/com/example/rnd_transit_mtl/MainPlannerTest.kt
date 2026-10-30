package com.example.rnd_transit_mtl

import com.example.rnd_transit_mtl.model.ids.ZoneId
import com.example.rnd_transit_mtl.model.ids.TransportId
import com.example.rnd_transit_mtl.ViewModels.main.MainPlannerState
import com.example.rnd_transit_mtl.ViewModels.main.MainPlannerViewModel
import com.example.rnd_transit_mtl.data.generation.MockTripGenerator
import com.example.rnd_transit_mtl.data.generation.TripGenerationInput
import com.example.rnd_transit_mtl.data.generation.TripGenerationResult
import com.example.rnd_transit_mtl.model.transport.TransportType
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.model.trip.TripPoint
import com.example.rnd_transit_mtl.data.generation.TripGenerationBounds
import kotlin.test.assertTrue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Checks draft restoration and zone requests without geographic-routing assumptions. */
class MainPlannerTest {
    @Test
    fun restoredZeroMinuteDraftUsesFiveMinuteMinimum() {
        val planner = MainPlannerViewModel(MainPlannerState(minutes = 0))
        assertEquals(5, planner.uiState.value.minutes)
        planner.setMinutes(Int.MIN_VALUE)
        assertEquals(5, planner.uiState.value.minutes)
    }

    private val types = listOf(
        TransportType(TransportId("walk"), "Walk", false),
        TransportType(TransportId("bus"), "Bus", true)
    )

    @Test
    fun transportModesToggleIndependentlyAndRetainHiddenZoneChoices() {
        val planner = MainPlannerViewModel()
        val available = types.map { it.id }.toSet()
        planner.toggleTransport(TransportId("bus"), available)
        planner.toggleZone(TransportId("bus"), ZoneId("B"))
        assertEquals(listOf(TransportId("walk"), TransportId("bus")), planner.uiState.value.selectedTransportIds)
        planner.toggleTransport(TransportId("bus"), available)
        planner.toggleTransport(TransportId("missing"), available)
        planner.toggleZone(TransportId("bus"), ZoneId("missing"))
        assertEquals(listOf(TransportId("walk")), planner.uiState.value.selectedTransportIds)
        assertEquals(listOf(ZoneId("A"), ZoneId("B")), planner.uiState.value.zonesForTransport(TransportId("bus")))
        planner.toggleTransport(TransportId("bus"), available)
        assertEquals(listOf(ZoneId("A"), ZoneId("B")), planner.uiState.value.zonesForTransport(TransportId("bus")))
    }

    @Test
    fun draftRestorationRetainsSelectionsAndBoundedInputs() {
        val planner = MainPlannerViewModel()
        planner.setMinutes(37)
        planner.setIntensity(150f)
        planner.setIntensity(Float.NaN)
        planner.toggleZone(TransportId("bus"), ZoneId("D"))
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
        assertEquals(listOf(TransportId("walk")), restored.uiState.value.selectedTransportIds)
        assertEquals(listOf(ZoneId("A")), restored.uiState.value.selectedZoneIds)
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
        planner.toggleTransport(TransportId("bike"), setOf(TransportId("walk"), TransportId("bike")))
        planner.toggleTransport(TransportId("bike"), setOf(TransportId("walk"), TransportId("bike")))
        val restored = MainPlannerViewModel(Json.decodeFromString<MainPlannerState>(Json.encodeToString(planner.uiState.value)))
        assertEquals(23, restored.uiState.value.bikeSpeedKmh)
        assertEquals(listOf(TransportId("walk")), restored.uiState.value.selectedTransportIds)
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
        assertEquals(listOf(ZoneId("B")), restored.selectedZoneIds)
        assertEquals(45, restored.minutes)
    }

    @Test
    fun transitZonesStayIndependentAcrossTogglingAndDraftRestoration() {
        val planner = MainPlannerViewModel()
        val available = setOf(TransportId("walk"), TransportId("bus"), TransportId("metro"), TransportId("train"), TransportId("rem"))
        planner.toggleTransport(TransportId("bus"), available)
        planner.toggleTransport(TransportId("metro"), available)
        planner.toggleZone(TransportId("bus"), ZoneId("B"))
        planner.toggleZone(TransportId("metro"), ZoneId("A"))
        planner.toggleZone(TransportId("metro"), ZoneId("C"))
        planner.toggleZone(TransportId("train"), ZoneId("D"))
        planner.toggleZone(TransportId("rem"), ZoneId("B"))
        planner.toggleTransport(TransportId("metro"), available)
        planner.toggleTransport(TransportId("metro"), available)
        planner.toggleZone(TransportId("walk"), ZoneId("D"))
        val restored = MainPlannerViewModel(Json.decodeFromString<MainPlannerState>(
            Json.encodeToString(planner.uiState.value)
        )).uiState.value
        assertEquals(listOf(ZoneId("A"), ZoneId("B")), restored.zonesForTransport(TransportId("bus")))
        assertEquals(listOf(ZoneId("C")), restored.zonesForTransport(TransportId("metro")))
        assertEquals(listOf(ZoneId("D")), restored.zonesForTransport(TransportId("train")))
        assertEquals(listOf(ZoneId("B")), restored.zonesForTransport(TransportId("rem")))
        assertEquals(false, restored.selectedZonesByTransport.containsKey(TransportId("walk")))
    }

    @Test
    fun choosingTheFirstZoneIncludesTheModeAndClearingItsLastZoneRemovesOnlyThatMode() {
        val planner = MainPlannerViewModel()
        assertEquals(emptyList(), planner.uiState.value.zonesForTransport(TransportId("bus")))
        planner.toggleZone(TransportId("bus"), ZoneId("B"))
        planner.toggleZone(TransportId("metro"), ZoneId("C"))
        assertEquals(listOf(TransportId("walk"), TransportId("bus"), TransportId("metro")), planner.uiState.value.selectedTransportIds)
        planner.toggleZone(TransportId("bus"), ZoneId("B"))
        assertEquals(listOf(TransportId("walk"), TransportId("metro")), planner.uiState.value.selectedTransportIds)
        assertEquals(emptyList(), planner.uiState.value.zonesForTransport(TransportId("bus")))
        assertEquals(listOf(ZoneId("C")), planner.uiState.value.zonesForTransport(TransportId("metro")))
        planner.toggleZone(TransportId("bus"), ZoneId("D"))
        assertEquals(listOf(ZoneId("D")), planner.uiState.value.zonesForTransport(TransportId("bus")))
    }

    @Test
    fun oldSharedZonesSeedEachModeUntilItIsEditedIndependently() {
        val oldDraft = Json.decodeFromString<MainPlannerState>(
            """{"selectedTransportIds":["bus","metro"],"selectedZoneIds":["B"]}"""
        )
        val planner = MainPlannerViewModel(oldDraft)
        planner.toggleZone(TransportId("bus"), ZoneId("C"))
        assertEquals(listOf(ZoneId("B"), ZoneId("C")), planner.uiState.value.zonesForTransport(TransportId("bus")))
        assertEquals(listOf(ZoneId("B")), planner.uiState.value.zonesForTransport(TransportId("metro")))
        planner.toggleZone(TransportId("bus"), ZoneId("B"))
        planner.toggleZone(TransportId("bus"), ZoneId("C"))
        assertEquals(emptyList(), planner.uiState.value.zonesForTransport(TransportId("bus")))
        assertEquals(listOf(ZoneId("B")), planner.uiState.value.zonesForTransport(TransportId("metro")))
    }

    @Test
    fun generatedTripPreservesIndependentZonesAndRejectsIncompleteOrInvalidMaps() {
        val types = types + TransportType(TransportId("metro"), "Metro", true)
        val busZones = mutableListOf(ZoneId("A"), ZoneId("B"))
        val metroZones = mutableListOf(ZoneId("C"))
        val zones = mutableMapOf(TransportId("bus") to busZones, TransportId("metro") to metroZones)
        val request = input(listOf(ZoneId("A"), ZoneId("B"), ZoneId("C"))).copy(
            selectedTransportIds = listOf(TransportId("bus"), TransportId("metro")), selectedZonesByTransport = zones
        )
        var ids = 0
        val generator = MockTripGenerator(idFactory = { "trip-${++ids}" }, nowEpochMillis = { 1_000L })
        val trip = assertIs<TripGenerationResult.Success>(
            generator.generate(request, types, emptyList(), emptySet())
        ).trip
        listOf(
            mapOf(TransportId("bus") to listOf(ZoneId("A"))),
            mapOf(TransportId("bus") to emptyList(), TransportId("metro") to listOf(ZoneId("C"))),
            mapOf(TransportId("bus") to listOf(ZoneId("A"), ZoneId("A")), TransportId("metro") to listOf(ZoneId("C"))),
            mapOf(TransportId("bus") to listOf(ZoneId("E")), TransportId("metro") to listOf(ZoneId("C"))),
            mapOf(TransportId("bus") to listOf(ZoneId("A")), TransportId("walk") to listOf(ZoneId("C")))
        ).forEach { invalid ->
            assertIs<TripGenerationResult.InvalidInput>(generator.generate(
                request.copy(selectedZonesByTransport = invalid), types, emptyList(), emptySet()
            ))
        }
        assertEquals(1, ids)
        busZones.clear()
        metroZones.clear()
        zones.clear()
        assertEquals(mapOf(TransportId("bus") to listOf(ZoneId("A"), ZoneId("B")), TransportId("metro") to listOf(ZoneId("C"))), trip.selectedZonesByTransport)
        assertEquals(trip, Json.decodeFromString<Trip>(Json.encodeToString(trip)))
    }

    @Test
    fun transitRequiresUniqueSupportedZonesBeforeAllocatingAnId() {
        var idsAllocated = 0
        val generator = MockTripGenerator(idFactory = { idsAllocated++; "trip" })
        listOf(emptyList(), listOf(ZoneId("E")), listOf(ZoneId("A"), ZoneId("A"))).forEach { zones ->
            assertIs<TripGenerationResult.InvalidInput>(generator.generate(
                input(zones), types, emptyList(), emptySet()
            ))
        }
        assertEquals(0, idsAllocated)
    }

    @Test
    fun transitGeneratesWithoutRouteNumbersAndSnapshotsZones() {
        val zones = mutableListOf(ZoneId("A"), ZoneId("B"))
        val trip = assertIs<TripGenerationResult.Success>(MockTripGenerator(
            random = Random(7), idFactory = { "zone-trip" }, nowEpochMillis = { 1_000L }
        ).generate(input(zones), types, emptyList(), emptySet())).trip
        zones.clear()
        assertEquals(listOf(ZoneId("A"), ZoneId("B")), trip.selectedZoneIds)
        assertEquals(emptyList(), trip.selectedRoutes)
        assertEquals(trip, Json.decodeFromString<Trip>(Json.encodeToString(trip)))
    }

    @Test
    fun walkingNeedsNoZonesAndOldTripRecordsStillDecode() {
        val trip = assertIs<TripGenerationResult.Success>(MockTripGenerator(
            random = Random(7), idFactory = { "walk-trip" }, nowEpochMillis = { 1_000L }
        ).generate(input(emptyList()).copy(selectedTransportIds = listOf(TransportId("walk"))),
            types, emptyList(), emptySet())).trip
        // Default-valued fields are omitted, matching records saved before zone support.
        val oldRecord = Json.encodeToString(trip)
        assertEquals(emptyList(), Json.decodeFromString<Trip>(oldRecord).selectedZoneIds)
    }

    @Test
    fun originRestoresAndEditingItKeepsAllOtherChoices() {
        val legacy = Json.decodeFromString<MainPlannerState>("""{"minutes":45}""")
        assertEquals(TripPoint("Current location", 0.5f, 0.35f), legacy.origin)
        val initial = MainPlannerState(minutes = 45, walkingPreference = "Dash",
            bikeSpeedKmh = 26, selectedZonesByTransport = mapOf(TransportId("bus") to listOf(ZoneId("C"))),
            origin = null, locationAccessOff = true)
        val planner = MainPlannerViewModel(initial)
        val point = TripPoint("  Selected point  ", 0.23f, 0.67f)
        planner.setOrigin(point)
        val expected = initial.copy(origin = point.copy(label = "Selected point"), originIsManual = true)
        assertEquals(expected, planner.uiState.value)
        assertEquals(expected, Json.decodeFromString<MainPlannerState>(Json.encodeToString(expected)))
        assertEquals(initial, Json.decodeFromString<MainPlannerState>(Json.encodeToString(initial)))
    }

    @Test
    fun selectedOriginIsNotTransformedAgainAndDestinationStaysInBounds() {
        val bounds = TripGenerationBounds(0.3f, 0.2f, 0.7f, 0.6f)
        val generator = MockTripGenerator(random = Random(41), idFactory = { "origin-trip" },
            nowEpochMillis = { 1_000L })
        listOf(TripPoint("Selected point", 0.43f, 0.31f),
            TripPoint("Outside visible crop", 0f, 1f)).forEach { origin ->
            repeat(20) {
                val trip = assertIs<TripGenerationResult.Success>(generator.generate(
                    input(emptyList()).copy(selectedTransportIds = listOf(TransportId("walk")), origin = origin),
                    types, emptyList(), emptySet(), bounds)).trip
                assertEquals(origin, trip.start)
                assertTrue(trip.destination.x in bounds.left..bounds.right)
                assertTrue(trip.destination.y in bounds.top..bounds.bottom)
                assertTrue(origin.normalizedDistanceTo(trip.destination) >= 0.099999)
                assertTrue(trip.distanceKm > 0)
            }
        }
    }

    @Test
    fun repeatedRandomPointFallsBackToAFarCorner() {
        val constantRandom = object : Random() { override fun nextBits(bitCount: Int): Int = 0 }
        val origin = TripPoint("Selected point", MockTripGenerator.SAFE_MIN, MockTripGenerator.SAFE_MIN)
        val trip = assertIs<TripGenerationResult.Success>(MockTripGenerator(
            random = constantRandom, idFactory = { "fallback" }, nowEpochMillis = { 1_000L }
        ).generate(input(emptyList()).copy(selectedTransportIds = listOf(TransportId("walk")), origin = origin),
            types, emptyList(), emptySet())).trip
        assertEquals(origin, trip.start)
        assertEquals(TripPoint("Destination", MockTripGenerator.SAFE_MAX, MockTripGenerator.SAFE_MAX),
            trip.destination)
    }

    private fun input(zones: List<ZoneId>) = TripGenerationInput(
        title = "Zone trip",
        description = "A sample transit request.",
        imageUrl = "",
        plannedMinutes = 30,
        selectedTransportIds = listOf(TransportId("bus")),
        selectedRouteIds = emptyList(),
        attractionIntensity = 90f,
        selectedZoneIds = zones
    )
}
