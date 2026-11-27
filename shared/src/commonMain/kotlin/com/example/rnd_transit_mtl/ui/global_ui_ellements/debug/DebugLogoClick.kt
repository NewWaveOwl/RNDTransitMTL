package com.example.rnd_transit_mtl.ui.global_ui_ellements.debug

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalViewConfiguration
import com.example.rnd_transit_mtl.state.debug.LocalDebugModeState
import kotlinx.coroutines.delay

// Interaction settings — triple-tap threshold; timeout comes from LocalViewConfiguration.
private const val DebugModeTriggerTapCount = 3

/**
 * Opens debug mode after three consecutive logo clicks within the platform tap timeout.
 * Defers the ordinary logo action so a triple tap does not navigate away from a trip.
 *
 * @param resetKey Cancels a pending tap sequence when the destination changes.
 * @param onSingleClick Ordinary logo action invoked when the tap sequence expires.
 * @return Click handler suitable for the header or sign-in logo control.
 */
@Composable
internal fun rememberDebugLogoClick(
    resetKey: Any? = Unit,
    onSingleClick: () -> Unit = {},
): () -> Unit {
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
        if (tapCount == DebugModeTriggerTapCount - 1) {
            tapCount = 0
            debugMode.openModeChooser()
        } else {
            tapCount++
        }
    }
}
