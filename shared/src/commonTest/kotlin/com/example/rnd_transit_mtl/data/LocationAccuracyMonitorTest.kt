package com.example.rnd_transit_mtl.data

import com.example.rnd_transit_mtl.data.location.*
import com.example.rnd_transit_mtl.model.geo.GeoPoint
import kotlin.test.*

class LocationAccuracyMonitorTest {
    private var now = 1_000_000L
    private val monitor = LocationAccuracyMonitor(nowEpochMillis = { now }).apply {
        updateAccess(LocationPermissionLevel.Precise, true)
    }
    private fun reading(accuracy: Double, after: Long = 0L) {
        now += after
        monitor.receive(LocationUpdate.Fix(GeoPoint(45.5, -73.6), accuracy, now))
    }
    private fun weakSignal() { repeat(4) { reading(100.0, if (it == 0) 0L else 5_000L) } }

    @Test
    fun requiresFifteenSecondsOfFreshWeakReadingsAndIgnoresBriefDrops() {
        repeat(3) { reading(80.0, if (it == 0) 0L else 5_000L) }
        assertNull(monitor.state.prompt)
        reading(15.0, 2_000L)
        repeat(3) { reading(80.0, 5_000L) }
        assertNull(monitor.state.prompt)
        reading(80.0, 5_000L)
        assertEquals(LocationAccuracyPrompt.WeakSignal, monitor.state.prompt)
    }

    @Test
    fun requiresTenSecondsOfGoodReadingsAndBorderlineAccuracyDoesNotFlicker() {
        weakSignal()
        reading(20.0, 2_000L)
        reading(20.0, 5_000L)
        assertEquals(LocationAccuracyPrompt.WeakSignal, monitor.state.prompt)
        reading(35.0, 1_000L)
        reading(20.0, 1_000L)
        reading(20.0, 5_000L)
        assertEquals(LocationAccuracyPrompt.WeakSignal, monitor.state.prompt)
        reading(20.0, 5_000L)
        assertNull(monitor.state.prompt)
    }

    @Test
    fun silenceAndStaleRepeatedOrFutureReadingsCannotCreateAnAccuracyPrompt() {
        reading(100.0)
        val old = LocationUpdate.Fix(GeoPoint(45.5, -73.6), 200.0, now)
        now += 15_000L
        monitor.receive(old)
        monitor.tick()
        assertNull(monitor.state.prompt)
        now += 16_000L
        monitor.receive(old)
        monitor.receive(old.copy(timestampEpochMillis = now + 1))
        monitor.tick()
        assertNull(monitor.state.prompt)
        assertEquals(31L, monitor.state.readingAgeSeconds)
        assertEquals(100.0, monitor.state.accuracyMeters)
        reading(100.0)
        assertNull(monitor.state.prompt)
    }

    @Test
    fun staleDataClearsWeakPromptWithoutInventingALocationNotUpdatingWarning() {
        weakSignal()
        now += 16_000L
        monitor.tick()
        assertNull(monitor.state.prompt)
        assertEquals(16L, monitor.state.readingAgeSeconds)
        assertEquals(100.0, monitor.state.accuracyMeters)
    }

    @Test
    fun approximatePermissionPromptsImmediatelyAndPreciseGrantClearsIt() {
        monitor.updateAccess(LocationPermissionLevel.Approximate, true)
        assertEquals(LocationAccuracyPrompt.PrecisePermission, monitor.state.prompt)
        reading(10.0)
        assertEquals(LocationAccuracyPrompt.PrecisePermission, monitor.state.prompt)
        monitor.updateAccess(LocationPermissionLevel.Precise, true)
        assertNull(monitor.state.prompt)
        monitor.updateAccess(LocationPermissionLevel.Denied, true)
        assertNull(monitor.state.prompt)
    }

    @Test
    fun dismissalSurvivesMonitorRecreationAndSuppressesAllReasonsForTenMinutes() {
        var saved: Long? = null
        val first = LocationAccuracyMonitor({ now }, onDismiss = { saved = it })
        first.updateAccess(LocationPermissionLevel.Approximate, true)
        first.dismissPrompt()
        assertEquals(now, saved)
        val restarted = LocationAccuracyMonitor({ now }, dismissedAtMillis = saved)
        restarted.updateAccess(LocationPermissionLevel.Approximate, true)
        assertNull(restarted.state.prompt)
        restarted.updateAccess(LocationPermissionLevel.Approximate, false)
        assertNull(restarted.state.prompt)
        now += 599_999L
        restarted.tick()
        assertNull(restarted.state.prompt)
        now++
        restarted.tick()
        assertEquals(LocationAccuracyPrompt.LocationDisabled, restarted.state.prompt)
    }

    @Test
    fun pauseAndPermissionChangesDoNotCountBackgroundTimeAsPoorSignal() {
        repeat(3) { reading(100.0, 5_000L) }
        monitor.pause()
        now += 5_000L
        reading(100.0)
        assertNull(monitor.state.prompt)
        monitor.updateAccess(LocationPermissionLevel.Approximate, true)
        monitor.updateAccess(LocationPermissionLevel.Precise, true)
        reading(100.0, 5_000L)
        assertNull(monitor.state.prompt)
    }

    @Test
    fun systemLocationStatusTakesPriorityAndDoesNotEraseLastReading() {
        reading(12.0)
        monitor.updateAccess(LocationPermissionLevel.Approximate, false)
        assertEquals(LocationAccuracyPrompt.LocationDisabled, monitor.state.prompt)
        assertEquals(12.0, monitor.state.accuracyMeters)
        monitor.updateAccess(LocationPermissionLevel.Approximate, true)
        assertEquals(LocationAccuracyPrompt.PrecisePermission, monitor.state.prompt)
    }
}
