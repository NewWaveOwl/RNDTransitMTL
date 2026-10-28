package com.example.rnd_transit_mtl.View.main.previews

import androidx.compose.runtime.*
import androidx.compose.ui.tooling.preview.Preview
import com.example.rnd_transit_mtl.View.current_trip.*
import com.example.rnd_transit_mtl.View.history.previews.reviewedHistoryPreviewTrip
import com.example.rnd_transit_mtl.View.main.*
import com.example.rnd_transit_mtl.layout.previews.NavigationPreviewHost
import com.example.rnd_transit_mtl.model.trip.TripPoint
import com.example.rnd_transit_mtl.navigation.MainScreenKey

/** Interactive failure fixture without provider calls or trip activation.
 * @param failure Explicit outcome being demonstrated.
 */
@Composable
private fun FailureSample(failure: Trip33Failure) {
    var picking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var origin by remember {
        mutableStateOf<TripPoint?>(null)
    }

    NavigationPreviewHost(MainScreenKey) {
        Trip33FallbackContent(
            failure = failure,
            detail = message,
            requestSummary = "30 minutes · Walk + Bus · Current location",
            originLabel = origin?.label ?: "Current location",
            pickingOrigin = picking,
            enabled = true,
            onChooseMap = { picking = true },
            onConfirmOrigin = {
                origin = it
                picking = false
                message = "Sample origin selected. No trip was activated."
            },
            onRetry = {
                message = "Sample retry. No live provider was contacted."
            },
            onEditSettings = {
                message = "Sample edit action. Request remains unchanged."
            },
            onUnavailableAction = {
                message = "Live location and address search are unavailable."
            },
            onCancelPicking = { picking = false },
            sampleLabel = "Sample · ${failure.name}"
        )
    }
}

/** No-route fixture retaining the original request. */
@Preview(name = "#33 No route", widthDp = 412, heightDp = 892)
@Composable
fun NoRoute33Preview() = FailureSample(Trip33Failure.NoRoute)

/** Provider-unavailable fixture with retry and edit actions. */
@Preview(name = "#33 Provider error", widthDp = 412, heightDp = 892)
@Composable
fun ProviderError33Preview() =
    FailureSample(Trip33Failure.ProviderUnavailable)

/** Permission-off fixture allowing manual selection. */
@Preview(name = "#33 Permission denied", widthDp = 412, heightDp = 892)
@Composable
fun Permission33Preview() =
    FailureSample(Trip33Failure.PermissionDenied)

/** Active-map fixture whose actions only change local sample state.
 * @param state Sample location availability.
 */
@Composable
private fun ActiveLocationSample(state: TripLocationState) {
    val trip = reviewedHistoryPreviewTrip.copy(
        id = "sample-024",
        plannedMinutes = 30,
        distanceKm = 2.6666667,
        completedAtEpochMillis = null,
        review = null
    )
    var progress by remember { mutableStateOf(0.4f) }
    var message by remember { mutableStateOf<String?>(null) }

    NavigationPreviewHost(MainScreenKey) {
        ActiveMapContent(
            trip = trip,
            progress = progress,
            actionsEnabled = true,
            onFinish = {
                progress = 1f
                message = "Sample completed."
            },
            onConfirmCancel = {
                message = "Sample cancelled. No real trip was changed."
            },
            instruction = ActiveMapInstruction(
                "Turn left in 120 m",
                "Continue toward the canal",
                showLeftTurnIcon = true
            ),
            locationState = state,
            staleMessage = if (state == TripLocationState.Stale) {
                "Last reading 2 min ago. Route remains available."
            } else null,
            sampleStateLabel = state.name,
            operationMessage = message
        )
    }
}

/** Explicit stale-location fixture retaining its last marker. */
@Preview(name = "#33 Stale location", widthDp = 412, heightDp = 892)
@Composable
fun Stale33Preview() =
    ActiveLocationSample(TripLocationState.Stale)

/** Explicit missing-location fixture retaining the route. */
@Preview(name = "#33 Missing location", widthDp = 412, heightDp = 892)
@Composable
fun Missing33Preview() =
    ActiveLocationSample(TripLocationState.Missing)
