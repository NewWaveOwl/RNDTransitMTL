package com.example.rnd_transit_mtl.View.current_trip

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.example.rnd_transit_mtl.ViewModels.current_trip.CurrentTripLocationViewModel
import com.example.rnd_transit_mtl.ViewModels.current_trip.ReturnPhase
import com.example.rnd_transit_mtl.ViewModels.current_trip.ReturnTransportCatalogue
import com.example.rnd_transit_mtl.ViewModels.current_trip.ReturnTripViewModel
import com.example.rnd_transit_mtl.data.generation.LocalBackendTripGenerator
import com.example.rnd_transit_mtl.data.generation.TripGenerationResult
import com.example.rnd_transit_mtl.data.location.LocalPlannerLocation
import com.example.rnd_transit_mtl.data.location.LocationJumpFilter
import com.example.rnd_transit_mtl.model.ids.TripId
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.model.trip.TripJourneyKind
import com.example.rnd_transit_mtl.navigation.HistoryScreenKey
import com.example.rnd_transit_mtl.navigation.LocalTripNavigation
import com.example.rnd_transit_mtl.simulation.ActiveTripSimulation
import com.example.rnd_transit_mtl.state.debug.LocalDebugModeState
import com.example.rnd_transit_mtl.state.trip.LocalTripsStore
import com.example.rnd_transit_mtl.state.trip.TRIP_SIMULATION_DURATION_MILLIS
import com.example.rnd_transit_mtl.state.trip.TripActionResult

// Layout settings — delegated to CurrentTripContent, TripReturnFlowContent, BackAtStartContent, and CurrentTripUnavailableContent.

/**
 * Coordinates one active or completed trip, with playback only after an explicit debug choice.
 *
 * [trip] supplies the navigation ID used to resolve the authoritative record from [LocalTripsStore].
 * It is never used to reconstruct missing state or restart a completed trip.
 *
 * @param trip Navigation parameter identifying the trip to track or display.
 * @param isDestinationActive True only for the current navigation entry, including while outgoing entries stay composed.
 * @param onReviewRequested Synchronously navigates to or focuses the review destination, returning true once handled.
 * @param onLeave Navigates away after pausing without cancelling the trip.
 * @param onReturnToPlanner Handles successful cancellation or missing-trip recovery.
 * @param modifier Parent layout constraints.
 */
@Composable
fun CurrentTripScreen(
    trip: Trip,
    isDestinationActive: Boolean,
    onReviewRequested: (tripId: TripId) -> Boolean,
    onLeave: () -> Unit,
    onReturnToPlanner: () -> Unit,
    modifier: Modifier = Modifier,
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
        CurrentTripLocationViewModel(
            trip = trip,
            tripsStore = tripsStore,
            initialFix = location?.fix.takeUnless { simulationEnabled },
            jumpFilter = location?.jumpFilter ?: LocationJumpFilter(),
        )
    }
    val locationState by locationModel.state.collectAsStateWithLifecycle()
    var locationRequested by rememberSaveable(trip.id.value) { mutableStateOf(false) }

    val currentState by tripsStore.state
    val activeState = currentState.activeTrip?.takeIf { it.trip.id == trip.id }
    val completedTrip = currentState.completedTrips.firstOrNull { it.id == trip.id }

    val backend = LocalBackendTripGenerator.current
    val returnModel = viewModel(key = "trip-return:${trip.id.value}") {
        ReturnTripViewModel(tripsStore, trip.id, generate = { input, existingIds ->
            backend?.generate(input, ReturnTransportCatalogue, existingIds)
                ?: TripGenerationResult.RoutingUnavailable(
                    "The return planner is not configured. Your completed trip is saved.",
                )
        })
    }
    val returnState by returnModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(isDestinationActive) { if (!isDestinationActive) returnModel.cancelReturn() }
    val returnBackState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    NavigationBackHandler(
        state = returnBackState,
        isBackEnabled = isDestinationActive && completedTrip != null && returnState.phase != ReturnPhase.Arrival,
        onBackCompleted = { returnModel.cancelReturn() },
    )

    val storedTrip = activeState?.trip ?: completedTrip
    val elapsedMillis = when {
        completedTrip != null -> TRIP_SIMULATION_DURATION_MILLIS
        activeState != null -> activeState.elapsedMillis
        else -> 0L
    }
    val progress = if (!simulationEnabled && activeState != null) {
        locationState.displayedProgress ?: activeState.routeProgress ?: 0f
    } else {
        (elapsedMillis.toFloat() / TRIP_SIMULATION_DURATION_MILLIS.toFloat()).coerceIn(0f, 1f)
    }
    val hasActiveTrip = activeState != null
    val simulation = remember(tripsStore, trip.id) { ActiveTripSimulation(tripsStore = tripsStore, tripId = trip.id) }
    var isAppResumed by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var operationMessage by remember(trip.id) { mutableStateOf<String?>(null) }

    CurrentTripLocationTrackingEffect(
        location = location,
        locationModel = locationModel,
        simulationEnabled = simulationEnabled,
        isDestinationActive = isDestinationActive,
        isAppResumed = isAppResumed,
        hasActiveTrip = hasActiveTrip,
        locationRequested = locationRequested,
        onMarkLocationRequested = { locationRequested = true },
    )
    CurrentTripLifecycleEffect(
        simulation = simulation,
        lifecycle = lifecycle,
        isDestinationActive = isDestinationActive,
        simulationRunningAllowed = simulationRunningAllowed,
        onAppResumedChanged = { isAppResumed = it },
    )
    CurrentTripSimulationLoopEffect(
        simulation = simulation,
        lifecycle = lifecycle,
        isDestinationActive = isDestinationActive,
        isAppResumed = isAppResumed,
        hasActiveTrip = hasActiveTrip,
        simulationRunningAllowed = simulationRunningAllowed,
    )

    val latestDestinationActive by rememberUpdatedState(isDestinationActive)
    val latestSimulationRunningAllowed by rememberUpdatedState(simulationRunningAllowed)
    val latestReviewRequest by rememberUpdatedState(onReviewRequested)
    val latestLeave by rememberUpdatedState(onLeave)
    val latestReturnToPlanner by rememberUpdatedState(onReturnToPlanner)

    fun isInteractiveNow(): Boolean =
        latestDestinationActive && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)

    fun requestReview() {
        if (!isInteractiveNow()) return
        if (tripsStore.findCompleted(trip.id) == null) {
            operationMessage = "This completed trip is no longer available."
            return
        }
        if (!latestReviewRequest(trip.id)) {
            operationMessage = "The review screen has not opened. " +
                "Your completed trip is saved. Tap the 100% progress bar to retry."
            return
        }
        // Only acknowledge a pending review event when it still matches this trip.
        operationMessage = if (tripsStore.pendingReviewTripId == trip.id) {
            when (tripsStore.acknowledgeReviewNavigation(trip.id)) {
                TripActionResult.Applied -> null
                is TripActionResult.MissingTrip -> "This trip was removed before review handling finished."
                else -> "The pending review could not be acknowledged."
            }
        } else {
            null
        }
    }

    fun skipCompletion(toHistory: Boolean) {
        if (!isInteractiveNow()) return
        when (val result = tripsStore.skipReview(trip.id)) {
            TripActionResult.Applied -> {
                val handled = if (toHistory) navigation.openSection(HistoryScreenKey) else navigation.returnToPlanner()
                if (!handled) operationMessage = "Your trip is saved. Please try navigating again."
            }
            is TripActionResult.InvalidInput -> operationMessage = result.message
            else -> operationMessage = "The completed trip could not be closed. Please try again."
        }
    }

    when {
        completedTrip != null -> {
            val actionsEnabled = isDestinationActive && isAppResumed
            if (completedTrip.journey != null) {
                BackAtStartContent(
                    trip = completedTrip,
                    onReview = {
                        if (actionsEnabled) {
                            val originalId = completedTrip.journey.outboundTripId
                            if (originalId == null) {
                                requestReview()
                            } else if (
                                tripsStore.skipReview(completedTrip.id) == TripActionResult.Applied &&
                                navigation.openCompletedTrip(originalId) &&
                                navigation.openInitialReview(originalId)
                            ) {
                                operationMessage = null
                            } else {
                                operationMessage = "Your trips are saved. Please try opening the review again."
                            }
                        }
                    },
                    onSkip = { skipCompletion(false) },
                    enabled = actionsEnabled,
                    modifier = modifier,
                    errorMessage = operationMessage,
                )
            } else {
                TripReturnFlowContent(
                    outbound = completedTrip,
                    state = returnState,
                    enabled = actionsEnabled,
                    onFind = { option ->
                        if (actionsEnabled) {
                            operationMessage = null
                            val fix = if (simulationEnabled) null else location?.fix?.takeIf { location.containsPoint(it.point) }
                            returnModel.findReturn(option, fix)
                        }
                    },
                    onCancel = {
                        returnModel.cancelReturn()
                        operationMessage = null
                    },
                    onStart = {
                        if (actionsEnabled) {
                            when (val result = returnModel.startReturn()) {
                                TripActionResult.Applied -> if (!navigation.resumeActiveTrip()) {
                                    operationMessage = "Your return is ready. Tap Start return trip to reopen it."
                                }
                                is TripActionResult.InvalidInput -> operationMessage = result.message
                                else -> operationMessage = "The return could not be started. Your completed trip is saved."
                            }
                        }
                    },
                    onReview = { requestReview() },
                    onSkip = { skipCompletion(true) },
                    onPlanAnother = { skipCompletion(false) },
                    modifier = modifier,
                    syncPending = trip.id in currentState.pendingFinalTripIds,
                    errorMessage = operationMessage,
                )
            }
        }

        storedTrip == null -> CurrentTripUnavailableContent(
            enabled = isDestinationActive && isAppResumed && trip.id !in currentState.pendingFinalTripIds,
            onReturnToPlanner = {
                if (latestDestinationActive) {
                    simulation.pause()
                    latestReturnToPlanner()
                }
            },
            modifier = modifier,
        )

        else -> CurrentTripContent(
            trip = storedTrip,
            progress = progress,
            actionsEnabled = isDestinationActive && isAppResumed,
            message = operationMessage,
            onLeave = {
                if (isInteractiveNow()) {
                    simulation.pause()
                    latestLeave()
                }
            },
            onCancel = { keepInHistory ->
                if (isInteractiveNow()) {
                    // Flush actual elapsed time first so a completed final frame is not deleted.
                    simulation.pause()
                    when (tripsStore.cancel(trip.id, keepInHistory || tripsStore.queueFinalTrips)) {
                        TripActionResult.Applied -> {
                            operationMessage = null
                            latestReturnToPlanner()
                        }
                        TripActionResult.AlreadyCompleted -> {
                            operationMessage = "The trip has finished and is saved. Continue to its review."
                        }
                        is TripActionResult.MissingTrip -> latestReturnToPlanner()
                        is TripActionResult.InvalidInput -> operationMessage = "The trip could not be cancelled."
                        is TripActionResult.ActiveTripExists -> operationMessage = "A different trip is currently active."
                    }
                }
            },
            onFinishRequested = {
                if ((latestSimulationRunningAllowed || trip.journey?.kind == TripJourneyKind.Return) && isInteractiveNow()) {
                    simulation.pause()
                    val result = if (!simulationEnabled && trip.journey?.kind == TripJourneyKind.Return) {
                        tripsStore.updateRouteProgress(trip.id, 1f)
                    } else {
                        tripsStore.updateElapsed(trip.id, TRIP_SIMULATION_DURATION_MILLIS)
                    }
                    operationMessage = when (result) {
                        TripActionResult.Applied,
                        TripActionResult.AlreadyCompleted,
                        -> null
                        else -> "The trip could not be finished. Please try again."
                    }
                }
            },
            onRequestReview = { requestReview() },
            onOpenImageReference = {
                if (isInteractiveNow()) {
                    try {
                        uriHandler.openUri(storedTrip.imageUrl)
                        operationMessage = null
                    } catch (_: Exception) {
                        operationMessage = "The image reference could not be opened. " +
                            "You can copy its URL from Trip information."
                    }
                }
            },
            showTripInformation = isDestinationActive && navigation.informationTripId == trip.id,
            onDismissInformation = { navigation.dismissTripInformation(trip.id) },
            onShowInformation = { if (latestDestinationActive) navigation.requestTripInformation() },
            simulationEnabled = simulationEnabled,
            userPosition = if (simulationEnabled) null else location?.fix?.point ?: locationState.trustedPosition,
            userUncertaintyMeters = if (simulationEnabled) null else locationState.uncertaintyRadiusMeters,
            onRequestLocation = { location?.request() },
            locationState = mapTripLocationState(simulationEnabled, locationState),
            staleMessage = resolveTripStaleMessage(simulationEnabled, locationState),
            modifier = modifier,
        )
    }
}
