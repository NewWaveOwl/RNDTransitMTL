package com.example.rnd_transit_mtl.View.current_trip

import com.example.rnd_transit_mtl.model.ids.TripId
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.rnd_transit_mtl.ViewModels.current_trip.CurrentTripLocationViewModel
import com.example.rnd_transit_mtl.ViewModels.current_trip.TripLocationAvailability
import com.example.rnd_transit_mtl.data.location.LocalPlannerLocation
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.simulation.ActiveTripSimulation
import com.example.rnd_transit_mtl.state.debug.LocalDebugModeState
import com.example.rnd_transit_mtl.state.trip.LocalTripsStore
import com.example.rnd_transit_mtl.state.trip.TRIP_SIMULATION_DURATION_MILLIS
import com.example.rnd_transit_mtl.state.trip.TripActionResult
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitWhite
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.rnd_transit_mtl.navigation.LocalTripNavigation
import com.example.rnd_transit_mtl.navigation.HistoryScreenKey
import com.example.rnd_transit_mtl.ui.global_ui_ellements.trip.CompleteV3
import com.example.rnd_transit_mtl.ViewModels.current_trip.ReturnTripViewModel
import com.example.rnd_transit_mtl.ViewModels.current_trip.ReturnTransportCatalogue
import com.example.rnd_transit_mtl.ViewModels.current_trip.ReturnPhase
import com.example.rnd_transit_mtl.data.generation.LocalBackendTripGenerator
import com.example.rnd_transit_mtl.data.generation.TripGenerationResult
import com.example.rnd_transit_mtl.model.trip.TripJourneyKind
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState

/**
 * Coordinates one active trip, with playback only after an explicit debug choice.
 *
 * trip is the entered/generated navigation parameter.
 * Its ID resolves the current record from TripsStore. The parameter
 * is never used to reconstruct missing state or restart a completed trip.
 *
 * isDestinationActive must be true only for the current navigation entry,
 * including while outgoing entry content remains composed.
 *
 * onReviewRequested must synchronously:
 * - establish the matching review destination, or recognize it exists;
 * - avoid inserting a duplicate matching destination;
 * - return true only after navigation is handled.
 *
 * Returning false preserves the pending review event for retry.
 *
 * onLeave navigates away after pausing without cancelling the trip.
 * onReturnToPlanner handles successful cancellation or missing-trip recovery.
 */
@Composable
fun CurrentTripScreen(
    trip: Trip,
    isDestinationActive: Boolean,
    onReviewRequested: (tripId: TripId) -> Boolean,
    onLeave: () -> Unit,
    onReturnToPlanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tripsStore = LocalTripsStore.current
    val navigation = LocalTripNavigation.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val uriHandler = LocalUriHandler.current
    val debugMode = LocalDebugModeState.current
    val simulationEnabled = debugMode?.simulationEnabled == true
    val simulationRunningAllowed = simulationEnabled && !debugMode.isModeChooserVisible
    val location = LocalPlannerLocation.current
    val locationModel = viewModel(key = "trip-location:${trip.id.value}") {
        CurrentTripLocationViewModel(trip, tripsStore, initialFix = location?.fix.takeUnless { simulationEnabled })
    }
    val locationState by locationModel.state.collectAsStateWithLifecycle()
    var locationRequested by rememberSaveable(trip.id.value) { mutableStateOf(false) }

    val currentState by tripsStore.state

    val activeState = currentState.activeTrip
        ?.takeIf { it.trip.id == trip.id }

    val completedTrip = currentState.completedTrips
        .firstOrNull { it.id == trip.id }

    val backend = LocalBackendTripGenerator.current
    val returnModel = viewModel(key = "trip-return:${trip.id.value}") {
        ReturnTripViewModel(tripsStore, trip.id, generate = { input, existingIds ->
            backend?.generate(input, ReturnTransportCatalogue, existingIds)
                ?: TripGenerationResult.RoutingUnavailable("The return planner is not configured. Your completed trip is saved.")
        })
    }
    val returnState by returnModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(isDestinationActive) { if (!isDestinationActive) returnModel.cancelReturn() }
    val returnBackState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    NavigationBackHandler(returnBackState,
        isBackEnabled = isDestinationActive && completedTrip != null && returnState.phase != ReturnPhase.Arrival,
        onBackCompleted = { returnModel.cancelReturn() })

    val storedTrip = activeState?.trip ?: completedTrip

    /*
     * Completed records explicitly remain at 100% even though the store
     * has coherently cleared its active state.
     */
    val elapsedMillis = when {
        completedTrip != null -> TRIP_SIMULATION_DURATION_MILLIS
        activeState != null -> activeState.elapsedMillis
        else -> 0L
    }

    val progress = if (!simulationEnabled && activeState != null) locationState.estimatedProgress ?: activeState.routeProgress ?: 0f else (
            elapsedMillis.toFloat() /
                    TRIP_SIMULATION_DURATION_MILLIS.toFloat()
            ).coerceIn(0f, 1f)

    val hasActiveTrip = activeState != null

    val simulation = remember(tripsStore, trip.id) {
        ActiveTripSimulation(
            tripsStore = tripsStore,
            tripId = trip.id
        )
    }

    var isAppResumed by remember(lifecycle) {
        mutableStateOf(
            lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        )
    }

    var operationMessage by remember(trip.id) {
        mutableStateOf<String?>(null)
    }

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
        if (!locationRequested) {
            locationRequested = true
            if (location.fix == null) location.request()
        }
        location.fix?.let(locationModel::receive)
        val collector = launch { location.automaticOriginUpdates?.collect(locationModel::receive) }
        try {
            while (isActive) {
                delay(250)
                locationModel.refreshFreshness()
            }
        } finally {
            collector.cancel()
            locationModel.stop()
        }
    }

    val latestDestinationActive by rememberUpdatedState(
        isDestinationActive
    )
    val latestSimulationRunningAllowed by rememberUpdatedState(simulationRunningAllowed)
    val latestReviewRequest by rememberUpdatedState(onReviewRequested)
    val latestLeave by rememberUpdatedState(onLeave)
    val latestReturnToPlanner by rememberUpdatedState(onReturnToPlanner)

    /*
     * Lifecycle pause flushes elapsed time synchronously at the event.
     * Waiting for the next frame after backgrounding would otherwise
     * risk counting the background interval.
     *
     * Changing destination activity also disposes the previous effect
     * and pauses its current session.
     */
    DisposableEffect(
        simulation,
        lifecycle,
        isDestinationActive,
        simulationRunningAllowed
    ) {
        val observer = LifecycleEventObserver { _, _ ->
            val resumed = lifecycle.currentState
                .isAtLeast(Lifecycle.State.RESUMED)

            if (!resumed) {
                simulation.pause()
            }

            isAppResumed = resumed
        }

        lifecycle.addObserver(observer)

        val resumedNow = lifecycle.currentState
            .isAtLeast(Lifecycle.State.RESUMED)

        isAppResumed = resumedNow

        if (!simulationRunningAllowed || !isDestinationActive || !resumedNow) {
            simulation.pause()
        }

        onDispose {
            lifecycle.removeObserver(observer)
            simulation.pause()
        }
    }

    /*
     * Elapsed milliseconds are deliberately not an effect key.
     * Each published frame therefore does not restart the timer.
     *
     * Frames schedule UI updates; the monotonic time mark measures
     * the actual active duration. No delay-counting or independent
     * position/progress animation is used.
     */
    LaunchedEffect(
        simulation,
        isDestinationActive,
        isAppResumed,
        hasActiveTrip,
        simulationRunningAllowed
    ) {
        if (
            !simulationRunningAllowed ||
            !isDestinationActive ||
            !isAppResumed ||
            !hasActiveTrip ||
            !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        ) {
            return@LaunchedEffect
        }

        val session = simulation.resume()
            ?: return@LaunchedEffect

        try {
            while (isActive) {
                withFrameNanos { _ -> }

                if (
                    !latestSimulationRunningAllowed ||
                    !latestDestinationActive ||
                    !lifecycle.currentState
                        .isAtLeast(Lifecycle.State.RESUMED)
                ) {
                    break
                }

                if (!simulation.checkpoint(session)) {
                    break
                }
            }
        } finally {
            /*
             * Flushes a partial frame when this effect is cancelled.
             * The session guard prevents an old finally block from
             * stopping a newer resumed session.
             */
            simulation.pause(session)
        }
    }

    fun requestReview() {
        if (
            !latestDestinationActive ||
            !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        ) {
            return
        }

        if (tripsStore.findCompleted(trip.id) == null) {
            operationMessage =
                "This completed trip is no longer available."
            return
        }

        val handled = latestReviewRequest(trip.id)

        if (!handled) {
            operationMessage =
                "The review screen has not opened. " +
                        "Your completed trip is saved. Tap the 100% progress bar to retry."
            return
        }

        /*
         * A repeated review request may occur after the pending
         * event was already handled. Only acknowledge a matching event.
         */
        if (tripsStore.pendingReviewTripId == trip.id) {
            when (
                tripsStore.acknowledgeReviewNavigation(trip.id)
            ) {
                TripActionResult.Applied -> {
                    operationMessage = null
                }

                is TripActionResult.MissingTrip -> {
                    operationMessage =
                        "This trip was removed before review handling finished."
                }

                else -> {
                    operationMessage =
                        "The pending review could not be acknowledged."
                }
            }
        } else {
            operationMessage = null
        }
    }

    // Completion stays visible at 100%. Review navigation is a user action.
    // The saved pending ID survives rotation until navigation or Skip handles it.

    fun skipCompletion(toHistory: Boolean) {
        if (!latestDestinationActive || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        when (val result = tripsStore.skipReview(trip.id)) {
            TripActionResult.Applied -> {
                val handled = if (toHistory) navigation.openSection(HistoryScreenKey) else navigation.returnToPlanner()
                if (!handled) operationMessage = "Your trip is saved. Please try navigating again."
            }
            is TripActionResult.InvalidInput -> operationMessage = result.message
            else -> operationMessage = "The completed trip could not be closed. Please try again."
        }
    }

    if (completedTrip != null) {
        if (completedTrip.journey != null) {
            BackAtStartContent(completedTrip, onReview = {
                if (isDestinationActive && isAppResumed) {
                    val originalId = completedTrip.journey.outboundTripId
                    if (originalId == null) requestReview()
                    else if (tripsStore.skipReview(completedTrip.id) == TripActionResult.Applied &&
                        navigation.openCompletedTrip(originalId) && navigation.openInitialReview(originalId)) {
                        operationMessage = null
                    } else operationMessage = "Your trips are saved. Please try opening the review again."
                }
            }, onSkip = { skipCompletion(false) }, enabled = isDestinationActive && isAppResumed,
                modifier = modifier, errorMessage = operationMessage)
        } else TripReturnFlowContent(completedTrip, returnState, isDestinationActive && isAppResumed,
            onFind = { option ->
                if (isDestinationActive && isAppResumed) {
                    operationMessage = null
                    val fix = if (simulationEnabled) null else location?.fix?.takeIf { location.containsPoint(it.point) }
                    returnModel.findReturn(option, fix)
                }
            }, onCancel = { returnModel.cancelReturn(); operationMessage = null }, onStart = {
                if (isDestinationActive && isAppResumed) {
                    when (val result = returnModel.startReturn()) {
                        TripActionResult.Applied -> if (!navigation.resumeActiveTrip()) {
                            operationMessage = "Your return is ready. Tap Start return trip to reopen it."
                        }
                        is TripActionResult.InvalidInput -> operationMessage = result.message
                        else -> operationMessage = "The return could not be started. Your completed trip is saved."
                    }
                }
            }, onReview = { requestReview() }, onSkip = { skipCompletion(true) },
            onPlanAnother = { skipCompletion(false) }, modifier = modifier,
            syncPending = trip.id in currentState.pendingFinalTripIds, errorMessage = operationMessage)
    } else if (storedTrip == null) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(TransitWhite)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Trip unavailable",
                style = MaterialTheme.typography.headlineSmall,
                color = TransitMain
            )

            Text(
                text = "This trip was cancelled, removed, or was not " +
                        "restored in the current session. Return to the " +
                        "planner to continue.",
                style = MaterialTheme.typography.bodyLarge,
                color = TransitMain
            )

            Button(
                onClick = {
                    if (latestDestinationActive) {
                        simulation.pause()
                        latestReturnToPlanner()
                    }
                },
                enabled = isDestinationActive && isAppResumed && trip.id !in currentState.pendingFinalTripIds,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Return to planner")
            }
        }
    } else {
        CurrentTripContent(
            trip = storedTrip,
            progress = progress,
            actionsEnabled = isDestinationActive && isAppResumed,
            message = operationMessage,
            onLeave = {
                if (
                    latestDestinationActive &&
                    lifecycle.currentState
                        .isAtLeast(Lifecycle.State.RESUMED)
                ) {
                    simulation.pause()
                    latestLeave()
                }
            },
            onCancel = { keepInHistory ->
                if (
                    latestDestinationActive &&
                    lifecycle.currentState
                        .isAtLeast(Lifecycle.State.RESUMED)
                ) {
                    /*
                     * First flush actual elapsed time. If that final
                     * partial frame completed the trip, cancellation
                     * must not delete the completed record.
                     */
                    simulation.pause()

                    when (tripsStore.cancel(trip.id, keepInHistory || tripsStore.queueFinalTrips)) {
                        TripActionResult.Applied -> {
                            operationMessage = null
                            latestReturnToPlanner()
                        }

                        TripActionResult.AlreadyCompleted -> {
                            operationMessage =
                                "The trip has finished and is saved. " +
                                        "Continue to its review."
                        }

                        is TripActionResult.MissingTrip -> {
                            latestReturnToPlanner()
                        }

                        is TripActionResult.InvalidInput -> {
                            operationMessage =
                                "The trip could not be cancelled."
                        }

                        is TripActionResult.ActiveTripExists -> {
                            operationMessage =
                                "A different trip is currently active."
                        }
                    }
                }
            },
            onFinishRequested = {
                if ((latestSimulationRunningAllowed || trip.journey?.kind == TripJourneyKind.Return) && latestDestinationActive &&
                    lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                ) {
                    simulation.pause()

                    // Explicitly finish the demo simulation and save its completed trip.
                    when (
                        if (!simulationEnabled && trip.journey?.kind == TripJourneyKind.Return) {
                            tripsStore.updateRouteProgress(trip.id, 1f)
                        } else tripsStore.updateElapsed(
                            trip.id,
                            TRIP_SIMULATION_DURATION_MILLIS
                        )
                    ) {
                        TripActionResult.Applied,
                        TripActionResult.AlreadyCompleted -> {
                            operationMessage = null
                        }

                        else -> {
                            operationMessage =
                                "The trip could not be finished. Please try again."
                        }
                    }
                }
            },
            onRequestReview = {
                requestReview()
            },
            onOpenImageReference = {
                if (
                    latestDestinationActive &&
                    lifecycle.currentState
                        .isAtLeast(Lifecycle.State.RESUMED)
                ) {
                    try {
                        uriHandler.openUri(storedTrip.imageUrl)
                        operationMessage = null
                    } catch (_: Exception) {
                        operationMessage =
                            "The image reference could not be opened. " +
                                    "You can copy its URL from Trip information."
                    }
                }
            },
            showTripInformation = isDestinationActive &&
                navigation.informationTripId == trip.id,
            onDismissInformation = {
                navigation.dismissTripInformation(trip.id)
            },
            onShowInformation = {
                if (latestDestinationActive) navigation.requestTripInformation()
            },
            simulationEnabled = simulationEnabled,
            userPosition = if (simulationEnabled) null else locationState.estimatedPosition ?: locationState.fix?.point,
            onRequestLocation = { location?.request() },
            locationState = if (simulationEnabled) TripLocationState.Sample else when (locationState.availability) {
                TripLocationAvailability.Available -> TripLocationState.Live
                TripLocationAvailability.LastKnown -> TripLocationState.LastKnown
                TripLocationAvailability.Estimated -> TripLocationState.Estimated
                TripLocationAvailability.Stale -> if (locationState.estimatedPosition != null) TripLocationState.EstimatedStale else TripLocationState.Stale
                TripLocationAvailability.PermissionDenied -> TripLocationState.PermissionDenied
                TripLocationAvailability.Waiting -> TripLocationState.Waiting
                TripLocationAvailability.Unsupported -> TripLocationState.NotTracking
            },
            staleMessage = if (simulationEnabled) null else when {
                locationState.availability == TripLocationAvailability.Stale ->
                    if (locationState.estimatedPosition != null) "No reliable GPS reading for over 30 seconds. Position is estimated along your saved route."
                    else "No reliable GPS reading for over 30 seconds. Your last position is kept on the map."
                locationState.offRoute -> "You are away from the planned route. Your position remains visible."
                else -> null
            },
            modifier = modifier
        )
    }
}
