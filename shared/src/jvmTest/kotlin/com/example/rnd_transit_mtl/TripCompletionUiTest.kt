package com.example.rnd_transit_mtl

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.example.rnd_transit_mtl.View.current_trip.TripCompletionContent
import com.example.rnd_transit_mtl.View.history.previews.reviewedHistoryPreviewTrip
import com.example.rnd_transit_mtl.ui.global_ui_ellements.buttons.TripFlowButton
import com.example.rnd_transit_mtl.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import java.io.File
import kotlin.test.*

/** The saved completion sheet must expose its metrics and actions at every supported viewport. */
@OptIn(ExperimentalComposeUiApi::class)
class TripCompletionUiTest {
    @Test fun statisticsExpandAndTheSheetKeepsActionsReachableAfterResize() = runBlocking(Dispatchers.Main) {
        var width by mutableStateOf(412)
        var height by mutableStateOf(716)
        var reviews = 0
        var returns = 0
        val scene = ImageComposeScene(1000, 900) {
            RNDTransitTheme {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
                    TripCompletionContent(reviewedHistoryPreviewTrip, { reviews++ }, {}, {},
                        modifier = Modifier.size(width.dp, height.dp), returnControls = {
                            Text("Return to starting point", color = TransitWhite)
                            TripFlowButton("Fastest way back", { returns++ }, true)
                        })
                }
            }
        }
        var time = 0L
        suspend fun render() { repeat(50) { yield(); time += 16_000_000; scene.render(time).close() } }
        fun all(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::all)
        fun nodes() = scene.semanticsOwners.flatMap { all(it.rootSemanticsNode) }
        fun text(label: String) = nodes().first {
            it.config.getOrNull(SemanticsProperties.Text)?.any { value -> value.text == label } == true
        }
        fun handle() = nodes().first { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Trip summary") == true }
        suspend fun click(label: String) {
            val point = text(label).boundsInRoot.center
            scene.sendPointerEvent(PointerEventType.Press, point, timeMillis = time / 1_000_000)
            scene.sendPointerEvent(PointerEventType.Release, point, timeMillis = time / 1_000_000 + 1)
            render()
        }
        fun snapshot(name: String) {
            scene.render(time + 1).use { image ->
                val folder = File("build/planner-previews").apply { mkdirs() }
                image.encodeToData()!!.use { File(folder, name).writeBytes(it.bytes) }
            }
        }
        try {
            render()
            assertNotNull(text("Not recorded"))
            click("Fastest way back")
            click("Trip Review")
            assertEquals(1, returns)
            assertEquals(1, reviews)
            snapshot("Round-trip-arrival.png")
            click("Distance")
            assertNotNull(text("Distance by transport"))
            assertNotNull(text("Only modes used in this trip are shown."))
            snapshot("Round-trip-distance.png")
            // Dismiss the popup by tapping the uncovered map.
            scene.sendPointerEvent(PointerEventType.Press, androidx.compose.ui.geometry.Offset(10f, 10f))
            scene.sendPointerEvent(PointerEventType.Release, androidx.compose.ui.geometry.Offset(10f, 10f))
            render()
            assertTrue(handle().config[SemanticsActions.CustomActions].first { it.label == "Collapse trip summary" }.action())
            render()
            assertEquals("Collapsed", handle().config[SemanticsProperties.StateDescription])
            assertNotNull(text("Drag up for trip stats and return options"))
            assertTrue(handle().config[SemanticsActions.CustomActions].first { it.label == "Expand trip summary" }.action())
            render()
            for ((newWidth, newHeight) in listOf(320 to 480, 720 to 360)) {
                width = newWidth; height = newHeight
                render()
                val scroll = nodes().first { it.config.getOrNull(SemanticsActions.ScrollBy) != null }
                assertTrue(scroll.config[SemanticsActions.ScrollBy].action!!.invoke(0f, 10_000f))
                render()
                val bounds = text("Trip Review").boundsInRoot
                assertTrue(bounds.top >= 0 && bounds.bottom <= height, "$newWidth x $newHeight: $bounds")
                click("Trip Review")
            }
            assertEquals(3, reviews)
        } finally { scene.close() }
    }
}
