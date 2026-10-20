package com.example.rnd_transit_mtl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.rnd_transit_mtl.state.LocalTripsStore
import com.example.rnd_transit_mtl.state.TripActionResult
import com.example.rnd_transit_mtl.ui.HistoryContent
import com.example.rnd_transit_mtl.ui.TripHistoryFilter
import com.example.rnd_transit_mtl.ui.historyTrips

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
    val tripsStore = LocalTripsStore.current
    val latestDestinationActive by rememberUpdatedState(
        isDestinationActive
    )

    var filterName by rememberSaveable {
        mutableStateOf(TripHistoryFilter.ALL.name)
    }

    var errorMessage by remember {
        mutableStateOf<String?>(null)
    }

    val filter = TripHistoryFilter.entries.firstOrNull {
        it.name == filterName
    } ?: TripHistoryFilter.ALL

    /*
     * The getter reads observable store state during composition.
     * No separate History collection is stored here.
     */
    val completedTrips = tripsStore.completedTrips
    val displayedTrips = historyTrips(completedTrips, filter)

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

    HistoryContent(
        trips = displayedTrips,
        filter = filter,
        totalCount = completedTrips.size,
        onFilterChange = { selected ->
            if (latestDestinationActive) {
                filterName = selected.name
                errorMessage = null
            }
        },
        onOpenDetails = detailsCallback,
        onReview = reviewCallback,
        onRemove = { tripId ->
            if (latestDestinationActive) {
                errorMessage = when (
                    val result = tripsStore.removeCompleted(tripId)
                ) {
                    TripActionResult.Applied -> null
                    is TripActionResult.MissingTrip ->
                        "This trip has already been removed."
                    is TripActionResult.InvalidInput -> result.message
                    else -> "The trip could not be removed."
                }
            }
        },
        actionsEnabled = isDestinationActive,
        errorMessage = errorMessage,
        modifier = modifier
    )
}
