package com.example.rnd_transit_mtl.ui

import androidx.compose.ui.geometry.Offset
import com.example.rnd_transit_mtl.data.assets.toSampleMapPoint
import com.example.rnd_transit_mtl.model.geo.GeoPoint
import com.example.rnd_transit_mtl.model.ids.TransportId
import com.example.rnd_transit_mtl.model.ids.TripId
import com.example.rnd_transit_mtl.model.trip.Place
import com.example.rnd_transit_mtl.model.trip.RouteLeg
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.model.trip.TripStatus
import com.example.rnd_transit_mtl.model.trip.TripTransportSnapshot
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.legProgressFractions
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.positionAlongPolyline
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.routeLine
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.toSampleMapLegs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The map draws stored leg geometry and moves the pointer along it. */
class RouteLineGeometryTest {
    // An L shape: 30 across, then 40 down, 70 long in total.
    private val corner = listOf(Offset(0f, 0f), Offset(30f, 0f), Offset(30f, 40f))

    @Test
    fun positionFollowsTurnsByLength() {
        val halfway = positionAlongPolyline(corner, 0.5f)

        // 35 along: past the corner by 5, not the straight-line midpoint (15, 20).
        assertEquals(Offset(30f, 5f), halfway.point)
        assertEquals(Offset(0f, 40f), halfway.direction)
        assertEquals(listOf(Offset(0f, 0f), Offset(30f, 0f), Offset(30f, 5f)), halfway.travelled)
    }

    @Test
    fun positionIsClampedToTheEnds() {
        assertEquals(Offset(0f, 0f), positionAlongPolyline(corner, -1f).point)
        assertEquals(Offset(30f, 40f), positionAlongPolyline(corner, 2f).point)
        assertEquals(Offset(0f, 0f), positionAlongPolyline(corner, Float.NaN).point)
    }

    @Test
    fun repeatedPointsDoNotStopThePointer() {
        val withPause = listOf(Offset(0f, 0f), Offset(10f, 0f), Offset(10f, 0f), Offset(20f, 0f))

        val end = positionAlongPolyline(withPause, 1f)

        assertEquals(Offset(20f, 0f), end.point)
        assertEquals(Offset(10f, 0f), end.direction)
    }

    @Test
    fun legsJoinWithoutRepeatingTheTransferPoint() {
        val first = listOf(Offset(0f, 0f), Offset(30f, 0f))
        val second = listOf(Offset(30f, 0f), Offset(30f, 40f))

        assertEquals(corner, routeLine(listOf(first, second)))
    }

    @Test
    fun progressFillsEarlierLegsFirst() {
        val first = listOf(Offset(0f, 0f), Offset(30f, 0f))
        val second = listOf(Offset(30f, 0f), Offset(30f, 40f))

        assertEquals(listOf(0f, 0f), legProgressFractions(listOf(first, second), 0f))
        assertEquals(listOf(1f, 0.125f), legProgressFractions(listOf(first, second), 0.5f))
        assertEquals(listOf(1f, 1f), legProgressFractions(listOf(first, second), 1f))
    }

    @Test
    fun mapLegsKeepRoutedGeometryAndFlagUnroutedLegs() {
        val origin = GeoPoint(45.515, -73.62)
        val transfer = GeoPoint(45.52, -73.60)
        val turn = GeoPoint(45.51, -73.58)
        val end = GeoPoint(45.505, -73.56)
        val bus = TransportId("bus")
        val walk = TransportId("walk")
        val trip = Trip(
            id = TripId("routed"),
            status = TripStatus.Generated,
            title = "Routed",
            description = "Bus then walk.",
            imageUrl = "",
            origin = Place("Start", origin),
            destination = Place("Destination", end),
            legs = listOf(
                RouteLeg(bus, geometry = listOf(origin, transfer), distanceMeters = 1_800.0,
                    durationSeconds = 600, isRouted = true),
                RouteLeg(walk, geometry = listOf(transfer, turn, end), distanceMeters = 3_000.0,
                    durationSeconds = 1_200, isRouted = false)
            ),
            plannedMinutes = 30,
            selectedTransports = listOf(
                TripTransportSnapshot(bus, "Bus", usesRoutes = false),
                TripTransportSnapshot(walk, "Walk", usesRoutes = false)
            ),
            selectedRoutes = emptyList(),
            attractionIntensity = 50f,
            createdAt = 0L
        )

        val legs = trip.toSampleMapLegs()

        assertEquals(listOf(2, 3), legs.map { it.points.size })
        assertTrue(legs[0].isRouted)
        assertFalse(legs[1].isRouted)
        assertEquals(turn.toSampleMapPoint("Route"), legs[1].points[1])
    }
}
