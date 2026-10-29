package com.example.rnd_transit_mtl

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.runtime.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Constraints
import com.example.rnd_transit_mtl.View.main.components.PlannerPreferenceBands
import com.example.rnd_transit_mtl.ViewModels.main.MainPlannerViewModel
import com.example.rnd_transit_mtl.model.transport.TransportType
import com.example.rnd_transit_mtl.ui.theme.RNDTransitTheme
import java.io.File
import kotlin.test.*

/** Verifies As3-style click behavior and continuous expansion/collapse using rendered frames. */
class TransportDropdownInteractionTest {
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun clicksCloseSwitchAndKeepSelectionsWhileHeightAnimatesThroughIntermediateFrames() {
        var expanded by mutableStateOf<String?>(null)
        val vm = MainPlannerViewModel()
        val scene = ImageComposeScene(412, 1000) {
            val draft by vm.uiState.collectAsState()
            RNDTransitTheme {
                LazyColumn(Modifier.fillMaxSize()) {
                    PlannerPreferenceBands(
                        scale = 1f,
                        transportTypes = listOf(TransportType("walk", "Walk", false), TransportType("bike", "Bike", false),
                            TransportType("bus", "Bus", true), TransportType("metro", "Metro", true),
                            TransportType("train", "Train", true), TransportType("rem", "REM", true)),
                        selectedTransportIds = draft.selectedTransportIds,
                        selectedZonesByTransport = MainPlannerViewModel.transitIds.associateWith { draft.zonesForTransport(it) },
                        onToggleTransport = { vm.toggleTransport(it, setOf("walk", "bike")) },
                        onToggleZone = vm::toggleZone, walkingPreference = draft.walkingPreference,
                        bikeSpeedKmh = draft.bikeSpeedKmh, onBikeSpeedChange = vm::setBikeSpeedKmh,
                        selectedAttractionIds = draft.selectedAttractionIds, onWalkingPreferenceChange = vm::setWalkingPreference,
                        onToggleAttraction = vm::toggleAttraction, enabled = true, onOpenDetailedSettings = {},
                        expandedTransportId = expanded,
                        onExpandedTransportChange = { expanded = if (expanded == it) null else it })
                }
            }
        }
        var time = 0L
        fun frames(count: Int) { repeat(count) { time += 16_000_000L; scene.render(time).close(); Thread.sleep(3) } }
        fun click(x: Float, y: Float) {
            scene.sendPointerEvent(PointerEventType.Press, Offset(x, y), timeMillis = time / 1_000_000L)
            scene.sendPointerEvent(PointerEventType.Release, Offset(x, y), timeMillis = time / 1_000_000L + 1)
        }
        fun all(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap { all(it) }
        fun textNode(label: String): SemanticsNode? = scene.semanticsOwners.flatMap { all(it.rootSemanticsNode) }.firstOrNull {
            it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == label } == true
        }
        fun headingY(): Float = assertNotNull(textNode("Walking preferences")).boundsInRoot.top
        fun save(name: String) {
            val out = File("build/planner-previews").apply { mkdirs() }
            scene.render(time).use { image -> image.encodeToData()!!.use { File(out, name).writeBytes(it.bytes) } }
        }
        try {
            frames(20)
            listOf("Amble", "Walk", "Dash").forEach { pace ->
                val bounds = scene.semanticsOwners.flatMap { all(it.rootSemanticsNode) }.first {
                    it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == pace } == true &&
                        it.boundsInRoot.top > headingY()
                }.boundsInRoot
                click(bounds.center.x, bounds.center.y)
                frames(2)
                assertEquals(pace, vm.uiState.value.walkingPreference)
            }
            val closed = headingY()
            click(330f, 74f)
            frames(5)
            val opening = headingY()
            save("Dropdown-opening.png")
            frames(10)
            val openingAfter240ms = headingY()
            frames(30)
            val open = headingY()
            assertTrue(openingAfter240ms > opening && openingAfter240ms < open,
                "The slower opening must still be progressing after 240 ms")
            assertEquals("bus", expanded)
            assertEquals(listOf("walk"), vm.uiState.value.selectedTransportIds)
            assertTrue(opening > closed && opening < open, "Opening must pass smoothly through an intermediate height")
            click(206f, 270f)
            frames(5)
            save("Zone-selection-filling-inward.png")
            frames(25)
            save("Zone-selection-filled.png")
            assertEquals(listOf("B"), vm.uiState.value.zonesForTransport("bus"))
            click(330f, 74f)
            frames(5)
            val closing = headingY()
            save("Dropdown-closing.png")
            frames(40)
            assertNull(expanded)
            assertTrue(closing > headingY() && closing < open, "Closing must pass smoothly through an intermediate height")
            assertEquals(listOf("walk", "bus"), vm.uiState.value.selectedTransportIds)
            assertEquals(listOf("B"), vm.uiState.value.zonesForTransport("bus"))
            click(330f, 74f)
            frames(40)
            click(82f, 126f)
            frames(16)
            assertNotNull(textNode("Bus zones"), "Keep the old menu while it collapses")
            assertNull(textNode("Metro zones"), "Do not open the next menu before collapse finishes")
            val switchingDown = headingY()
            assertTrue(switchingDown > closed && switchingDown < open)
            frames(20)
            assertNull(textNode("Bus zones"))
            assertNotNull(textNode("Metro zones"))
            assertTrue(headingY() < switchingDown, "The new menu must start opening from the collapsed position")
            frames(40)
            assertEquals("metro", expanded)
            click(330f, 270f)
            frames(5)
            assertEquals(listOf("C"), vm.uiState.value.zonesForTransport("metro"))
            assertEquals(listOf("B"), vm.uiState.value.zonesForTransport("bus"))
            save("Dropdown-switched-to-metro.png")
            click(82f, 126f)
            frames(40)
            assertNull(expanded)

            // A click with no zone selection still closes before the next type opens.
            click(206f, 126f) // Train, initially without zones.
            frames(40)
            assertNotNull(textNode("Train zones"))
            click(330f, 126f) // REM.
            frames(16)
            assertNotNull(textNode("Train zones"))
            assertNull(textNode("REM zones"))
            click(330f, 74f) // Latest request wins while the previous switch is collapsing.
            frames(80)
            assertEquals("bus", expanded)
            assertNotNull(textNode("Bus zones"))
            assertNull(textNode("REM zones"))
            assertEquals(listOf("B"), vm.uiState.value.zonesForTransport("bus"))

            // Walking uses the exact same 500 ms motion in both directions.
            vm.setWalkingPreference("Dash")
            frames(2)
            fun attractionsY(): Float = assertNotNull(textNode("Make it your kind of random.")).boundsInRoot.top
            val withWalking = attractionsY()
            click(82f, 74f)
            frames(5)
            val walkingClosing = attractionsY()
            frames(10)
            val walkingClosingAfter240ms = attractionsY()
            frames(30)
            val withoutWalking = attractionsY()
            assertTrue(walkingClosing < withWalking && walkingClosing > withoutWalking)
            assertTrue(walkingClosingAfter240ms < walkingClosing && walkingClosingAfter240ms > withoutWalking)
            assertNull(textNode("Walking preferences"))
            assertNotNull(textNode("Bus zones"))
            click(82f, 74f)
            frames(5)
            val walkingOpening = attractionsY()
            frames(10)
            val walkingOpeningAfter240ms = attractionsY()
            frames(30)
            assertTrue(walkingOpening > withoutWalking && walkingOpening < attractionsY())
            assertTrue(walkingOpeningAfter240ms > walkingOpening && walkingOpeningAfter240ms < attractionsY())
            assertEquals(withWalking, attractionsY())
            assertEquals("Dash", vm.uiState.value.walkingPreference)
            assertEquals(listOf("B"), vm.uiState.value.zonesForTransport("bus"))

            val originalRowY = assertNotNull(textNode("Walk")).boundsInRoot.top
            // Bike uses the same opening motion, accepts taps and continuous drags, and keeps its value when hidden.
            expanded = null
            frames(40)
            val withoutBike = headingY()
            click(206f, 74f)
            frames(5)
            val bikeOpening = headingY()
            frames(10)
            val bikeOpeningAfter240ms = headingY()
            frames(30)
            assertTrue(bikeOpening > withoutBike && bikeOpening < headingY())
            assertTrue(bikeOpeningAfter240ms > bikeOpening && bikeOpeningAfter240ms < headingY())
            fun bikeSlider() = scene.semanticsOwners.flatMap { all(it.rootSemanticsNode) }.first {
                it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Biking speed in kilometres per hour") == true
            }
            val track = bikeSlider().boundsInRoot
            click(track.left + 1, track.center.y)
            frames(2)
            assertEquals(8, vm.uiState.value.bikeSpeedKmh)
            scene.sendPointerEvent(PointerEventType.Press, Offset(track.left + 1, track.center.y), timeMillis = time / 1_000_000L)
            frames(2)
            scene.sendPointerEvent(PointerEventType.Move, Offset(track.center.x, track.center.y), timeMillis = time / 1_000_000L)
            frames(2)
            assertEquals(20, vm.uiState.value.bikeSpeedKmh)
            scene.sendPointerEvent(PointerEventType.Move, Offset(track.right - 1, track.center.y), timeMillis = time / 1_000_000L)
            frames(2)
            scene.sendPointerEvent(PointerEventType.Release, Offset(track.right - 1, track.center.y), timeMillis = time / 1_000_000L)
            frames(2)
            assertEquals(32, vm.uiState.value.bikeSpeedKmh)
            save("Bike-speed-32.png")
            vm.setBikeSpeedKmh(26)
            frames(2)
            assertNotNull(textNode("speedy"))
            save("Bike-speed-26-design.png")
            vm.setBikeSpeedKmh(32)
            frames(2)
            val withBike = headingY()
            click(206f, 74f)
            frames(5)
            val bikeClosing = headingY()
            frames(40)
            assertTrue(bikeClosing < withBike && bikeClosing > headingY())
            assertNull(textNode("Biking speed"))
            assertEquals(32, vm.uiState.value.bikeSpeedKmh)
            MainPlannerViewModel.transitIds.forEach { mode ->
                MainPlannerViewModel.zoneIds.forEach { zone ->
                    if (zone !in vm.uiState.value.zonesForTransport(mode)) vm.toggleZone(mode, zone)
                }
            }
            // Constrain the row so long summaries actually exceed the available button width.
            scene.constraints = Constraints.fixed(300, 1000)
            frames(10)
            save("Dropdown-all-zones-ellipsis.png")
            MainPlannerViewModel.transitIds.forEach { mode ->
                val title = when (mode) { "bus" -> "Bus"; "metro" -> "Metro"; "train" -> "Train"; else -> "REM" }
                val label = "$title A, B, C, D"
                val node = assertNotNull(textNode(label), "Retain the full zone summary for accessibility")
                val layouts = mutableListOf<TextLayoutResult>()
                assertTrue(node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts) == true)
                assertEquals(1, layouts.single().lineCount, "Long transport summaries must never wrap")
                if (mode == "metro") {
                    // Check measured overflow; the rendered Skia text visibly truncates with an ellipsis.
                    assertTrue(layouts.single().multiParagraph.intrinsics.maxIntrinsicWidth > layouts.single().size.width)
                    assertEquals(TextOverflow.Ellipsis, layouts.single().layoutInput.overflow)
                }
            }
            assertEquals(originalRowY, assertNotNull(textNode("Walk")).boundsInRoot.top)
            save("Dropdown-all-zones-ellipsis.png")
        } finally { scene.close() }
    }
}
