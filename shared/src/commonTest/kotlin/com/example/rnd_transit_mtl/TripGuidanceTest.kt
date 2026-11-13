package com.example.rnd_transit_mtl

import com.example.rnd_transit_mtl.model.geo.GeoPoint
import com.example.rnd_transit_mtl.model.ids.RouteId
import com.example.rnd_transit_mtl.model.ids.TransportId
import com.example.rnd_transit_mtl.model.ids.TripId
import com.example.rnd_transit_mtl.model.trip.*
import com.example.rnd_transit_mtl.data.remote.dto.toDto
import com.example.rnd_transit_mtl.data.remote.dto.toDomainOrNull
import com.example.rnd_transit_mtl.ui.theme.tripLegColor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

/** Three equally long sections let guidance transitions be checked against map progress. */
internal fun guidanceTestTrip(): Trip {
    val points = (0..3).map { GeoPoint(45.5 + it * 0.001, -73.6) }
    val walk = TransportId("walk")
    val bus = TransportId("bus")
    val route = RouteId("STM:24")
    return Trip(
        id = TripId("guidance"), status = TripStatus.Generated, title = "City trip", description = "Walk, bus, walk",
        imageUrl = "", origin = Place("Start", points[0]), destination = Place("Park", points[3]),
        legs = listOf(
            RouteLeg(walk, geometry = points.take(2), distanceMeters = 100.0, durationSeconds = 90, isRouted = true,
                navigation = LegNavigation(steps = listOf(
                    NavigationStep("DEPART", "Rue Sherbrooke", 50.0),
                    NavigationStep("LEFT", "Rue Saint-Denis", 50.0)
                ))),
            RouteLeg(bus, route, points.subList(1, 3), 100.0, 60, true,
                navigation = LegNavigation("24", "East", "Sherbrooke / Saint-Denis", "Park stop")),
            RouteLeg(walk, geometry = points.takeLast(2), distanceMeters = 100.0, durationSeconds = 90, isRouted = true)
        ),
        plannedMinutes = 10,
        selectedTransports = listOf(TripTransportSnapshot(walk, "Walk", false), TripTransportSnapshot(bus, "Bus", true)),
        selectedRoutes = listOf(TripRouteSnapshot(route, bus, "Sherbrooke")), attractionIntensity = 50f, createdAt = 0
    )
}

class TripGuidanceTest {
    @Test
    fun upcomingNamedTurnAdvancesToTheNumberedBusAndThenTheFinalWalk() {
        val trip = guidanceTestTrip()
        val start = trip.guidanceAt(0f)
        assertEquals(0, start.legIndex)
        assertContains(start.currentAction, "Rue Sherbrooke")
        assertContains(start.nextAction, "50 m, turn left onto Rue Saint-Denis")
        assertTrue(start.nextIsLeftTurn)

        val afterTurn = trip.guidanceAt(0.2f)
        assertContains(afterTurn.currentAction, "Turn left onto Rue Saint-Denis")
        assertContains(afterTurn.nextAction, "Wait for Bus 24 at Sherbrooke / Saint-Denis, then board towards East")

        val riding = trip.guidanceAt(0.5f)
        assertEquals(1, riding.legIndex)
        assertEquals("Section 2 of 3: Bus 24", riding.section)
        assertEquals("Ride Bus 24 towards East", riding.currentAction)
        assertContains(riding.nextAction, "Get off at Park stop. Continue with Walk")

        val finalWalk = trip.guidanceAt(0.9f)
        assertEquals(2, finalWalk.legIndex)
        assertEquals("Arrive at Park", finalWalk.nextAction)
        assertEquals("Destination reached", trip.guidanceAt(1f).currentAction)
    }

    @Test
    fun invalidProgressAndLegacyLegsNeverInventTurnsOrUseTheFirstSelectedRoute() {
        val trip = guidanceTestTrip()
        assertEquals(trip.guidanceAt(0f), trip.guidanceAt(Float.NaN))
        assertEquals(trip.guidanceAt(0f), trip.guidanceAt(-1f))
        assertEquals(trip.guidanceAt(1f), trip.guidanceAt(2f))
        val legacy = trip.copy(legs = trip.legs.map { it.copy(navigation = null) })
        assertEquals("Continue with Walk", legacy.guidanceAt(0f).currentAction)
        assertEquals("Section 2 of 3: Bus Sherbrooke", legacy.guidanceAt(0.5f).section)
        assertFalse(legacy.guidanceAt(0f).nextAction.contains("Turn"))
    }

    @Test
    fun storedInstructionsSurviveBothSerializationPathsAndLegColoursAreDistinct() {
        val trip = guidanceTestTrip()
        assertEquals(trip, Json.decodeFromString<Trip>(Json.encodeToString(trip)))
        assertEquals(trip, trip.toDto().toDomainOrNull())
        assertEquals(12, (0..11).map(::tripLegColor).distinct().size)
    }

    @Test
    fun zeroLengthLegsAreSkippedAndZeroLengthStepsDoNotBlockGuidance() {
        val trip = guidanceTestTrip()
        val connector = trip.legs.first().copy(geometry = listOf(trip.origin.point, trip.origin.point), distanceMeters = 0.0)
        assertEquals(1, trip.copy(legs = listOf(connector) + trip.legs).guidanceAt(0f).legIndex)
        val first = trip.legs.first()
        val navigation = assertNotNull(first.navigation)
        val zeroStep = first.copy(navigation = navigation.copy(steps =
            listOf(NavigationStep("CONTINUE", "Unnamed", 0.0)) + navigation.steps))
        assertContains(trip.copy(legs = listOf(zeroStep) + trip.legs.drop(1)).guidanceAt(0f).currentAction, "Rue Sherbrooke")
    }
}
