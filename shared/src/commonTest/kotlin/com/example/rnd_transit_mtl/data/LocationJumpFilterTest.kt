package com.example.rnd_transit_mtl.data

import com.example.rnd_transit_mtl.data.location.LocationJumpFilter
import com.example.rnd_transit_mtl.data.location.LocationUpdate
import com.example.rnd_transit_mtl.model.geo.GeoPoint
import kotlin.test.*

class LocationJumpFilterTest {
    @Test
    fun stoppingAnInactiveScreenCannotResetTheActiveTripsSpeedPolicy() {
        val filter = LocationJumpFilter()
        val oldTrip = Any()
        val activeTrip = Any()
        filter.useMaximumSpeed(oldTrip, 15.0)
        filter.useMaximumSpeed(activeTrip, 3.5)
        filter.releaseMaximumSpeed(oldTrip)
        assertEquals(3.5, filter.maximumSpeedMetersPerSecond)
        filter.releaseMaximumSpeed(activeTrip)
        assertEquals(45.0, filter.maximumSpeedMetersPerSecond)
    }

    private val origin = GeoPoint(45.5, -73.6)
    private fun fix(northMeters: Double, time: Long, accuracy: Double = 5.0) =
        LocationUpdate.Fix(GeoPoint(origin.latitude + northMeters / 111_195.0, origin.longitude), accuracy, time)
    private fun walkingFilter() = LocationJumpFilter().apply { maximumSpeedMetersPerSecond = 3.5 }

    @Test
    fun providerOutliersCannotMoveTheAnchorAndAValidReadingImmediatelyRecovers() {
        val filter = walkingFilter()
        val start = fix(0.0, 50_000)
        assertTrue(assertNotNull(filter.evaluate(start, 50_000)).accepted)
        for ((index, meters) in listOf(900.0, -800.0, 1_200.0, -600.0).withIndex()) {
            val time = 52_000L + index * 2_000
            assertFalse(assertNotNull(filter.evaluate(fix(meters, time, 200.0), time)).accepted)
            assertEquals(start, filter.acceptedFix)
        }
        val recovered = fix(14.0, 60_000)
        assertTrue(assertNotNull(filter.evaluate(recovered, 60_000)).accepted)
        assertEquals(recovered, filter.acceptedFix)
    }

    @Test
    fun relocationNeedsThreeConsistentMeasurementsAndDuplicateObserversDoNotCountTwice() {
        val filter = walkingFilter()
        filter.evaluate(fix(0.0, 50_000), 50_000)
        val first = fix(1_000.0, 52_000)
        repeat(4) { assertFalse(assertNotNull(filter.evaluate(first, 52_000)).accepted) }
        assertFalse(assertNotNull(filter.evaluate(fix(1_003.0, 54_000), 54_000)).accepted)
        val result = assertNotNull(filter.evaluate(fix(1_006.0, 56_000), 56_000))
        assertTrue(result.accepted)
        assertTrue(result.relocated)
        assertEquals(result, filter.evaluate(result.reading, 56_001))
        assertTrue(assertNotNull(filter.evaluate(fix(1_009.0, 58_000), 58_000)).accepted)
    }

    @Test
    fun staleFutureAndUnorderedSamplesCannotConfirmRelocation() {
        val filter = walkingFilter()
        filter.evaluate(fix(0.0, 50_000), 50_000)
        filter.evaluate(fix(1_000.0, 52_000), 52_000)
        assertNull(filter.evaluate(fix(1_001.0, 40_000), 60_000))
        assertNull(filter.evaluate(fix(1_001.0, 60_001), 60_000))
        assertNull(filter.evaluate(fix(1_001.0, 51_000), 60_000))
        assertFalse(assertNotNull(filter.evaluate(fix(1_002.0, 54_000), 54_000)).accepted)
        assertTrue(assertNotNull(filter.evaluate(fix(1_004.0, 56_000), 56_000)).relocated)
    }

    @Test
    fun busSpeedIsAllowedButTheSameMovementIsRejectedWhenWalking() {
        val bus = LocationJumpFilter()
        val walk = walkingFilter()
        for (filter in listOf(bus, walk)) filter.evaluate(fix(0.0, 50_000), 50_000)
        val moving = fix(80.0, 52_000).copy(speedMetersPerSecond = 35.0)
        assertTrue(assertNotNull(bus.evaluate(moving, 52_000)).accepted)
        assertFalse(assertNotNull(walk.evaluate(moving, 52_000)).accepted)
    }

    @Test
    fun poorAccuracyAndLongOutagesCannotMakeAnyCoordinateAutomaticallyPlausible() {
        val filter = walkingFilter()
        filter.evaluate(fix(0.0, 50_000), 50_000)
        repeat(4) {
            val time = 52_000L + it * 2_000
            assertFalse(assertNotNull(filter.evaluate(fix(2_000.0, time, 5_000.0), time)).accepted)
        }
        assertFalse(assertNotNull(filter.evaluate(fix(10_000.0, 3_650_000), 3_650_000)).accepted)
    }

    @Test
    fun coarseReadingsCannotIncrementallyDragAPreciseAnchor() {
        val filter = walkingFilter()
        val start = fix(0.0, 50_000)
        filter.evaluate(start, 50_000)
        assertFalse(assertNotNull(filter.evaluate(fix(20.0, 52_000, 80.0), 52_000)).accepted)
        assertEquals(start, filter.acceptedFix)
        assertEquals(start, assertNotNull(filter.evaluate(fix(20.0, 52_000, 80.0), 52_000)).trustedFix,
            "Map consumers must receive the retained anchor rather than the weaker raw point")
        assertFalse(assertNotNull(filter.evaluate(fix(100.0, 54_000, 80.0), 54_000)).accepted)
    }

    @Test fun stationaryZeroSpeedAndAlternatingProvidersCannotMoveTheMarker() {
        val filter = LocationJumpFilter()
        val start = fix(0.0, 50_000).copy(speedMetersPerSecond = 0.0)
        filter.evaluate(start, 50_000)
        repeat(40) { index ->
            val time = 52_000L + index * 2_000
            val reading = fix(if (index % 2 == 0) 80.0 else -6.0, time).copy(speedMetersPerSecond = 0.0)
            assertFalse(assertNotNull(filter.evaluate(reading, time)).relocated)
            assertEquals(start.point, filter.acceptedFix?.point)
        }
    }

    @Test fun weakClustersCannotRelocateAReliablePoint() {
        val filter = walkingFilter()
        val start = fix(0.0, 50_000)
        filter.evaluate(start, 50_000)
        repeat(20) { index ->
            val time = 52_000L + index * 2_000
            assertFalse(assertNotNull(filter.evaluate(fix(1_000.0, time, 150.0), time)).accepted)
            assertEquals(start, filter.acceptedFix)
        }
    }

    @Test fun preciseRecoverySurvivesInterleavedCoarseProviderReadings() {
        val filter = walkingFilter()
        filter.evaluate(fix(0.0, 50_000, 200.0), 50_000)
        repeat(3) { index ->
            val time = 52_000L + index * 4_000
            val precise = assertNotNull(filter.evaluate(fix(1_000.0, time), time))
            assertEquals(index == 2, precise.accepted)
            if (index < 2) filter.evaluate(fix(0.0, time + 2_000, 200.0), time + 2_000)
        }
        assertEquals(fix(1_000.0, 60_000), filter.acceptedFix)
    }

    @Test fun noiseIsHeldButSlowWalkingAndMeasuredBusMovementAreNotFrozen() {
        val filter = LocationJumpFilter()
        filter.evaluate(fix(0.0, 50_000), 50_000)
        assertTrue(assertNotNull(filter.evaluate(fix(2.0, 52_000), 52_000)).stationary)
        assertEquals(fix(0.0, 50_000).point, filter.acceptedFix?.point)
        assertTrue(assertNotNull(filter.evaluate(fix(12.0, 54_000), 54_000)).accepted)
        val moving = fix(92.0, 56_000).copy(speedMetersPerSecond = 35.0)
        assertTrue(assertNotNull(filter.evaluate(moving, 56_000)).accepted)
        assertEquals(moving, filter.acceptedFix)
    }

    @Test fun plannerWithoutMovementEvidenceRejectsAnIsolatedEightyMetreJump() {
        val filter = LocationJumpFilter()
        filter.evaluate(fix(0.0, 50_000), 50_000)
        assertFalse(assertNotNull(filter.evaluate(fix(80.0, 52_000), 52_000)).accepted)
        assertTrue(assertNotNull(filter.evaluate(fix(0.0, 54_000), 54_000)).stationary)
    }

    @Test fun unspeededJitterAndLessPreciseNetworkClustersRetainTheFreshGpsPoint() {
        val filter = LocationJumpFilter()
        val start = fix(0.0, 50_000)
        filter.evaluate(start, 50_000)
        repeat(8) { index ->
            val time = 52_000L + index * 2_000
            filter.evaluate(fix(if (index % 2 == 0) 8.0 else -8.0, time), time)
            assertEquals(start.point, filter.acceptedFix?.point)
        }
        repeat(5) { index ->
            val time = 68_000L + index * 2_000
            assertFalse(assertNotNull(filter.evaluate(fix(80.0, time, 20.0), time)).accepted)
            assertEquals(start.point, filter.acceptedFix?.point)
        }
    }

    @Test fun betterAccuracyCanCorrectACoarseStationaryFixWithoutWaitingForMovement() {
        val filter = LocationJumpFilter()
        filter.evaluate(fix(0.0, 50_000, 200.0).copy(speedMetersPerSecond = 0.0), 50_000)
        val precise = fix(100.0, 52_000).copy(speedMetersPerSecond = 0.0)
        assertTrue(assertNotNull(filter.evaluate(precise, 52_000)).relocated)
        assertEquals(precise, filter.acceptedFix)
    }
    @Test fun stationaryFixesAfterAnOutageRecoverPlanningWithoutAcceptingASingleJump() {
        val filter = walkingFilter()
        val queue = com.example.rnd_transit_mtl.data.location.LocationRequestQueue()
        val old = fix(0.0, 50_000).copy(speedMetersPerSecond = 0.0)
        filter.evaluate(old, 50_000)
        var resolved: LocationUpdate.Fix? = null
        queue.add(100_000) { resolved = it }
        repeat(3) { index ->
            val time = 100_000L + index * 2_000
            val reading = fix(1_000.0 + index, time).copy(speedMetersPerSecond = 0.0)
            val result = assertNotNull(filter.evaluate(reading, time))
            assertEquals(index == 2, result.accepted)
            if (result.accepted) queue.receive(assertNotNull(result.trustedFix), time)
            if (index < 2) assertNull(resolved)
        }
        assertEquals(fix(1_002.0, 104_000).point, assertNotNull(resolved).point)
        assertEquals(0, queue.size)
    }

}
