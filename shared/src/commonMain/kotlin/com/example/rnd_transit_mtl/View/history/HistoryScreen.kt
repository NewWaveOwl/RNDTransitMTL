package com.example.rnd_transit_mtl.View.history

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.rnd_transit_mtl.state.trip.LocalTripsStore
import com.example.rnd_transit_mtl.state.trip.TripActionResult
import com.example.rnd_transit_mtl.ui.theme.TransitComplementary
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitWhite

/**
 * Displays completed trips from the shared store.
 *
 * Remove requests confirmation. Only the dialog's explicit confirmation
 * calls removeCompleted. Cancel, Back and outside dismissal keep the trip.
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

    var errorMessage by remember {
        mutableStateOf<String?>(null)
    }

    // Store only the ID. Resolve the latest record from TripsStore.
    var pendingRemovalId by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    var filterName by rememberSaveable { mutableStateOf(TripHistoryFilter.ALL.name) }
    val filter = TripHistoryFilter.entries.firstOrNull { it.name == filterName } ?: TripHistoryFilter.ALL

    val completedTrips = tripsStore.completedTrips
    val pendingRemovalTrip = completedTrips.firstOrNull {
        it.id == pendingRemovalId
    }

    // Do not leave a confirmation pending after navigating away.
    // Also recover if another action removes the selected record.
    LaunchedEffect(
        isDestinationActive,
        pendingRemovalId,
        pendingRemovalTrip?.id
    ) {
        if (!isDestinationActive) {
            pendingRemovalId = null
        } else if (
            pendingRemovalId != null &&
            pendingRemovalTrip == null
        ) {
            pendingRemovalId = null
            errorMessage = "This trip is no longer available."
        }
    }

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

    fun requestRemoval(tripId: String) {
        if (!latestDestinationActive) return

        if (tripsStore.findCompleted(tripId) == null) {
            errorMessage = "This trip is no longer available."
            return
        }

        errorMessage = null
        pendingRemovalId = tripId
    }

    fun confirmRemoval() {
        if (!latestDestinationActive) return
        val tripId = pendingRemovalId ?: return

        // Check again at confirmation time, rather than using an old record.
        if (tripsStore.findCompleted(tripId) == null) {
            pendingRemovalId = null
            errorMessage = "This trip has already been removed."
            return
        }

        when (val result = tripsStore.removeCompleted(tripId)) {
            TripActionResult.Applied -> {
                pendingRemovalId = null
                errorMessage = null
            }

            is TripActionResult.MissingTrip -> {
                pendingRemovalId = null
                errorMessage = "This trip has already been removed."
            }

            is TripActionResult.InvalidInput -> {
                pendingRemovalId = null
                errorMessage = result.message
            }

            else -> {
                pendingRemovalId = null
                errorMessage = "This trip could not be removed."
            }
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
        trips = historyTrips(completedTrips, filter),
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
        onRemove = { requestRemoval(it) },
        actionsEnabled = isDestinationActive,
        errorMessage = errorMessage,
        modifier = modifier
    )
    if (isDestinationActive && pendingRemovalTrip != null) {
        AlertDialog(
            onDismissRequest = {
                pendingRemovalId = null
            },
            containerColor = TransitWhite,
            titleContentColor = TransitComplementary,
            textContentColor = TransitComplementary,
            title = {
                Text("Remove this adventure?")
            },
            text = {
                Text(
                    "Remove “${pendingRemovalTrip.title}” from History? " +
                        "Its saved review will also be removed. " +
                        "This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { confirmRemoval() }
                ) {
                    Text(
                        "Remove trip",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingRemovalId = null
                    }
                ) {
                    Text("Keep trip", color = TransitMain)
                }
            }
        )
    }
}
