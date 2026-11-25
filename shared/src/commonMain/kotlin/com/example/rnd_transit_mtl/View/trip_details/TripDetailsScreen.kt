package com.example.rnd_transit_mtl.View.trip_details

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import com.example.rnd_transit_mtl.View.history.formatTripTimestampUtc
import com.example.rnd_transit_mtl.data.trips.LocalCloudTrips
import com.example.rnd_transit_mtl.data.trips.tryCloudAction
import com.example.rnd_transit_mtl.model.ids.TripId
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.model.trip.TripStatus
import com.example.rnd_transit_mtl.navigation.LocalTripNavigation
import com.example.rnd_transit_mtl.state.trip.LocalTripsStore
import com.example.rnd_transit_mtl.ui.global_ui_ellements.trip.TripDetailsV3
import kotlinx.coroutines.launch

// Layout settings — delegated to TripDetailsV3.kt and TripDetailsContent.kt.

/**
 * Resolves the latest saved record by stable ID and coordinates cloud edit/delete dialogs.
 * No trip is generated, appended, or reconstructed from a route snapshot.
 *
 * @param tripId Stable identifier of the trip being inspected.
 * @param onReturnToHistory Navigates back to History when the trip is missing or cancelled.
 * @param onReview Optional callback opening the review editor for [tripId].
 * @param isDestinationActive True only while this details entry is on top of the back stack.
 * @param modifier Parent layout constraints.
 */
@Composable
fun TripDetailsScreen(
    tripId: TripId,
    onReturnToHistory: () -> Unit,
    onReview: ((TripId) -> Unit)? = null,
    isDestinationActive: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val tripsStore = LocalTripsStore.current
    val uriHandler = LocalUriHandler.current
    val cloud = LocalCloudTrips.current
    val scope = rememberCoroutineScope()
    var storedTrip by remember(cloud, tripId) { mutableStateOf<Trip?>(null) }
    var loading by remember(cloud, tripId) { mutableStateOf(cloud != null) }
    var loadFailed by remember(cloud, tripId) { mutableStateOf(false) }
    var retry by remember { mutableStateOf(0) }
    var edit by remember(tripId) { mutableStateOf(false) }
    var confirmingReviewDeletion by remember(tripId) { mutableStateOf(false) }
    var title by remember(tripId) { mutableStateOf("") }
    var description by remember(tripId) { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(cloud, tripId, cloud?.revision, isDestinationActive, retry) {
        if (cloud != null && isDestinationActive) {
            loading = storedTrip == null
            loadFailed = !tryCloudAction { storedTrip = cloud.details(tripId) }
            loading = false
        }
    }

    if (cloud != null && (loading || loadFailed)) {
        TripDetailsLoadStateView(
            loading = loading,
            loadFailed = loadFailed,
            onRetry = { retry++ },
            onReturnToHistory = onReturnToHistory,
            modifier = modifier,
        )
        return
    }

    val snapshot by tripsStore.state
    val trip = if (cloud != null) {
        storedTrip
    } else {
        snapshot.completedTrips.firstOrNull { it.id == tripId }
            ?: snapshot.cancelledTrips.firstOrNull { it.id == tripId }
    }

    val latestDestinationActive by rememberUpdatedState(isDestinationActive)
    var errorMessage by remember(tripId) { mutableStateOf<String?>(null) }
    val reviewCallback: (() -> Unit)? = onReview?.let { callback ->
        {
            if (latestDestinationActive) {
                if (tripsStore.findCompleted(tripId) == null) {
                    errorMessage = "This trip is no longer available."
                } else {
                    errorMessage = null
                    callback(tripId)
                }
            }
        }
    }

    val navigation = LocalTripNavigation.current
    if (trip != null) {
        fun saveChanges() {
            if (cloud == null || saving) return
            saving = true
            scope.launch {
                try {
                    if (tryCloudAction {
                            cloud.flushFinalTrip(tripId)
                            cloud.mutate {
                                if (edit) check(updateTrip(tripId, title, description)) { "Trip unavailable." }
                                else deleteReview(tripId)
                            }
                        }
                    ) {
                        edit = false
                        confirmingReviewDeletion = false
                        errorMessage = null
                    } else {
                        errorMessage = "Could not save changes. Please retry."
                    }
                } finally {
                    saving = false
                }
            }
        }

        Column(modifier) {
            if (cloud != null) {
                Text("Trip ID ${trip.id.value}")
                Row {
                    TextButton(
                        enabled = isDestinationActive,
                        onClick = {
                            title = trip.title
                            description = trip.description
                            errorMessage = null
                            edit = true
                        },
                    ) { Text("Edit trip") }
                    if (trip.review != null) {
                        TextButton(
                            enabled = isDestinationActive,
                            onClick = {
                                errorMessage = null
                                confirmingReviewDeletion = true
                            },
                        ) { Text("Delete review") }
                    }
                }
            }
            TripDetailsV3(
                trip = trip,
                completedLabel = formatTripTimestampUtc(trip.completedAt ?: trip.cancelledAt ?: trip.createdAt),
                onRepeat = { if (latestDestinationActive) navigation.openPlannerCopy(tripId, true) },
                onEditCopy = { if (latestDestinationActive) navigation.openPlannerCopy(tripId, false) },
                onReview = reviewCallback.takeIf { trip.status == TripStatus.Completed },
                enabled = isDestinationActive,
                errorMessage = errorMessage,
            )
        }

        if (isDestinationActive && (edit || confirmingReviewDeletion)) {
            TripDetailsEditOrDeleteDialog(
                editingTrip = edit,
                title = title,
                description = description,
                saving = saving,
                errorMessage = errorMessage,
                onTitleChange = { title = it },
                onDescriptionChange = { description = it },
                onConfirm = { saveChanges() },
                onDismiss = {
                    if (!saving) {
                        edit = false
                        confirmingReviewDeletion = false
                        errorMessage = null
                    }
                },
            )
        }
        return
    }

    TripDetailsContent(
        trip = null,
        onReturnToHistory = { if (latestDestinationActive) onReturnToHistory() },
        onReview = reviewCallback,
        onOpenImageReference = {
            if (latestDestinationActive) {
                val latestTrip = tripsStore.findCompleted(tripId)
                if (latestTrip == null) {
                    errorMessage = "This trip is no longer available."
                } else {
                    try {
                        uriHandler.openUri(latestTrip.imageUrl)
                        errorMessage = null
                    } catch (_: Exception) {
                        errorMessage = "The image reference could not be opened. " +
                            "You can copy its URL from Trip information."
                    }
                }
            }
        },
        actionsEnabled = isDestinationActive,
        errorMessage = errorMessage,
        modifier = modifier,
    )
}

/** Loading or retry fallback shown while fetching cloud trip details. */
@Composable
private fun TripDetailsLoadStateView(
    loading: Boolean,
    loadFailed: Boolean,
    onRetry: () -> Unit,
    onReturnToHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(if (loading) "Loading trip…" else "Could not load trip details.")
        if (loadFailed) TextButton(onClick = onRetry) { Text("Retry") }
        TextButton(onClick = onReturnToHistory) { Text("Return to History") }
    }
}

/** Modal dialog supporting either editing a saved trip's metadata or confirming review deletion. */
@Composable
private fun TripDetailsEditOrDeleteDialog(
    editingTrip: Boolean,
    title: String,
    description: String,
    saving: Boolean,
    errorMessage: String?,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editingTrip) "Edit trip" else "Delete this review?") },
        text = {
            Column {
                if (editingTrip) {
                    OutlinedTextField(title, onTitleChange, label = { Text("Title") }, enabled = !saving)
                    OutlinedTextField(description, onDescriptionChange, label = { Text("Description") }, enabled = !saving)
                }
                errorMessage?.let { Text(it) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving && (!editingTrip || (title.isNotBlank() && description.isNotBlank())),
                onClick = onConfirm,
            ) { Text(if (editingTrip) "Save" else "Delete review") }
        },
        dismissButton = {
            TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancel") }
        },
    )
}
