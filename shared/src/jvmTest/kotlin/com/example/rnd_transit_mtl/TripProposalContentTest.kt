package com.example.rnd_transit_mtl

import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.example.rnd_transit_mtl.View.history.previews.reviewedHistoryPreviewTrip
import com.example.rnd_transit_mtl.View.main.TripLoadingContent
import com.example.rnd_transit_mtl.View.main.TripProposalContent
import com.example.rnd_transit_mtl.ui.theme.RNDTransitTheme
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** Verifies the supplied loading/preview controls render and dispatch their separate actions. */
class TripProposalContentTest {
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun loadingAndProposalActionsRemainSeparate() {
        var loading by mutableStateOf(true)
        val events = mutableListOf<String>()
        val scene = ImageComposeScene(412, 720) {
            RNDTransitTheme {
                if (loading) TripLoadingContent(30, "Walk", { events += "cancel" })
                else TripProposalContent(reviewedHistoryPreviewTrip,
                    { events += "start" }, { events += "another" }, { events += "edit" })
            }
        }
        var time = 0L
        fun render(name: String) {
            repeat(3) { time += 16_000_000; scene.render(time).close() }
            time += 16_000_000
            scene.render(time).use { image ->
                val folder = File("build/planner-previews").apply { mkdirs() }
                image.encodeToData()!!.use { File(folder, name).writeBytes(it.bytes) }
            }
        }
        fun all(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::all)
        fun click(label: String) {
            val node = scene.semanticsOwners.flatMap { all(it.rootSemanticsNode) }.first {
                it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == label } == true
            }
            val point = node.boundsInRoot.center
            scene.sendPointerEvent(PointerEventType.Press, point, timeMillis = time / 1_000_000)
            scene.sendPointerEvent(PointerEventType.Release, point, timeMillis = time / 1_000_000 + 1)
        }
        try {
            render("Trip-loading.png")
            click("Cancel search")
            loading = false
            render("Trip-proposal.png")
            click("Generate another")
            click("Edit settings")
            click("Start Trip")
            assertEquals(listOf("cancel", "another", "edit", "start"), events)
        } finally { scene.close() }
    }
}
