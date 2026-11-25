package com.example.rnd_transit_mtl.ui.global_ui_ellements.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rnd_transit_mtl.state.debug.DebugModeState
import com.example.rnd_transit_mtl.state.debug.LocalDebugModeState
import com.example.rnd_transit_mtl.ui.global_ui_ellements.buttons.TransitFilledButton
import com.example.rnd_transit_mtl.ui.global_ui_ellements.buttons.TransitOutlineButton
import com.example.rnd_transit_mtl.ui.theme.TransitComplementary
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitWhite
import kotlinx.coroutines.delay

// Layout settings — dp values and readable action labels shared by both choices.
private val DebugDialogCornerRadius = 24.dp
private val DebugDialogActionHeight = 52.dp
private val DebugDialogActionSpacing = 12.dp
private val DebugDialogActionFontSize = 16.sp

/**
 * Opens debug mode after three consecutive logo clicks within the platform tap timeout.
 * Defers the ordinary logo action so a triple tap does not navigate away from a trip.
 * @param resetKey Cancels a pending tap sequence when the destination changes.
 * @param onSingleClick Ordinary logo action, invoked when the tap sequence expires.
 */
@Composable
internal fun rememberDebugLogoClick(resetKey: Any? = Unit, onSingleClick: () -> Unit = {}): () -> Unit {
    val debugMode = LocalDebugModeState.current ?: return onSingleClick
    val latestSingleClick by rememberUpdatedState(onSingleClick)
    val tapTimeoutMillis = LocalViewConfiguration.current.doubleTapTimeoutMillis
    var tapCount by remember(debugMode, resetKey) { mutableIntStateOf(0) }

    LaunchedEffect(debugMode, resetKey, tapCount) {
        if (tapCount > 0) {
            delay(tapTimeoutMillis)
            tapCount = 0
            latestSingleClick()
        }
    }

    return {
        if (tapCount == 2) {
            tapCount = 0
            debugMode.openModeChooser()
        } else {
            tapCount++
        }
    }
}

/**
 * Offers opt-in playback while keeping normal use the default.
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
                    "Trip simulation", onClick = { state.selectSimulation(true) },
                    height = DebugDialogActionHeight, fontSize = DebugDialogActionFontSize,
                )
                TransitOutlineButton(
                    "Normal app use", onClick = { state.selectSimulation(false) },
                    height = DebugDialogActionHeight, fontSize = DebugDialogActionFontSize,
                )
            }
        },
    )
}
