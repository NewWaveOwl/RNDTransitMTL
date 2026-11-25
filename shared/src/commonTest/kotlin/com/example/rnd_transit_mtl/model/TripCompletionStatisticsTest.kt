package com.example.rnd_transit_mtl.model

import com.example.rnd_transit_mtl.model.geo.GeoPoint
import com.example.rnd_transit_mtl.model.ids.TransportId
import com.example.rnd_transit_mtl.model.ids.TripId
import com.example.rnd_transit_mtl.model.trip.*
import kotlin.test.*

class TripCompletionStatisticsTest {
    private fun trip(): Trip {
        val points = (0..3).map { GeoPoint(45.5 + it * .001, -73.6) }
        val walk = TransportId("walk"); val bus = TransportId("bus")
        return Trip(TripId("statistics"), status = TripStatus.Completed, title = "Completed trip",
            description = "Walk, bus, walk", imageUrl = "", origin = Place("Start", points.first()),
            destination = Place("Destination", points.last()), legs = listOf(
                RouteLeg(walk, geometry = points.take(2), distanceMeters = 300.0, durationSeconds = 200, isRouted = true),
                RouteLeg(bus, geometry = points.subList(1, 3), distanceMeters = 1600.0, durationSeconds = 500, isRouted = true),
                RouteLeg(walk, geometry = points.takeLast(2), distanceMeters = 600.0, durationSeconds = 400, isRouted = true)),
            plannedMinutes = 35, selectedTransports = listOf(TripTransportSnapshot(walk, "Walk", false),
                TripTransportSnapshot(bus, "Bus", true), TripTransportSnapshot(TransportId("bike"), "Bike", false)),
            selectedRoutes = emptyList(), attractionIntensity = 50f, createdAt = 1000, completedAt = 2_000_000,
            routedDurationSeconds = 1800, waitingTimeSeconds = 700)
    }

    @Test fun totalsGroupOnlyUsedModesAndIncludeWaitsInTheTimeEstimate() {
        val saved = trip()
        val stats = TripCompletionStatistics.from(saved)
        assertEquals(mapOf("walk" to .9, "bus" to 1.6), stats.distanceByTransportKm)
        assertEquals(2.5, stats.distanceKm, .00001)
        assertEquals(1800L, stats.durationSeconds)
        assertEquals(600L, stats.walkingSeconds)
        assertEquals(1200, stats.walkingSteps)
        assertEquals(5.0, assertNotNull(stats.averageSpeedKmh), .00001)
        assertEquals(saved, trip(), "Summarizing a completed trip must not edit it")
    }

    @Test fun missingTimingDoesNotProduceAnInfiniteSpeed() {
        val saved = trip()
        val stats = TripCompletionStatistics.from(saved.copy(routedDurationSeconds = null, waitingTimeSeconds = null,
            legs = saved.legs.map { it.copy(durationSeconds = 0) }))
        assertNull(stats.averageSpeedKmh)
        assertEquals(0L, stats.durationSeconds)
    }

    @Test fun legacyWalkingIdentifiersStillContributeToWalkingStatistics() {
        val saved = trip()
        val legacy = saved.copy(legs = saved.legs.map {
            if (it.transportId.value == "walk") it.copy(transportId = TransportId("walking")) else it
        }, selectedTransports = saved.selectedTransports.map {
            if (it.id.value == "walk") it.copy(id = TransportId("walking")) else it
        })
        val stats = TripCompletionStatistics.from(legacy)
        assertEquals(1200, stats.walkingSteps)
        assertEquals(.9, stats.walkingDistanceKm, .00001)
    }
}
