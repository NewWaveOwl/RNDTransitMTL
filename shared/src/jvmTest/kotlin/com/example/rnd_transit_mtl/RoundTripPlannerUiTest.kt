package com.example.rnd_transit_mtl

import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.*
import com.example.rnd_transit_mtl.View.main.*
import com.example.rnd_transit_mtl.ViewModels.main.*
import com.example.rnd_transit_mtl.model.ids.TransportId
import com.example.rnd_transit_mtl.ui.theme.RNDTransitTheme
import kotlinx.coroutines.*
import java.io.File
import kotlin.test.*

/** Planner choices remain accessible inside the existing draggable preferences sheet. */
@OptIn(ExperimentalComposeUiApi::class)
class RoundTripPlannerUiTest {
    @Test fun selectsAndRestoresTripTypeWithoutChangingTheOrigin() = runBlocking(Dispatchers.Main) {
        val planner = MainPlannerViewModel()
        val original = planner.uiState.value.origin
        val scene = ImageComposeScene(412, 716) {
            val state by planner.uiState.collectAsState()
            RNDTransitTheme {
                MainPlannerContent(state.minutes, {}, emptyList(), listOf(TransportId("walk")),
                    emptyMap(), {}, { _, _ -> }, "Amble", 18, {}, emptyList(), {}, {}, "", "GO",
                    true, true, null, null, {}, {}, initialSheetPosition = PlannerSheetPosition.Preferences,
                    roundTrip = state.roundTrip, onRoundTripChange = planner::setRoundTrip)
            }
        }
        var time = 0L
        suspend fun render() { repeat(35) { yield(); time += 16_000_000; scene.render(time).close() } }
        fun all(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::all)
        fun choice(label: String) = scene.semanticsOwners.flatMap { all(it.rootSemanticsNode) }.first {
            it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == label } == true &&
                it.config.getOrNull(SemanticsProperties.Role) == Role.RadioButton
        }
        suspend fun tap(label: String) {
            val node = choice(label)
            scene.sendPointerEvent(PointerEventType.Press, node.boundsInRoot.center, timeMillis = time / 1_000_000)
            scene.sendPointerEvent(PointerEventType.Release, node.boundsInRoot.center, timeMillis = time / 1_000_000 + 1)
            render()
        }
        try {
            render()
            tap("Round trip")
            assertTrue(planner.uiState.value.roundTrip)
            assertTrue(choice("Round trip").config[SemanticsProperties.Selected])
            assertEquals(original, planner.uiState.value.origin)
            scene.render(time + 1).use { image ->
                val folder = File("build/planner-previews").apply { mkdirs() }
                image.encodeToData()!!.use { File(folder, "Round-trip-planner.png").writeBytes(it.bytes) }
            }
            tap("One way")
            assertFalse(planner.uiState.value.roundTrip)
            assertTrue(choice("One way").config[SemanticsProperties.Selected])
        } finally { scene.close() }
    }
}
