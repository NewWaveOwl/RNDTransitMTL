package com.example.rnd_transit_mtl.state.refresh

import androidx.compose.runtime.*
import androidx.lifecycle.compose.LifecycleStartEffect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlin.time.TimeSource

internal const val APP_IDLE_REFRESH_MILLIS = 5 * 60_000L
internal const val APP_REFRESH_TIMEOUT_MILLIS = 15_000L

private fun monotonicClock(): () -> Long {
    val origin = TimeSource.Monotonic.markNow()
    return { origin.elapsedNow().inWholeMilliseconds }
}

/** Refreshes existing session data after a long background interval, without recreating navigation. */
internal class InactivityRefreshCoordinator(private val now: () -> Long = monotonicClock()) {
    var isForeground by mutableStateOf(false)
        private set
    var isRefreshing by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set
    var cycle by mutableIntStateOf(0)
        private set
    var requestVersion by mutableIntStateOf(0)
        private set
    private var backgroundedAt: Long? = null
    private var pending = false

    fun foreground() {
        if (isForeground) return
        isForeground = true
        val idleSince = backgroundedAt
        backgroundedAt = null
        if (idleSince != null && now() - idleSince >= APP_IDLE_REFRESH_MILLIS) request()
    }

    fun background() {
        if (!isForeground) return
        isForeground = false
        backgroundedAt = now()
    }

    fun request() {
        if (isRefreshing) return
        pending = true
        requestVersion++
    }

    suspend fun refreshPending(refresh: suspend () -> Unit) {
        if (!isForeground || !pending || isRefreshing) return
        pending = false
        isRefreshing = true
        failed = false
        cycle++
        try {
            withTimeout(APP_REFRESH_TIMEOUT_MILLIS) { refresh() }
        } catch (_: TimeoutCancellationException) {
            failed = true
        } catch (cancelled: CancellationException) {
            pending = true
            throw cancelled
        } catch (_: Exception) {
            failed = true
        } finally {
            isRefreshing = false
        }
    }
}

@Composable
internal fun rememberInactivityRefresh(
    identity: String,
    clock: (() -> Long)? = null,
    refresh: suspend () -> Unit,
): InactivityRefreshCoordinator {
    val coordinator = remember(identity) {
        if (clock == null) InactivityRefreshCoordinator() else InactivityRefreshCoordinator(clock)
    }
    val latestRefresh by rememberUpdatedState(refresh)
    LifecycleStartEffect(coordinator) {
        coordinator.foreground()
        onStopOrDispose { coordinator.background() }
    }
    LaunchedEffect(coordinator, coordinator.isForeground, coordinator.requestVersion) {
        coordinator.refreshPending { latestRefresh() }
    }
    return coordinator
}
