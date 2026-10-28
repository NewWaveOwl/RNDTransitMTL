package com.example.rnd_transit_mtl

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import com.example.rnd_transit_mtl.layout.SharedTopBarContent
import com.example.rnd_transit_mtl.navigation.MainScreenKey
import com.example.rnd_transit_mtl.navigation.ScreenKey
import com.example.rnd_transit_mtl.ui.theme.RNDTransitTheme
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** Exercises native GO rendering and its real navigation click target. */
class ReturnToMainArtworkTest {
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun returnArtworkRendersAndKeepsMainNavigationAction() {
        var destination: ScreenKey? = null
        val scene = ImageComposeScene(380, 100) {
            RNDTransitTheme {
                SharedTopBarContent(MainScreenKey, false, {}, { destination = it })
            }
        }
        try {
            scene.render(0).use { image ->
                val out = File("build/planner-previews").apply { mkdirs() }
                image.encodeToData()!!.use { File(out, "Return-to-Main-header.png").writeBytes(it.bytes) }
            }
            scene.sendPointerEvent(PointerEventType.Press, Offset(48f, 40f), timeMillis = 1)
            scene.sendPointerEvent(PointerEventType.Release, Offset(48f, 40f), timeMillis = 2)
            assertEquals(MainScreenKey, destination)
        } finally {
            scene.close()
        }
    }
}
