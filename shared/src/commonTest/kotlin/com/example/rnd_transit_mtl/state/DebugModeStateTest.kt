package com.example.rnd_transit_mtl.state

import androidx.compose.runtime.saveable.SaverScope
import com.example.rnd_transit_mtl.state.debug.DebugModeState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Guards normal defaults and restoration of the user's explicit playback choice. */
class DebugModeStateTest {
    @Test
    fun recreationKeepsTheModeAndDismissingTheChooserDoesNotChangeIt() {
        val original = DebugModeState()
        assertFalse(original.simulationEnabled)
        original.selectSimulation(true)
        original.openModeChooser()

        val scope = object : SaverScope {
            override fun canBeSaved(value: Any) = true
        }
        val saved = with(DebugModeState.Saver) { scope.save(original) }
        val restored = assertNotNull(DebugModeState.Saver.restore(assertNotNull(saved)))
        assertTrue(restored.simulationEnabled)
        assertTrue(restored.isModeChooserVisible)
        restored.dismissModeChooser()
        assertTrue(restored.simulationEnabled)
        assertFalse(restored.isModeChooserVisible)
        restored.selectSimulation(false)
        assertFalse(restored.simulationEnabled)
    }
}
