package com.example.rnd_transit_mtl.ui.global_ui_ellements.location

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rnd_transit_mtl.data.location.LocalPlannerLocation
import com.example.rnd_transit_mtl.data.location.LocationAccuracyPrompt
import com.example.rnd_transit_mtl.state.debug.LocalDebugModeState
import com.example.rnd_transit_mtl.ui.theme.TransitComplementary
import com.example.rnd_transit_mtl.ui.theme.TransitHighlight
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitWhite

// Layout settings — dp corner radius, padding, and action spacing for the inline accuracy banner.
private val LocationNoticeCornerRadius = 14.dp
private val LocationNoticeHorizontalPadding = 12.dp
private val LocationNoticeVerticalPadding = 4.dp
private val LocationNoticeActionSpacing = 8.dp

/**
 * Dismissible inline location-accuracy hint shown on map screens.
 * Never blocks navigation or appears during debug trip simulation.
 *
 * @param modifier Placement and padding constraints supplied by the map overlay.
 * @param enabled Whether the notice can display and respond to clicks.
 */
@Composable
internal fun LocationAccuracyNotice(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
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
            shape = RoundedCornerShape(LocationNoticeCornerRadius),
            border = BorderStroke(1.dp, TransitHighlight),
        ) {
            Column(
                Modifier.padding(
                    horizontal = LocationNoticeHorizontalPadding,
                    vertical = LocationNoticeVerticalPadding,
                ),
            ) {
                Text(
                    when (prompt) {
                        LocationAccuracyPrompt.PrecisePermission -> "Precise location is disabled"
                        LocationAccuracyPrompt.WeakSignal -> "Location accuracy is low"
                        LocationAccuracyPrompt.LocationDisabled -> "Device location is turned off"
                    },
                    color = TransitComplementary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (settingsFailed) {
                    Text(
                        "Couldn't open settings. Open your phone's Settings app.",
                        color = TransitComplementary,
                        fontSize = 12.sp,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(LocationNoticeActionSpacing)) {
                    TextButton(
                        onClick = {
                            settingsFailed = false
                            when (prompt) {
                                LocationAccuracyPrompt.WeakSignal -> opened = true
                                LocationAccuracyPrompt.PrecisePermission ->
                                    settingsFailed = !location.openAppLocationSettings()
                                LocationAccuracyPrompt.LocationDisabled ->
                                    settingsFailed = !location.openDeviceLocationSettings()
                            }
                        },
                    ) {
                        Text(
                            if (prompt == LocationAccuracyPrompt.WeakSignal) "Improve accuracy" else "Open settings",
                            color = TransitMain,
                        )
                    }
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
