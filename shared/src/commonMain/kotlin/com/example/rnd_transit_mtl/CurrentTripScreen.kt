package com.example.rnd_transit_mtl

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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.rnd_transit_mtl.model.Trip
import com.example.rnd_transit_mtl.simulation.ActiveTripSimulation
import com.example.rnd_transit_mtl.state.LocalTripsStore
import com.example.rnd_transit_mtl.state.TRIP_SIMULATION_DURATION_MILLIS
import com.example.rnd_transit_mtl.state.TripActionResult
import com.example.rnd_transit_mtl.ui.CurrentTripContent
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitWhite
import kotlinx.coroutines.isActive

/**
 * Coordinates one trip's active simulation.
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
    onReviewRequested: (tripId: String) -> Boolean,
    onLeave: () -> Unit,
    onReturnToPlanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tripsStore = LocalTripsStore.current
    val navigation = LocalTripNavigation.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val uriHandler = LocalUriHandler.current

    val currentState by tripsStore.state

    val activeState = currentState.activeTrip
        ?.takeIf { it.trip.id == trip.id }

    val completedTrip = currentState.completedTrips
        .firstOrNull { it.id == trip.id }

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

    val progress = (
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

    val latestDestinationActive by rememberUpdatedState(
        isDestinationActive
    )
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
        isDestinationActive
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

        if (!isDestinationActive || !resumedNow) {
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
        hasActiveTrip
    ) {
        if (
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

    if (storedTrip == null) {
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
                enabled = isDestinationActive && isAppResumed,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Return to planner")
            }
        }
    } else {
        CurrentTripContent(
            trip = storedTrip,
            progress = progress,
            isRunning = hasActiveTrip &&
                    isDestinationActive &&
                    isAppResumed,
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
            onCancel = {
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

                    when (tripsStore.cancel(trip.id)) {
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
            mapInteractive = true,
            animateEntrance = true,
            onShowInformation = {
                if (latestDestinationActive) navigation.requestTripInformation()
            },
            modifier = modifier
        )
    }
}
