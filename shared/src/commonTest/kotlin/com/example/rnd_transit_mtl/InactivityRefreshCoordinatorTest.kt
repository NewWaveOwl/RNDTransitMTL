package com.example.rnd_transit_mtl

import com.example.rnd_transit_mtl.state.refresh.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class InactivityRefreshCoordinatorTest {
    @Test fun coldStartAndShortBackgroundDoNotRefreshOrReplayBoot() = runTest {
        var now = 0L
        val refresh = InactivityRefreshCoordinator { now }
        refresh.foreground()
        refresh.refreshPending { error("Cold start already loads data") }
        refresh.background()
        now += APP_IDLE_REFRESH_MILLIS - 1
        refresh.foreground()
        refresh.refreshPending { error("Short background must keep existing data") }
        assertEquals(0, refresh.cycle)
    }

    @Test fun longIdleAndDuplicateLifecycleEventsStartOnlyOneRefresh() = runTest {
        var now = 0L
        val refresh = InactivityRefreshCoordinator { now }
        refresh.foreground()
        refresh.background()
        now += APP_IDLE_REFRESH_MILLIS
        refresh.background() // Duplicate stop must not reset the idle clock.
        refresh.foreground()
        refresh.foreground()
        val release = CompletableDeferred<Unit>()
        var requests = 0
        val first = async { refresh.refreshPending { requests++; release.await() } }
        runCurrent()
        refresh.request()
        refresh.refreshPending { requests++ }
        assertTrue(refresh.isRefreshing)
        assertEquals(1, requests)
        release.complete(Unit)
        first.await()
        refresh.refreshPending { requests++ }
        assertEquals(1, requests)
        assertFalse(refresh.isRefreshing)
        assertFalse(refresh.failed)
    }

    @Test fun failureReleasesOverlayAndExplicitRetryRunsAgain() = runTest {
        val refresh = InactivityRefreshCoordinator()
        refresh.foreground()
        refresh.request()
        refresh.refreshPending { error("Offline") }
        assertTrue(refresh.failed)
        assertFalse(refresh.isRefreshing)
        refresh.request()
        refresh.refreshPending { }
        assertFalse(refresh.failed)
        assertEquals(2, refresh.cycle)
    }

    @Test fun timeoutReleasesOverlayAndKeepsRetryAvailable() = runTest {
        val refresh = InactivityRefreshCoordinator()
        refresh.foreground()
        refresh.request()
        refresh.refreshPending { awaitCancellation() }
        assertEquals(APP_REFRESH_TIMEOUT_MILLIS, testScheduler.currentTime)
        assertTrue(refresh.failed)
        assertFalse(refresh.isRefreshing)
    }

    @Test fun backgroundCancellationDefersInterruptedRefreshUntilForeground() = runTest {
        val refresh = InactivityRefreshCoordinator()
        refresh.foreground()
        refresh.request()
        val job = async { refresh.refreshPending { awaitCancellation() } }
        runCurrent()
        refresh.background()
        job.cancelAndJoin()
        refresh.refreshPending { error("Must not refresh in background") }
        assertFalse(refresh.failed)
        assertFalse(refresh.isRefreshing)
        refresh.foreground()
        var retried = false
        refresh.refreshPending { retried = true }
        assertTrue(retried)
    }
}
