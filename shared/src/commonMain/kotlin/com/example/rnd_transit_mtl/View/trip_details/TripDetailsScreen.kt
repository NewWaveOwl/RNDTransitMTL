package com.example.rnd_transit_mtl.View.trip_details
import com.example.rnd_transit_mtl.model.ids.TripId

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import com.example.rnd_transit_mtl.data.trips.LocalCloudTrips
import com.example.rnd_transit_mtl.data.trips.tryCloudAction
import com.example.rnd_transit_mtl.model.trip.Trip
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import com.example.rnd_transit_mtl.state.trip.LocalTripsStore
import com.example.rnd_transit_mtl.navigation.LocalTripNavigation
import com.example.rnd_transit_mtl.ui.global_ui_ellements.trip.TripDetailsV3
import com.example.rnd_transit_mtl.View.history.formatTripTimestampUtc
import com.example.rnd_transit_mtl.model.trip.TripStatus

/**
 * Resolves the latest completed record by stable ID.
 *
 * No trip is generated, appended, or reconstructed from a route snapshot.
 * No simulation effect is created.
 *
 * Review navigation should use TripReviewMode.EDIT with this details
 * entry as its origin. If this record is subsequently deleted, the
 * review navigation owner should recover to History.
 */
@Composable
fun TripDetailsScreen(
    tripId: TripId,
    onReturnToHistory: () -> Unit,
    onReview: ((TripId) -> Unit)? = null,
    isDestinationActive: Boolean = true,
    modifier: Modifier = Modifier
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
        Column(modifier) {
            Text(if (loading) "Loading trip…" else "Could not load trip details.")
            if (loadFailed) TextButton(onClick = { retry++ }) { Text("Retry") }
            TextButton(onClick = onReturnToHistory) { Text("Return to History") }
        }
        return
    }

    val snapshot by tripsStore.state
    val trip = if (cloud != null) storedTrip else snapshot.completedTrips.firstOrNull {
        it.id == tripId
    } ?: snapshot.cancelledTrips.firstOrNull { it.id == tripId }

    val latestDestinationActive by rememberUpdatedState(
        isDestinationActive
    )

    var errorMessage by remember(tripId) {
        mutableStateOf<String?>(null)
    }

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
                        cloud.mutate {
                            if (edit) {
                                check(updateTrip(tripId, title, description)) { "Trip unavailable." }
                            } else {
                                deleteReview(tripId)
                            }
                        }
                    }) {
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
                    TextButton(enabled = isDestinationActive, onClick = {
                        title = trip.title
                        description = trip.description
                        errorMessage = null
                        edit = true
                    }) { Text("Edit trip") }
                    if (trip.review != null) {
                        TextButton(enabled = isDestinationActive, onClick = {
                            errorMessage = null
                            confirmingReviewDeletion = true
                        }) { Text("Delete review") }
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
                errorMessage = errorMessage
            )
        }
        if (isDestinationActive && (edit || confirmingReviewDeletion)) {
            AlertDialog(
                onDismissRequest = {
                    if (!saving) {
                        edit = false
                        confirmingReviewDeletion = false
                    }
                },
                title = { Text(if (edit) "Edit trip" else "Delete this review?") },
                text = {
                    Column {
                        if (edit) {
                            OutlinedTextField(title, { title = it }, label = { Text("Title") }, enabled = !saving)
                            OutlinedTextField(description, { description = it }, label = { Text("Description") }, enabled = !saving)
                        }
                        errorMessage?.let { Text(it) }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = !saving && (!edit || (title.isNotBlank() && description.isNotBlank())),
                        onClick = { saveChanges() }
                    ) { Text(if (edit) "Save" else "Delete review") }
                },
                dismissButton = {
                    TextButton(enabled = !saving, onClick = {
                        edit = false
                        confirmingReviewDeletion = false
                        errorMessage = null
                    }) { Text("Cancel") }
                }
            )
        }
        return
    }
    TripDetailsContent(
        trip = trip,
        onReturnToHistory = {
            if (latestDestinationActive) {
                onReturnToHistory()
            }
        },
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
                        errorMessage =
                            "The image reference could not be opened. " +
                                "You can copy its URL from Trip information."
                    }
                }
            }
        },
        actionsEnabled = isDestinationActive,
        errorMessage = errorMessage,
        modifier = modifier
    )
}
