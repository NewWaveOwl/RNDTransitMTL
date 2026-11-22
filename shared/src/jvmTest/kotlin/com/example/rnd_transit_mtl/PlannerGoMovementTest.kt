package com.example.rnd_transit_mtl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.dp
import com.example.rnd_transit_mtl.View.main.FigmaOriginMap
import com.example.rnd_transit_mtl.View.main.MainPlannerContent
import com.example.rnd_transit_mtl.model.ids.TransportId
import com.example.rnd_transit_mtl.model.trip.TripPoint
import com.example.rnd_transit_mtl.ui.theme.RNDTransitTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import java.io.File
import kotlin.test.*

/** Exercises bounded GO movement alongside the existing minute, action and map gestures. */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class PlannerGoMovementTest {
    @Test
    fun movingGoKeepsItsControlsIndependentAndReachableAfterResize() = runBlocking(Dispatchers.Main) {
        var width by mutableStateOf(412)
        var height by mutableStateOf(800)
        var enabled by mutableStateOf(true)
        var minutes by mutableStateOf(30)
        var goActions = 0
        val scene = ImageComposeScene(1000, 900) {
            RNDTransitTheme {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
                    MainPlannerContent(minutes, { minutes = it }, emptyList(), listOf(TransportId("walk")),
                        emptyMap(), {}, { _, _ -> }, "Amble", 18, {}, emptyList(), {}, {}, "", "GO",
                        true, enabled, null, null, { goActions++ }, {},
                        modifier = Modifier.size(width.dp, height.dp))
                }
            }
        }
        var time = 0L
        suspend fun render() {
            repeat(30) { yield(); time += 16_000_000; scene.render(time).close() }
        }
        fun all(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::all)
        fun nodes() = scene.semanticsOwners.flatMap { all(it.rootSemanticsNode) }
        fun control(description: String) = nodes().first {
            it.config.getOrNull(SemanticsProperties.ContentDescription)?.any { text -> text.startsWith(description) } == true
        }
        fun text(label: String) = nodes().first {
            it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == label } == true
        }
        fun action(label: String): Boolean = control("Move GO box").config[SemanticsActions.CustomActions]
            .first { it.label == label }.action()
        fun tap(point: Offset) {
            scene.sendPointerEvent(PointerEventType.Press, point, timeMillis = time / 1_000_000)
            scene.sendPointerEvent(PointerEventType.Release, point, timeMillis = time / 1_000_000 + 1)
        }
        suspend fun drag(from: Offset, amount: Offset) {
            scene.sendPointerEvent(PointerEventType.Press, from, timeMillis = time / 1_000_000)
            repeat(4) { index ->
                scene.sendPointerEvent(PointerEventType.Move, from + amount * ((index + 1) / 4f),
                    timeMillis = time / 1_000_000 + 10)
                render()
            }
            scene.sendPointerEvent(PointerEventType.Release, from + amount, timeMillis = time / 1_000_000 + 1)
            render()
        }
        try {
            render()
            val original = control("Move GO box").boundsInRoot
            val sheetTop = control("Trip preferences").boundsInRoot.top
            drag(text("I have").boundsInRoot.center, Offset(-20f, -100f))
            val moved = control("Move GO box").boundsInRoot
            assertTrue(moved.top < original.top - 40f, "GO should move independently: $original -> $moved")
            assertEquals(sheetTop, control("Trip preferences").boundsInRoot.top, 1f)
            assertEquals(30, minutes)
            assertEquals(0, goActions)

            drag(control("Planned time:").boundsInRoot.center, Offset(0f, -75f))
            assertTrue(minutes > 30)
            assertEquals(moved, control("Move GO box").boundsInRoot)
            val previousMinutes = minutes
            tap(control("Increase planned time").boundsInRoot.center)
            render()
            assertTrue(minutes > previousMinutes)
            tap(control("GO").boundsInRoot.center)
            render()
            assertEquals(1, goActions)
            assertTrue(action("Move GO up"))
            render()
            assertTrue(control("Move GO box").boundsInRoot.top < moved.top)
            val beforeKey = control("Move GO box").boundsInRoot.top
            assertTrue(control("Move GO box").config[SemanticsActions.RequestFocus].action!!.invoke())
            assertTrue(scene.sendKeyEvent(KeyEvent(Key.DirectionDown, KeyEventType.KeyDown)))
            render()
            assertTrue(control("Move GO box").boundsInRoot.top > beforeKey)
            assertTrue(scene.sendKeyEvent(KeyEvent(Key.MoveHome, KeyEventType.KeyDown)))
            render()
            assertEquals(original, control("Move GO box").boundsInRoot)
            assertTrue(action("Move GO up"))
            render()

            for ((newWidth, newHeight) in listOf(720 to 300, 320 to 480, 320 to 160, 412 to 800)) {
                width = newWidth
                height = newHeight
                render()
                val bounds = control("Move GO box").boundsInRoot
                assertTrue(bounds.left >= 0f && bounds.top >= 0f, "$newWidth x $newHeight: $bounds")
                assertTrue(bounds.right <= width + 1f && bounds.bottom <= height + 1f, "$newWidth x $newHeight: $bounds")
                tap(control("GO").boundsInRoot.center)
                render()
                if (newHeight == 160) scene.render(time + 1).use { image ->
                    val folder = File("build/planner-previews").apply { mkdirs() }
                    image.encodeToData()!!.use { File(folder, "GO-short-window.png").writeBytes(it.bytes) }
                }
            }
            assertEquals(5, goActions)
            assertTrue(action("Reset GO position"))
            render()
            assertEquals(original, control("Move GO box").boundsInRoot)
            enabled = false
            render()
            assertFalse(action("Move GO up"))
            assertFalse(action("Reset GO position"))
            drag(text("I have").boundsInRoot.center, Offset(0f, -100f))
            assertEquals(original, control("Move GO box").boundsInRoot)
            scene.render(time + 1).use { image ->
                val folder = File("build/planner-previews").apply { mkdirs() }
                image.encodeToData()!!.use { File(folder, "GO-movable.png").writeBytes(it.bytes) }
            }
        } finally { scene.close() }
    }

    @Test
    fun panningOrHoldingTheBrowsingMapDoesNotStageAPoint() = runBlocking(Dispatchers.Main) {
        var picked: TripPoint? = null
        val scene = ImageComposeScene(412, 716) {
            RNDTransitTheme {
                FigmaOriginMap(null, Modifier.fillMaxSize(), onTap = { picked = it })
            }
        }
        var time = 0L
        suspend fun render() {
            repeat(4) { yield(); time += 16_000_000; scene.render(time).close() }
        }
        try {
            render()
            scene.sendPointerEvent(PointerEventType.Press, Offset(100f, 150f), timeMillis = 100)
            scene.sendPointerEvent(PointerEventType.Move, Offset(150f, 180f), timeMillis = 150)
            scene.sendPointerEvent(PointerEventType.Move, Offset(220f, 190f), timeMillis = 200)
            scene.sendPointerEvent(PointerEventType.Release, Offset(220f, 190f), timeMillis = 201)
            render()
            assertNull(picked)
            scene.sendPointerEvent(PointerEventType.Press, Offset(100f, 150f), timeMillis = 300)
            scene.sendPointerEvent(PointerEventType.Release, Offset(100f, 150f), timeMillis = 1800)
            render()
            assertNull(picked)
            scene.sendPointerEvent(PointerEventType.Press, Offset(100f, 150f), timeMillis = 1900)
            scene.sendPointerEvent(PointerEventType.Release, Offset(100f, 150f), timeMillis = 1901)
            render()
            assertNotNull(picked)
            Unit
        } finally { scene.close() }
    }
}
