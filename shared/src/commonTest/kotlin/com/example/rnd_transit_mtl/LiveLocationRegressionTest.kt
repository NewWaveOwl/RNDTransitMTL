package com.example.rnd_transit_mtl

import com.example.rnd_transit_mtl.data.location.*
import com.example.rnd_transit_mtl.model.geo.GeoPoint
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class LiveLocationRegressionTest {
    private fun fix(lat: Double, lon: Double) = LocationUpdate.Fix(GeoPoint(lat, lon), 5.0)

    @Test fun movementHeadingTurnsAndRetainsDirectionInsideGpsNoise() {
        val heading = MovementHeading()
        heading.receive(fix(45.5, -73.5))
        heading.receive(fix(45.501, -73.5))
        assertEquals(0f, heading.degrees, 0.1f)
        heading.receive(fix(45.501, -73.499))
        assertEquals(90f, heading.degrees, 0.1f)
        heading.receive(fix(45.501001, -73.499001))
        assertEquals(90f, heading.degrees, 0.1f)
        heading.receive(fix(45.5, -73.499))
        assertEquals(180f, heading.degrees, 0.1f)
        heading.receive(fix(45.5, -73.5))
        assertEquals(270f, heading.degrees, 0.1f)
    }

    private class PendingLocation(override val fix: LocationUpdate.Fix) : PlannerLocation {
        override val containsPoint: (GeoPoint) -> Boolean = { true }
        var callback: ((LocationUpdate.Fix?) -> Unit)? = null
        override fun request(onResult: ((LocationUpdate.Fix?) -> Unit)?) { callback = onResult }
    }

    @Test fun routingWaitsForRequestedFixInsteadOfUsingCachedPosition() = runTest {
        val old = fix(45.5, -73.5)
        val fresh = fix(45.51, -73.51)
        val location = PendingLocation(old)
        val result = async { location.awaitFreshFix() }
        runCurrent()
        assertFalse(result.isCompleted)
        assertNotNull(location.callback).invoke(fresh)
        assertEquals(fresh, result.await())
    }

    @Test fun unavailableRequestNeverFallsBackToOldPosition() = runTest {
        val location = PendingLocation(fix(45.5, -73.5))
        val result = async { location.awaitFreshFix() }
        runCurrent()
        assertNotNull(location.callback).invoke(null)
        assertNull(result.await())
    }

    @Test fun planningReusesOnlyARecentlyMeasuredPosition() = runTest {
        val recent = fix(45.51, -73.51).copy(timestampEpochMillis = 99_000L)
        val location = PendingLocation(recent)
        assertEquals(recent, location.awaitPlanningLocation { 100_000L })
        assertNull(location.callback, "A recent measurement needs no second provider request")
        val result = async { location.awaitPlanningLocation { 105_000L } }
        runCurrent()
        assertFalse(result.isCompleted, "An older map position must not become the planning origin")
        val fresh = recent.copy(timestampEpochMillis = 105_001L)
        assertNotNull(location.callback).invoke(fresh)
        assertEquals(fresh, result.await())
    }

    @Test fun planningDoesNotReuseRecentFixAfterProviderBecomesUnavailable() = runTest {
        val location = object : PlannerLocation {
            override val fix = fix(45.5, -73.5).copy(timestampEpochMillis = 99_000L)
            override val currentUpdate = LocationUpdate.Unavailable
            override val containsPoint: (GeoPoint) -> Boolean = { true }
            override fun request(onResult: ((LocationUpdate.Fix?) -> Unit)?) { onResult?.invoke(null) }
        }
        assertEquals(LocationUpdate.Unavailable, location.awaitPlanningLocation { 100_000L })
    }

    @Test fun failedRefreshDistinguishesPermissionDenialFromMissingGps() = runTest {
        for (state in listOf(LocationUpdate.PermissionDenied, LocationUpdate.Unavailable)) {
            val location = object : PlannerLocation {
                override val fix: LocationUpdate.Fix? = null
                override val currentUpdate = state
                override val containsPoint: (GeoPoint) -> Boolean = { true }
                override fun request(onResult: ((LocationUpdate.Fix?) -> Unit)?) { onResult?.invoke(null) }
            }
            assertEquals(state, location.awaitPlanningLocation())
        }
    }

    @Test fun lateLocationCallbackAfterCancellationIsIgnored() = runTest {
        val location = PendingLocation(fix(45.5, -73.5))
        val result = async { location.awaitFreshFix() }
        runCurrent()
        result.cancel()
        assertNotNull(location.callback).invoke(fix(45.51, -73.51))
        assertTrue(result.isCancelled)
    }
}
