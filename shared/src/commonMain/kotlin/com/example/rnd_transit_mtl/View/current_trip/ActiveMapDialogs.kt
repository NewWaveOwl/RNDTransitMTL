package com.example.rnd_transit_mtl.View.current_trip

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.rnd_transit_mtl.ui.theme.TransitComplementary
import com.example.rnd_transit_mtl.ui.theme.TransitWhite

// Layout settings — dp width limit, padding, and spacing for active-trip confirmation dialogs.
private val ActiveMapDialogMaximumWidth = 620.dp
private val ActiveMapDialogPadding = 24.dp
private val ActiveMapDialogSpacing = 16.dp

/**
 * Confirmation dialog shown before ending an active trip, with optional save-to-history choice.
 *
 * @param reference Short trip identifier suffix displayed in the dialog body.
 * @param alwaysSaveCancellation Whether cloud-backed sessions always persist cancelled trips.
 * @param actionsEnabled Whether confirmation actions accept clicks.
 * @param onConfirmCancel Called with whether to save the cancelled trip to History.
 * @param onDismiss Called when the user cancels the dialog or keeps travelling.
 */
@Composable
internal fun CancelTripConfirmationDialog(
    reference: String,
    alwaysSaveCancellation: Boolean,
    actionsEnabled: Boolean,
    onConfirmCancel: (keepInHistory: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = ActiveMapDialogMaximumWidth)
                .fillMaxWidth()
                .background(TransitWhite)
                .verticalScroll(rememberScrollState())
                .padding(ActiveMapDialogPadding),
            verticalArrangement = Arrangement.spacedBy(ActiveMapDialogSpacing),
        ) {
            Text(
                "Cancel this adventure?",
                color = TransitComplementary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            )

            Text(
                if (alwaysSaveCancellation) {
                    "This ends trip #$reference and saves it to your History."
                } else {
                    "This ends trip #$reference. Do you want to save it " +
                        "to your History? Your planner choices stay saved either way."
                },
                color = TransitComplementary,
                fontSize = 16.sp,
            )

            TripActionButton(
                label = "Cancel and save to History",
                enabled = actionsEnabled,
                onClick = { onConfirmCancel(true) },
            )

            if (!alwaysSaveCancellation) {
                TripActionButton(
                    label = "Cancel without saving",
                    enabled = actionsEnabled,
                    secondary = true,
                    onClick = { onConfirmCancel(false) },
                )
            }

            TripActionButton(
                label = "Keep travelling",
                secondary = true,
                onClick = onDismiss,
            )
        }
    }
}

/**
 * Confirmation dialog shown before manually completing a return trip or simulated sample trip.
 *
 * @param isReturnTrip Whether the active trip is a return leg back to the original start.
 * @param actionsEnabled Whether confirmation actions accept clicks.
 * @param onConfirmFinish Called when the user confirms trip completion.
 * @param onDismiss Called when the user cancels the dialog or keeps travelling.
 */
@Composable
internal fun FinishTripConfirmationDialog(
    isReturnTrip: Boolean,
    actionsEnabled: Boolean,
    onConfirmFinish: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .widthIn(max = ActiveMapDialogMaximumWidth)
                .fillMaxWidth()
                .background(TransitWhite)
                .verticalScroll(rememberScrollState())
                .padding(ActiveMapDialogPadding),
            verticalArrangement = Arrangement.spacedBy(ActiveMapDialogSpacing),
        ) {
            Text(
                if (isReturnTrip) "Back at your starting point?" else "Finish this adventure?",
                color = TransitComplementary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                if (isReturnTrip) {
                    "Confirm that you have arrived. Your outbound and return trips remain saved."
                } else {
                    "This marks the sample trip as completed."
                },
                color = TransitComplementary,
            )
            TripActionButton(
                label = if (isReturnTrip) "Yes, I'm back" else "Yes, finish trip",
                onClick = onConfirmFinish,
                enabled = actionsEnabled,
            )
            TripActionButton(
                label = "Keep travelling",
                onClick = onDismiss,
                secondary = true,
            )
        }
    }
}
