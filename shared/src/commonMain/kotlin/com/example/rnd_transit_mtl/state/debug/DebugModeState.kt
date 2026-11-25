package com.example.rnd_transit_mtl.state.debug

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.saveable.listSaver

/**
 * App-wide, restorable debug choice. Normal use never enables simulated progress.
 * @param simulationEnabled Whether the user explicitly selected trip simulation.
 * @param isModeChooserVisible Whether the logo's debug menu is open.
 */
internal class DebugModeState(
    simulationEnabled: Boolean = false,
    isModeChooserVisible: Boolean = false,
) {
    var simulationEnabled by mutableStateOf(simulationEnabled)
        private set
    var isModeChooserVisible by mutableStateOf(isModeChooserVisible)
        private set

    /** Opens the choice without changing the selected mode. */
    fun openModeChooser() {
        isModeChooserVisible = true
    }

    /** Dismisses the choice and preserves the selected mode. */
    fun dismissModeChooser() {
        isModeChooserVisible = false
    }

    /** @param enabled True for simulation; false for normal app use. */
    fun selectSimulation(enabled: Boolean) {
        simulationEnabled = enabled
        isModeChooserVisible = false
    }

    companion object {
        val Saver = listSaver<DebugModeState, Boolean>(
            save = { listOf(it.simulationEnabled, it.isModeChooserVisible) },
            restore = { DebugModeState(it[0], it[1]) },
        )
    }
}

/** Unhosted screens and previews use normal app behavior. */
internal val LocalDebugModeState = staticCompositionLocalOf<DebugModeState?> { null }
