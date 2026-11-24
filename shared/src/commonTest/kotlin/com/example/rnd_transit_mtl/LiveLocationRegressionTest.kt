package com.example.rnd_transit_mtl

import com.example.rnd_transit_mtl.data.location.*
import com.example.rnd_transit_mtl.model.geo.GeoPoint
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
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
        val result = async { location.awaitPlanningLocation { 115_000L } }
        runCurrent()
        assertFalse(result.isCompleted, "An older map position must not become the planning origin")
        val fresh = recent.copy(timestampEpochMillis = 115_001L)
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
    @Test fun repeatedPlanningAcceptsRecentMeasurementsBeforeTheTapWithoutRefreshingGps() = runTest {
        val measured = fix(45.5, -73.5).copy(timestampEpochMillis = 100_000L)
        val location = PendingLocation(measured)
        for (now in listOf(100_001L, 106_000L, 112_000L, 115_000L)) {
            assertEquals(measured, location.awaitPlanningLocation { now })
            assertNull(location.callback)
        }
    }

    @Test fun concurrentRecenterCannotReplaceAPlanningLocationRequest() {
        val queue = LocationRequestQueue()
        val results = mutableListOf<Pair<String, LocationUpdate.Fix?>>()
        queue.add(100_000L) { results += "plan" to it }
        queue.add(100_500L) { results += "recenter" to it }
        // Android may return a valid recent measurement taken before either button was tapped.
        val measured = fix(45.5, -73.5).copy(timestampEpochMillis = 99_000L)
        queue.receive(measured, 101_000L)
        assertEquals<List<Pair<String, LocationUpdate.Fix?>>>(listOf("plan" to measured, "recenter" to measured), results)
        assertEquals(0, queue.size)
    }

    @Test fun repeatedLocationRequestsKeepTheirOwnDeadlinesAndRejectOldOrFutureMeasurements() {
        val queue = LocationRequestQueue()
        val results = mutableListOf<Pair<String, LocationUpdate.Fix?>>()
        queue.add(100_000L) { results += "first" to it }
        queue.add(110_000L) { results += "second" to it }
        queue.receive(fix(45.5, -73.5).copy(timestampEpochMillis = 80_000L), 112_000L)
        queue.receive(fix(45.5, -73.5).copy(timestampEpochMillis = 113_000L), 112_000L)
        assertTrue(results.isEmpty())
        queue.expire(115_000L)
        assertEquals<List<Pair<String, LocationUpdate.Fix?>>>(listOf("first" to null), results)
        val measured = fix(45.51, -73.51).copy(timestampEpochMillis = 116_000L)
        queue.receive(measured, 116_000L)
        assertEquals(listOf("first" to null, "second" to measured), results)
        assertEquals(0, queue.size)
    }

    @Test fun deniedPermissionCompletesAllLocationRequestsOnce() {
        val queue = LocationRequestQueue()
        val results = mutableListOf<LocationUpdate.Fix?>()
        repeat(2) { queue.add(100_000L) { results += it } }
        queue.denyAccess()
        queue.expire(120_000L)
        assertEquals<List<LocationUpdate.Fix?>>(listOf(null, null), results)
        assertEquals(0, queue.size)
    }

    @Test fun locationDeadlineStartsAfterPermissionDialogOpenLongerThanFifteenSeconds() = runTest {
        var now = 100_000L
        var permitted = false
        val queue = LocationRequestQueue(collectingInitially = false)
        val location = object : PlannerLocation {
            override val fix: LocationUpdate.Fix? = null
            override val currentUpdate: LocationUpdate?
                get() = if (permitted) null else LocationUpdate.PermissionDenied
            override val containsPoint: (GeoPoint) -> Boolean = { true }
            override fun request(onResult: ((LocationUpdate.Fix?) -> Unit)?) {
                queue.add(now) { onResult?.invoke(it) }
            }
        }

        val tripRequest = async { location.awaitPlanningLocation { now } }
        runCurrent()
        assertEquals(1, queue.size)
        assertEquals(0, queue.activeSize)

        // User spends 20 seconds reading the Android permission prompt before granting access.
        now = 120_000L
        advanceTimeBy(20_000L)
        queue.expire(now)
        runCurrent()
        assertFalse(tripRequest.isCompleted, "Permission prompt time must not consume the GPS deadline")

        permitted = true
        assertTrue(queue.setCollecting(true, now))
        assertEquals(1, queue.activeSize)
        // Restarting collection (e.g., approximate -> precise) must not extend an active deadline.
        assertFalse(queue.setCollecting(true, 130_000L))

        now = 134_999L
        advanceTimeBy(14_999L)
        queue.expire(now)
        runCurrent()
        assertFalse(tripRequest.isCompleted)

        val measured = fix(45.51, -73.59).copy(timestampEpochMillis = 134_500L)
        queue.receive(measured, now)
        assertEquals(measured, tripRequest.await())
        assertEquals(0, queue.size)
        assertEquals(0, queue.activeSize)
    }

    @Test fun locationRequestExpiresFifteenSecondsAfterPermissionGrantWhenGpsStaysUnavailable() = runTest {
        var now = 100_000L
        var permitted = false
        val queue = LocationRequestQueue(collectingInitially = false)
        val location = object : PlannerLocation {
            override val fix: LocationUpdate.Fix? = null
            override val currentUpdate: LocationUpdate?
                get() = if (permitted) null else LocationUpdate.PermissionDenied
            override val containsPoint: (GeoPoint) -> Boolean = { true }
            override fun request(onResult: ((LocationUpdate.Fix?) -> Unit)?) {
                queue.add(now) { onResult?.invoke(it) }
            }
        }

        val tripRequest = async { location.awaitPlanningLocation { now } }
        runCurrent()

        now = 125_000L
        advanceTimeBy(25_000L)
        queue.expire(now)
        permitted = true
        assertTrue(queue.setCollecting(true, now))

        now = 140_000L
        advanceTimeBy(15_000L)
        queue.expire(now)
        assertEquals(LocationUpdate.Unavailable, tripRequest.await())
        assertEquals(0, queue.size)
    }

}
