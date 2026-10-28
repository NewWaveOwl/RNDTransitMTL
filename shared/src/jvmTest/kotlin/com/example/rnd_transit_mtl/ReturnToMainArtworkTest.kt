package com.example.rnd_transit_mtl

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import com.example.rnd_transit_mtl.layout.SharedTopBarContent
import com.example.rnd_transit_mtl.layout.SharedBottomBar
import com.example.rnd_transit_mtl.layout.MainViewTab
import com.example.rnd_transit_mtl.navigation.MainScreenKey
import com.example.rnd_transit_mtl.navigation.ScreenKey
import com.example.rnd_transit_mtl.ui.theme.RNDTransitTheme
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Exercises native GO rendering and its real navigation click target. */
class ReturnToMainArtworkTest {
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun bottomBarDisablesUnimplementedViewsAndDispatchesMap() {
        var selected: MainViewTab? = null
        val scene = ImageComposeScene(380, 80) {
            RNDTransitTheme {
                SharedBottomBar(MainViewTab.Map, setOf(MainViewTab.Map), { selected = it })
            }
        }
        try {
            scene.render(0).use { image ->
                val out = File("build/planner-previews").apply { mkdirs() }
                image.encodeToData()!!.use { File(out, "Shared-bottom-bar.png").writeBytes(it.bytes) }
            }
            fun click(x: Float, time: Long) {
                scene.sendPointerEvent(PointerEventType.Press, Offset(x, 24f), timeMillis = time)
                scene.sendPointerEvent(PointerEventType.Release, Offset(x, 24f), timeMillis = time + 1)
            }
            click(190f, 1)
            click(316f, 3)
            assertNull(selected)
            click(63f, 5)
            assertEquals(MainViewTab.Map, selected)
        } finally { scene.close() }
    }

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
            scene.sendPointerEvent(PointerEventType.Press, Offset(48f, 24f), timeMillis = 1)
            scene.sendPointerEvent(PointerEventType.Release, Offset(48f, 24f), timeMillis = 2)
            assertEquals(MainScreenKey, destination)
            destination = null
            scene.sendPointerEvent(PointerEventType.Press, Offset(190f, 32f), timeMillis = 3)
            scene.sendPointerEvent(PointerEventType.Release, Offset(190f, 32f), timeMillis = 4)
            assertEquals(MainScreenKey, destination)
        } finally {
            scene.close()
        }
    }
}
