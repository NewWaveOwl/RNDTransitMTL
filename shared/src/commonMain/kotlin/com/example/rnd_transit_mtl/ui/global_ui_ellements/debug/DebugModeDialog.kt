package com.example.rnd_transit_mtl.ui.global_ui_ellements.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rnd_transit_mtl.state.debug.DebugModeState
import com.example.rnd_transit_mtl.ui.global_ui_ellements.buttons.TransitFilledButton
import com.example.rnd_transit_mtl.ui.global_ui_ellements.buttons.TransitOutlineButton
import com.example.rnd_transit_mtl.ui.theme.TransitComplementary
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitWhite

// Layout settings — dp values and readable action labels shared by both choices.
private val DebugDialogCornerRadius = 24.dp
private val DebugDialogActionHeight = 52.dp
private val DebugDialogActionSpacing = 12.dp
private val DebugDialogActionFontSize = 16.sp

/**
 * Offers opt-in trip simulation playback while keeping normal app use the default.
 *
 * @param state App-scoped selection shared by the logo and active-trip coordinator.
 */
@Composable
internal fun DebugModeDialog(state: DebugModeState) {
    if (!state.isModeChooserVisible) return

    AlertDialog(
        onDismissRequest = state::dismissModeChooser,
        containerColor = TransitWhite,
        titleContentColor = TransitMain,
        textContentColor = TransitComplementary,
        shape = RoundedCornerShape(DebugDialogCornerRadius),
        title = { Text("Debug mode", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "Choose how to use the app. Trip simulation plays routes automatically. " +
                    "Current mode: " + if (state.simulationEnabled) "Trip simulation" else "Normal app use",
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(DebugDialogActionSpacing)) {
                TransitFilledButton(
                    "Trip simulation",
                    onClick = { state.selectSimulation(true) },
                    height = DebugDialogActionHeight,
                    fontSize = DebugDialogActionFontSize,
                )
                TransitOutlineButton(
                    "Normal app use",
                    onClick = { state.selectSimulation(false) },
                    height = DebugDialogActionHeight,
                    fontSize = DebugDialogActionFontSize,
                )
            }
        },
    )
}
