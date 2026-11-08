package com.example.rnd_transit_mtl.View.history

import com.example.rnd_transit_mtl.state.NullableTripIdSaver
import com.example.rnd_transit_mtl.model.ids.TripId
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.Column
import com.example.rnd_transit_mtl.data.trips.LocalCloudTrips
import com.example.rnd_transit_mtl.data.trips.tryCloudAction
import com.example.rnd_transit_mtl.model.trip.Trip
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.rnd_transit_mtl.state.trip.LocalTripsStore
import com.example.rnd_transit_mtl.state.trip.TripActionResult
import com.example.rnd_transit_mtl.ui.theme.TransitComplementary
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitWhite

/**
 * Displays owner-scoped History for signed-in users and local History for guests.
 *
 * Remove requests confirmation. Only the dialog's explicit confirmation
 * calls removeCompleted. Cancel, Back and outside dismissal keep the trip.
 */
@Composable
fun HistoryScreen(
    onOpenDetails: ((TripId) -> Unit)? = null,
    onReview: ((TripId) -> Unit)? = null,
    isDestinationActive: Boolean = true,
    modifier: Modifier = Modifier
) {
    val tripsStore = LocalTripsStore.current
    val cloud = LocalCloudTrips.current
    val scope = rememberCoroutineScope()
    var cloudRecords by remember(cloud) { mutableStateOf(emptyList<Trip>()) }
    var loadFailed by remember { mutableStateOf(false) }
    var retry by remember { mutableStateOf(0) }
    var removing by remember { mutableStateOf(false) }

    LaunchedEffect(cloud, cloud?.revision, isDestinationActive, retry) {
        if (cloud != null && isDestinationActive) {
            loadFailed = !tryCloudAction { cloudRecords = cloud.history() }
        }
    }

    val latestDestinationActive by rememberUpdatedState(
        isDestinationActive
    )

    var errorMessage by remember {
        mutableStateOf<String?>(null)
    }

    // Store only the ID. Resolve the latest record from TripsStore.
    var pendingRemovalId by rememberSaveable(stateSaver = NullableTripIdSaver) {
        mutableStateOf<TripId?>(null)
    }

    var filterName by rememberSaveable { mutableStateOf(TripHistoryFilter.ALL.name) }
    val filter = TripHistoryFilter.entries.firstOrNull { it.name == filterName } ?: TripHistoryFilter.ALL

    // History lists completed trips and cancelled trips the user chose to keep.
    val historyRecords = if (cloud != null) {
        cloudRecords.filterNot { it.id in tripsStore.state.value.pendingFinalTripIds }
    } else {
        tripsStore.completedTrips + tripsStore.cancelledTrips
    }
    val pendingRemovalTrip = historyRecords.firstOrNull {
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
        tripId: TripId,
        callback: (TripId) -> Unit
    ) {
        if (!latestDestinationActive) return

        val trip = historyRecords.firstOrNull { it.id == tripId }
        if (trip == null) {
            errorMessage = "This trip is no longer available."
        } else {
            errorMessage = null
            if (cloud != null) tripsStore.acceptStoredTrip(trip)
            callback(tripId)
        }
    }

    fun requestRemoval(tripId: TripId) {
        if (!latestDestinationActive) return

        val trip = historyRecords.firstOrNull { it.id == tripId }
        if (trip == null) {
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
        val trip = historyRecords.firstOrNull { it.id == tripId }
        if (trip == null) {
            pendingRemovalId = null
            errorMessage = "This trip has already been removed."
            return
        }

        if (cloud != null) {
            if (removing) return
            removing = true
            scope.launch {
                try {
                    if (tryCloudAction {
                        cloud.mutate {
                            deleteTrip(tripId)
                            tripsStore.removeFromHistory(tripId)
                            cloudRecords = cloudRecords.filterNot { it.id == tripId }
                        }
                    }) {
                        pendingRemovalId = null
                        errorMessage = null
                    } else {
                        errorMessage = "Could not remove this trip. Please retry."
                    }
                } finally {
                    removing = false
                }
            }
            return
        }

        when (val result = tripsStore.removeFromHistory(tripId)) {
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

    val detailsCallback: ((TripId) -> Unit)? =
        onOpenDetails?.let { callback ->
            { tripId -> openExistingTrip(tripId, callback) }
        }

    val reviewCallback: ((TripId) -> Unit)? =
        onReview?.let { callback ->
            { tripId -> openExistingTrip(tripId, callback) }
        }

    Column(modifier) {
        if (loadFailed) {
            TextButton(onClick = { retry++ }) { Text("Could not load History. Retry") }
        }
        HistoryContent(
            trips = historyTrips(historyRecords, filter),
            filter = filter,
            totalCount = historyRecords.size,
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
            modifier = Modifier
        )
    }
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
                        "This cannot be undone." + if (errorMessage != null) "\n${errorMessage.orEmpty()}" else ""
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !removing,
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
