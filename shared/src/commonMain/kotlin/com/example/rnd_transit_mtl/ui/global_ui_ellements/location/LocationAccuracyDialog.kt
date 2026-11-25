package com.example.rnd_transit_mtl.ui.global_ui_ellements.location

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rnd_transit_mtl.data.location.LocationAccuracyState
import com.example.rnd_transit_mtl.data.location.LocationPermissionLevel
import com.example.rnd_transit_mtl.data.location.PlannerLocation
import com.example.rnd_transit_mtl.ui.global_ui_ellements.buttons.TransitOutlineButton
import com.example.rnd_transit_mtl.ui.theme.TransitComplementary
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitWhite
import kotlin.math.roundToInt

// Layout settings — dp dialog scroll height limit, section spacing, and button height.
private val LocationDialogMaxContentHeight = 420.dp
private val LocationDialogSectionSpacing = 12.dp
private val LocationDialogReadingSpacing = 4.dp
private val LocationDialogButtonHeight = 48.dp

/**
 * Modal diagnostics and troubleshooting dialog showing live permission, accuracy, and settings actions.
 *
 * @param location Shared planner location adapter supplying live accuracy readings, or null on non-Android hosts.
 * @param onDismiss Called when the user closes the dialog.
 */
@Composable
internal fun LocationAccuracyDialog(
    location: PlannerLocation?,
    onDismiss: () -> Unit,
) {
    val state = location?.accuracyMonitor?.state
    var headingHelp by rememberSaveable { mutableStateOf(false) }
    var settingsFailed by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TransitWhite,
        titleContentColor = TransitComplementary,
        textContentColor = TransitComplementary,
        title = { Text("Improve location accuracy", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                Modifier
                    .heightIn(max = LocationDialogMaxContentHeight)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(LocationDialogSectionSpacing),
            ) {
                if (state == null) {
                    Text("Live location controls are available in the Android app.")
                } else {
                    LocationDiagnosticsSummary(state)
                    if (state.permission != LocationPermissionLevel.Precise) {
                        Text("In app settings, choose Permissions, then Location, and enable Use precise location.")
                        TransitOutlineButton(
                            "Open app settings",
                            onClick = { settingsFailed = !location.openAppLocationSettings() },
                            height = LocationDialogButtonHeight,
                            fontSize = 14.sp,
                        )
                    }
                    Text(
                        "In device location settings, turn on Location and Location Accuracy when available. " +
                            "Wi-Fi and mobile signals can help your phone locate you.",
                    )
                    TransitOutlineButton(
                        "Open location settings",
                        onClick = { settingsFailed = !location.openDeviceLocationSettings() },
                        height = LocationDialogButtonHeight,
                        fontSize = 14.sp,
                    )
                    if (settingsFailed) {
                        Text("Couldn't open settings. Open your phone's Settings app and choose Location.")
                    }
                    Text(
                        "For a clearer signal, move outside or away from tall buildings. " +
                            "Your yellow circle shows the uncertainty around your position.",
                    )
                    TextButton(onClick = { headingHelp = !headingHelp }) {
                        Text(if (headingHelp) "Hide direction help" else "Direction looks wrong?", color = TransitMain)
                    }
                    if (headingHelp) {
                        Text(
                            "Away from metal and magnets, gently move your phone in a figure eight a few times to help calibrate its compass. " +
                                "This helps the direction it points; GPS and network signals determine your position.",
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = TransitMain) } },
    )
}

/** Renders the permission, device-location status, estimated accuracy, and last-reading age block. */
@Composable
private fun LocationDiagnosticsSummary(state: LocationAccuracyState) {
    Column(verticalArrangement = Arrangement.spacedBy(LocationDialogReadingSpacing)) {
        Text(locationPermissionLabel(state), fontWeight = FontWeight.Bold)
        Text(
            "Device location: " + when (state.locationEnabled) {
                true -> "On"
                false -> "Off"
                null -> "Checking"
            },
        )
        Text(
            "Estimated accuracy: " + (
                state.accuracyMeters?.let {
                    "about ${it.coerceAtMost(Int.MAX_VALUE.toDouble()).roundToInt()} m"
                } ?: "Waiting for a reading"
            ),
        )
        Text(
            "Last reading: " + when (val age = state.readingAgeSeconds) {
                null -> "No reading yet"
                0L -> "Just now"
                in 1L..59L -> "$age seconds ago"
                else -> "${age / 60L} minutes ago"
            },
        )
    }
}

/**
 * Formats the user-facing permission-level status line for [state].
 *
 * @param state Current location-accuracy snapshot.
 * @return Human-readable permission description.
 */
internal fun locationPermissionLabel(state: LocationAccuracyState): String =
    "Location permission: " + when (state.permission) {
        LocationPermissionLevel.Precise -> "Precise"
        LocationPermissionLevel.Approximate -> "Approximate"
        LocationPermissionLevel.Denied -> "Not allowed"
        LocationPermissionLevel.Unknown -> "Checking"
    }
