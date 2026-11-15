package com.example.rnd_transit_mtl

import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import com.example.rnd_transit_mtl.View.history.previews.reviewedHistoryPreviewTrip
import com.example.rnd_transit_mtl.View.main.TripLoadingContent
import com.example.rnd_transit_mtl.View.main.TripProposalContent
import com.example.rnd_transit_mtl.ui.theme.RNDTransitTheme
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import kotlin.test.assertEquals

/** Verifies the supplied loading/preview controls render and dispatch their separate actions. */
class TripProposalContentTest {
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun loadingAndProposalActionsRemainSeparate() {
        var loading by mutableStateOf(true)
        var proposal by mutableStateOf(reviewedHistoryPreviewTrip)
        val events = mutableListOf<String>()
        val scene = ImageComposeScene(412, 720) {
            RNDTransitTheme {
                if (loading) TripLoadingContent(30, "Walk", { events += "cancel" })
                else TripProposalContent(proposal,
                    { events += "start:${proposal.title}" }, { events += "another" }, { events += "edit" },
                    onRename = { proposal = proposal.copy(title = it) })
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
            val progress = scene.semanticsOwners.flatMap { all(it.rootSemanticsNode) }.firstOrNull {
                it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo) == ProgressBarRangeInfo.Indeterminate
            }
            assertNotNull(progress)
            val firstFrame = File("build/planner-previews/Trip-loading.png").readBytes()
            time += 600_000_000
            render("Trip-loading-moving.png")
            assertFalse(firstFrame.contentEquals(File("build/planner-previews/Trip-loading-moving.png").readBytes()))
            click("Cancel search")
            loading = false
            render("Trip-proposal.png")
            val texts = scene.semanticsOwners.flatMap { all(it.rootSemanticsNode) }
                .flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }
                .map { it.text }
            assertFalse(reviewedHistoryPreviewTrip.description in texts)
            assertFalse("Trip destination" in texts)
            assertEquals(1, texts.count { it == proposal.title })
            click(proposal.title)
            render("Trip-proposal-rename.png")
            val editor = scene.semanticsOwners.flatMap { all(it.rootSemanticsNode) }.first {
                it.config.getOrNull(SemanticsActions.SetText) != null
            }
            editor.config[SemanticsActions.SetText].action?.invoke(AnnotatedString("  Evening walk  "))
            render("Trip-proposal-name-draft.png")
            click("Save")
            render("Trip-proposal-renamed.png")
            assertEquals("Evening walk", proposal.title)
            click("Generate another")
            click("Edit settings")
            click("Start Trip")
            assertEquals(listOf("cancel", "another", "edit", "start:Evening walk"), events)
        } finally { scene.close() }
    }
}
