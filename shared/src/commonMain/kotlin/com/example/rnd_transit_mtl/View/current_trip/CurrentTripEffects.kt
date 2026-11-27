package com.example.rnd_transit_mtl.View.current_trip

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.rnd_transit_mtl.ViewModels.current_trip.CurrentTripLocationState
import com.example.rnd_transit_mtl.ViewModels.current_trip.CurrentTripLocationViewModel
import com.example.rnd_transit_mtl.ViewModels.current_trip.TripLocationAvailability
import com.example.rnd_transit_mtl.data.location.PlannerLocation
import com.example.rnd_transit_mtl.simulation.ActiveTripSimulation
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// Timing settings — milliseconds between GPS freshness checks during live trip tracking.
private const val LOCATION_FRESHNESS_POLL_INTERVAL_MILLIS = 250L

/**
 * Connects [CurrentTripLocationViewModel] to the shared [PlannerLocation] stream while an active
 * trip is visible in normal (non-simulated) mode, polling freshness every [LOCATION_FRESHNESS_POLL_INTERVAL_MILLIS].
 *
 * @param location Shared location adapter, or null on platforms without live GPS.
 * @param locationModel ViewModel owning route progress and GPS uncertainty state.
 * @param simulationEnabled Whether debug simulation mode is active.
 * @param isDestinationActive Whether CurrentTripScreen is the active navigation entry.
 * @param isAppResumed Whether the host lifecycle is currently at least `RESUMED`.
 * @param hasActiveTrip Whether the displayed trip is still active.
 * @param locationRequested Whether an initial location request has already been sent for this trip.
 * @param onMarkLocationRequested Marks that the initial location request was dispatched.
 */
@Composable
internal fun CurrentTripLocationTrackingEffect(
    location: PlannerLocation?,
    locationModel: CurrentTripLocationViewModel,
    simulationEnabled: Boolean,
    isDestinationActive: Boolean,
    isAppResumed: Boolean,
    hasActiveTrip: Boolean,
    locationRequested: Boolean,
    onMarkLocationRequested: () -> Unit,
) {
    val latestMarkRequested by rememberUpdatedState(onMarkLocationRequested)
    val latestRequested by rememberUpdatedState(locationRequested)

    LaunchedEffect(location, locationModel, simulationEnabled, isDestinationActive, isAppResumed, hasActiveTrip) {
        if (simulationEnabled || !isDestinationActive || !isAppResumed || !hasActiveTrip) {
            locationModel.stop()
            return@LaunchedEffect
        }
        if (location == null) {
            locationModel.unsupported()
            return@LaunchedEffect
        }
        locationModel.startTracking()
        locationModel.useSharedFix(location.fix)
        if (!latestRequested) {
            latestMarkRequested()
            if (location.fix == null) location.request()
        }
        location.fix?.let(locationModel::receive)
        val collector = launch { location.trackingUpdates?.collect(locationModel::receive) }
        try {
            while (isActive) {
                delay(LOCATION_FRESHNESS_POLL_INTERVAL_MILLIS)
                locationModel.refreshFreshness()
            }
        } finally {
            collector.cancel()
            locationModel.stop()
        }
    }
}

/**
 * Synchronously flushes and pauses [simulation] when the host lifecycle leaves `RESUMED`
 * or the destination becomes inactive.
 *
 * @param simulation Active-trip debug simulation coordinator.
 * @param lifecycle Current platform lifecycle.
 * @param isDestinationActive Whether CurrentTripScreen is the current navigation entry.
 * @param simulationRunningAllowed Whether debug simulation is currently permitted to advance.
 * @param onAppResumedChanged Reports updates to whether the lifecycle is at least `RESUMED`.
 */
@Composable
internal fun CurrentTripLifecycleEffect(
    simulation: ActiveTripSimulation,
    lifecycle: Lifecycle,
    isDestinationActive: Boolean,
    simulationRunningAllowed: Boolean,
    onAppResumedChanged: (Boolean) -> Unit,
) {
    val latestOnResumedChanged by rememberUpdatedState(onAppResumedChanged)

    DisposableEffect(simulation, lifecycle, isDestinationActive, simulationRunningAllowed) {
        val observer = LifecycleEventObserver { _, _ ->
            val resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (!resumed) simulation.pause()
            latestOnResumedChanged(resumed)
        }

        lifecycle.addObserver(observer)
        val resumedNow = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        latestOnResumedChanged(resumedNow)

        if (!simulationRunningAllowed || !isDestinationActive || !resumedNow) {
            simulation.pause()
        }

        onDispose {
            lifecycle.removeObserver(observer)
            simulation.pause()
        }
    }
}

/**
 * Drives frame-aligned checkpoints on [simulation] using monotonic elapsed time while debug
 * simulation is active and the destination is resumed.
 *
 * @param simulation Active-trip simulation coordinator.
 * @param lifecycle Host lifecycle used to verify resumed status each frame.
 * @param isDestinationActive Whether CurrentTripScreen is the active navigation entry.
 * @param isAppResumed Whether the host lifecycle is currently at least `RESUMED`.
 * @param hasActiveTrip Whether the trip has not yet completed.
 * @param simulationRunningAllowed Whether simulation playback is currently allowed.
 */
@Composable
internal fun CurrentTripSimulationLoopEffect(
    simulation: ActiveTripSimulation,
    lifecycle: Lifecycle,
    isDestinationActive: Boolean,
    isAppResumed: Boolean,
    hasActiveTrip: Boolean,
    simulationRunningAllowed: Boolean,
) {
    val latestDestinationActive by rememberUpdatedState(isDestinationActive)
    val latestSimulationRunningAllowed by rememberUpdatedState(simulationRunningAllowed)

    // Elapsed milliseconds are deliberately not an effect key so each frame does not restart the timer.
    LaunchedEffect(simulation, isDestinationActive, isAppResumed, hasActiveTrip, simulationRunningAllowed) {
        if (
            !simulationRunningAllowed ||
            !isDestinationActive ||
            !isAppResumed ||
            !hasActiveTrip ||
            !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        ) {
            return@LaunchedEffect
        }

        val session = simulation.resume() ?: return@LaunchedEffect
        try {
            while (isActive) {
                withFrameNanos { _ -> }
                if (
                    !latestSimulationRunningAllowed ||
                    !latestDestinationActive ||
                    !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                ) {
                    break
                }
                if (!simulation.checkpoint(session)) break
            }
        } finally {
            // Flushes a partial frame when cancelled; the session guard protects newer sessions.
            simulation.pause(session)
        }
    }
}

/**
 * Maps [CurrentTripLocationState] and simulation flag to the active map's [TripLocationState].
 *
 * @param simulationEnabled Whether debug simulation playback is active.
 * @param locationState Current location tracking state snapshot.
 * @return Presentation-level [TripLocationState].
 */
internal fun mapTripLocationState(
    simulationEnabled: Boolean,
    locationState: CurrentTripLocationState,
): TripLocationState = if (simulationEnabled) {
    TripLocationState.Sample
} else {
    when (locationState.availability) {
        TripLocationAvailability.Available -> TripLocationState.Live
        TripLocationAvailability.LastKnown -> TripLocationState.LastKnown
        TripLocationAvailability.LowAccuracy ->
            if (locationState.matchedPosition != null) TripLocationState.MatchedLowAccuracy else TripLocationState.LowAccuracy
        TripLocationAvailability.Estimated -> TripLocationState.LastKnown
        TripLocationAvailability.Stale -> TripLocationState.Stale
        TripLocationAvailability.PermissionDenied -> TripLocationState.PermissionDenied
        TripLocationAvailability.Waiting -> TripLocationState.Waiting
        TripLocationAvailability.Unsupported -> TripLocationState.NotTracking
    }
}

/**
 * Derives an optional stale or off-route warning message for live GPS tracking.
 *
 * @param simulationEnabled Whether debug simulation playback is active.
 * @param locationState Current location tracking state snapshot.
 * @return Warning text, or null when no warning applies.
 */
internal fun resolveTripStaleMessage(
    simulationEnabled: Boolean,
    locationState: CurrentTripLocationState,
): String? = if (simulationEnabled) {
    null
} else {
    when {
        locationState.availability == TripLocationAvailability.Stale ->
            "No fresh location reading for over 30 seconds. Your last position is kept on the map."
        locationState.offRoute ->
            "You are away from the planned route. Your position remains visible."
        else -> null
    }
}
