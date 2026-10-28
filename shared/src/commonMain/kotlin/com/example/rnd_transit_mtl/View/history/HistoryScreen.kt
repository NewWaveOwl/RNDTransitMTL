package com.example.rnd_transit_mtl.View.history

import com.example.rnd_transit_mtl.ui.global_ui_ellements.trip.HistoryV3
import com.example.rnd_transit_mtl.ui.global_ui_ellements.trip.HistoryRowV3
import com.example.rnd_transit_mtl.navigation.LocalTripNavigation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.rnd_transit_mtl.state.trip.LocalTripsStore
import com.example.rnd_transit_mtl.state.trip.TripActionResult

/**
 * Coordinates completed History from the one shared TripsStore.
 *
 * Navigation callbacks receive stable IDs. Review navigation should
 * open TripReviewMode.EDIT with History as its origin, including when
 * adding feedback to a previously skipped trip.
 *
 * Nullable callbacks keep the current Router call valid until final
 * route integration. Unavailable navigation actions are disabled.
 */
@Composable
fun HistoryScreen(
    onOpenDetails: ((String) -> Unit)? = null,
    onReview: ((String) -> Unit)? = null,
    isDestinationActive: Boolean = true,
    modifier: Modifier = Modifier
) {
    val navigation = LocalTripNavigation.current
    val tripsStore = LocalTripsStore.current
    val latestDestinationActive by rememberUpdatedState(
        isDestinationActive
    )

    var errorMessage by remember {
        mutableStateOf<String?>(null)
    }


    /*
     * The getter reads observable store state during composition.
     * No separate History collection is stored here.
     */
    val completedTrips = tripsStore.completedTrips

    fun openExistingTrip(
        tripId: String,
        callback: (String) -> Unit
    ) {
        if (!latestDestinationActive) return

        if (tripsStore.findCompleted(tripId) == null) {
            errorMessage = "This trip is no longer available."
        } else {
            errorMessage = null
            callback(tripId)
        }
    }

    val detailsCallback: ((String) -> Unit)? =
        onOpenDetails?.let { callback ->
            { tripId -> openExistingTrip(tripId, callback) }
        }

    val reviewCallback: ((String) -> Unit)? =
        onReview?.let { callback ->
            { tripId -> openExistingTrip(tripId, callback) }
        }

    val onPlanNew = { if (latestDestinationActive) navigation.returnToPlanner(); Unit }
    HistoryV3(
        rows = historyTrips(completedTrips, TripHistoryFilter.ALL).map { trip ->
            HistoryRowV3(
                id = trip.id,
                title = trip.title,
                summary = formatTripTimestampUtc(
                    trip.completedAtEpochMillis ?: trip.createdAtEpochMillis
                ) + " · " + tripTransportSummary(trip),
                status = "Completed",
                completed = true
            )
        },
        onOpenTrip = { id -> detailsCallback?.invoke(id) },
        onReviewTrip = reviewCallback,
        onRemoveTrip = { id ->
            if (latestDestinationActive) {
                errorMessage = when (val result = tripsStore.removeCompleted(id)) {
                    TripActionResult.Applied -> null
                    is TripActionResult.InvalidInput -> result.message
                    else -> "This trip could not be removed."
                }
            }
        },
        onPlanNew = onPlanNew,
        onGo = onPlanNew,
        enabled = isDestinationActive && detailsCallback != null,
        errorMessage = errorMessage,
        modifier = modifier
    )
}
