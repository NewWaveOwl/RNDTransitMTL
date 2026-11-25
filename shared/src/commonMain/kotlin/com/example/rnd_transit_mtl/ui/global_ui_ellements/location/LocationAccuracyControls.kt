package com.example.rnd_transit_mtl.ui.global_ui_ellements.location

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.lerp
import com.example.rnd_transit_mtl.data.location.LocalPlannerLocation
import com.example.rnd_transit_mtl.data.location.LocationAccuracyPrompt
import com.example.rnd_transit_mtl.data.location.LocationAccuracyState
import com.example.rnd_transit_mtl.data.location.LocationPermissionLevel
import com.example.rnd_transit_mtl.data.location.PlannerLocation
import com.example.rnd_transit_mtl.state.debug.LocalDebugModeState
import com.example.rnd_transit_mtl.ui.global_ui_ellements.buttons.TransitOutlineButton
import com.example.rnd_transit_mtl.ui.theme.TransitComplementary
import com.example.rnd_transit_mtl.ui.theme.TransitHighlight
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitWhite
import kotlin.math.roundToInt

/** Settings and map notices open the same live diagnostics without starting another location source. */
@Composable
internal fun LocationAccuracySettingsEntry() {
    val location = LocalPlannerLocation.current
    var opened by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Location accuracy", color = TransitComplementary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(location?.accuracyMonitor?.state?.let(::permissionLabel) ?: "Live location is available on Android.",
            color = TransitComplementary, fontSize = 14.sp)
        TransitOutlineButton("Improve location accuracy", { opened = true }, height = 48.dp, fontSize = 15.sp)
    }
    if (opened) LocationAccuracyDialog(location) { opened = false }
}

/** A dismissible inline hint. It never blocks navigation or appears during trip simulation. */
@Composable
internal fun LocationAccuracyNotice(modifier: Modifier = Modifier, enabled: Boolean = true) {
    val location = LocalPlannerLocation.current ?: return
    val monitor = location.accuracyMonitor ?: return
    if (!enabled || LocalDebugModeState.current?.simulationEnabled == true) return
    var opened by rememberSaveable { mutableStateOf(false) }
    var settingsFailed by remember { mutableStateOf(false) }
    val prompt = monitor.state.prompt
    if (prompt != null) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            color = lerp(TransitWhite, TransitHighlight, 0.15f),
            contentColor = TransitComplementary,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, TransitHighlight),
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                Text(when (prompt) {
                    LocationAccuracyPrompt.PrecisePermission -> "Precise location is disabled"
                    LocationAccuracyPrompt.WeakSignal -> "Location accuracy is low"
                    LocationAccuracyPrompt.LocationDisabled -> "Device location is turned off"
                }, color = TransitComplementary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                if (settingsFailed) Text("Couldn't open settings. Open your phone's Settings app.",
                    color = TransitComplementary, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        settingsFailed = false
                        when (prompt) {
                            LocationAccuracyPrompt.WeakSignal -> opened = true
                            LocationAccuracyPrompt.PrecisePermission -> settingsFailed = !location.openAppLocationSettings()
                            LocationAccuracyPrompt.LocationDisabled -> settingsFailed = !location.openDeviceLocationSettings()
                        }
                    }) { Text(if (prompt == LocationAccuracyPrompt.WeakSignal) "Improve accuracy" else "Open settings", color = TransitMain) }
                    TextButton(onClick = { settingsFailed = false; monitor.dismissPrompt() }) {
                        Text("Dismiss", color = TransitComplementary)
                    }
                }
            }
        }
    }
    // Diagnostics remain open if the notice clears while the user is reading them.
    if (opened) LocationAccuracyDialog(location) { opened = false }
}

@Composable
internal fun LocationAccuracyDialog(location: PlannerLocation?, onDismiss: () -> Unit) {
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
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state == null) {
                    Text("Live location controls are available in the Android app.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(permissionLabel(state), fontWeight = FontWeight.Bold)
                        Text("Device location: " + when (state.locationEnabled) {
                            true -> "On"
                            false -> "Off"
                            null -> "Checking"
                        })
                        Text("Estimated accuracy: " + (state.accuracyMeters?.let {
                            "about ${it.coerceAtMost(Int.MAX_VALUE.toDouble()).roundToInt()} m"
                        } ?: "Waiting for a reading"))
                        Text("Last reading: " + when (val age = state.readingAgeSeconds) {
                            null -> "No reading yet"
                            0L -> "Just now"
                            in 1L..59L -> "$age seconds ago"
                            else -> "${age / 60L} minutes ago"
                        })
                    }
                    if (state.permission != LocationPermissionLevel.Precise) {
                        Text("In app settings, choose Permissions, then Location, and enable Use precise location.")
                        TransitOutlineButton("Open app settings", { settingsFailed = !location.openAppLocationSettings() },
                            height = 48.dp, fontSize = 14.sp)
                    }
                    Text("In device location settings, turn on Location and Location Accuracy when available. Wi-Fi and mobile signals can help your phone locate you.")
                    TransitOutlineButton("Open location settings", { settingsFailed = !location.openDeviceLocationSettings() },
                        height = 48.dp, fontSize = 14.sp)
                    if (settingsFailed) Text("Couldn't open settings. Open your phone's Settings app and choose Location.")
                    Text("For a clearer signal, move outside or away from tall buildings. Your yellow circle shows the uncertainty around your position.")
                    TextButton(onClick = { headingHelp = !headingHelp }) {
                        Text(if (headingHelp) "Hide direction help" else "Direction looks wrong?", color = TransitMain)
                    }
                    if (headingHelp) Text("Away from metal and magnets, gently move your phone in a figure eight a few times to help calibrate its compass. This helps the direction it points; GPS and network signals determine your position.")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = TransitMain) } },
    )
}

private fun permissionLabel(state: LocationAccuracyState): String = "Location permission: " + when (state.permission) {
    LocationPermissionLevel.Precise -> "Precise"
    LocationPermissionLevel.Approximate -> "Approximate"
    LocationPermissionLevel.Denied -> "Not allowed"
    LocationPermissionLevel.Unknown -> "Checking"
}
