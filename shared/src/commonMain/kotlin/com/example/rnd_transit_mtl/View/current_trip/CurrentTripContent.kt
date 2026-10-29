package com.example.rnd_transit_mtl.View.current_trip

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitWhite
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.boundedTripProgress
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.formatDemoDistanceKm
import com.example.rnd_transit_mtl.ui.global_ui_ellements.trip.TripInformationPanel

// Layout settings — maximum fraction of the viewport used by scrollable trip information.
private const val TripInformationMaximumHeightFraction = 0.6f
/** Map and compact progress footer; trip information appears only in a popup.
 * @param instruction Optional routing or explicitly labelled sample instruction.
 * @param locationState Explicit sample, stale, or missing reading state.
 * @param staleMessage Optional explanation for a stale reading.
 */
@Composable
fun CurrentTripContent(
    trip: Trip,
    progress: Float,
    actionsEnabled: Boolean,
    message: String?,
    onLeave: () -> Unit,
    onCancel: () -> Unit,
    onRequestReview: () -> Unit,
    onFinishRequested: () -> Unit,
    onOpenImageReference: () -> Unit,
    modifier: Modifier = Modifier,
    showTripInformation: Boolean = false,
    onDismissInformation: () -> Unit = {},
    onShowInformation: () -> Unit = {},
    instruction: ActiveMapInstruction? = null,
    locationState: TripLocationState = TripLocationState.Sample,
    staleMessage: String? = null
) {
    val boundedProgress = boundedTripProgress(progress)
    val completed = boundedProgress == 1f
    BoxWithConstraints(modifier.fillMaxSize()) {
        val availableHeight = maxHeight
        ActiveMapContent(
            trip = trip,
            progress = boundedProgress,
            actionsEnabled = actionsEnabled,
            operationMessage = message,
            instruction = instruction,
            locationState = locationState,
            staleMessage = staleMessage,
            onFinish = { if (completed) onRequestReview() else onFinishRequested() },
            onConfirmCancel = onCancel,
            onShowInformation = onShowInformation,
            modifier = Modifier.fillMaxSize()
        )
        if (showTripInformation) {
            AlertDialog(
                onDismissRequest = onDismissInformation,
                containerColor = TransitWhite,
                titleContentColor = TransitMain,
                textContentColor = TransitMain,
                title = {
                    Text(trip.title, style = MaterialTheme.typography.titleLarge)
                },
                text = {
                    Column(
                        modifier = Modifier.heightIn(max = availableHeight * TripInformationMaximumHeightFraction)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "${formatDemoDistanceKm(trip.distanceKm)} km demo",
                            color = TransitMain
                        )
                        TripInformationPanel(
                            trip = trip,
                            onOpenImageReference = onOpenImageReference
                        )
                    }
                },
                confirmButton = {
                    // Trip cancellation is confirmed in the active-map panel.
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = onLeave, enabled = actionsEnabled) {
                                Text("Return to GO", color = TransitMain)
                            }
                            TextButton(onClick = onDismissInformation) {
                                Text("Close", color = TransitMain)
                            }
                        }
                    }
                }
            )
        }
    }
}
