package com.example.rnd_transit_mtl

import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.*
import com.example.rnd_transit_mtl.View.history.previews.reviewedHistoryPreviewTrip
import com.example.rnd_transit_mtl.ui.global_ui_ellements.trip.*
import com.example.rnd_transit_mtl.ui.theme.RNDTransitTheme
import java.io.File
import kotlin.test.*

/** Checks the supplied screens and their explicit, parent-owned actions. */
class TripScreensV3Test {
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun screensRenderAndReviewRequiresStars() {
        val trip = reviewedHistoryPreviewTrip
        var mode by mutableStateOf(0)
        var rating by mutableStateOf<Int?>(null)
        var choice by mutableStateOf(FeedbackChoice.NONE)
        val events = mutableListOf<String>()
        val scene = ImageComposeScene(412, 720) {
            RNDTransitTheme {
                when (mode) {
                    0 -> CompleteV3(trip, { events += "review" }, { events += "skip" }, {}, {})
                    1 -> ReviewV3(trip.title, trip.id.value.takeLast(3), rating, "", { rating = it }, {},
                        { events += "save" }, {}, {}, {}, feedbackEnabled = false)
                    2 -> FeedbackV3(trip.id.value.takeLast(3), "Sample segment", "Preview fixture only", choice,
                        "", { choice = it }, {}, { events += "feedback" }, {})
                    3 -> HistoryV3(listOf(HistoryRowV3(trip.id, trip.title, "30 min · Walk", "Completed", true)),
                        { events += "details" }, {}, {})
                    else -> TripDetailsV3(trip, "2026-10-28", { events += "repeat" }, { events += "copy" })
                }
            }
        }
        var time = 0L
        fun render(name: String) {
            repeat(4) { time += 16_000_000; scene.render(time).close() }
            scene.render(time).use { image ->
                val dir = File("build/planner-previews").apply { mkdirs() }
                image.encodeToData()!!.use { File(dir, name).writeBytes(it.bytes) }
            }
        }
        fun all(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::all)
        fun click(label: String) {
            val node = scene.semanticsOwners.flatMap { all(it.rootSemanticsNode) }.first {
                it.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text == label } == true ||
                    it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label) == true
            }
            val point = node.boundsInRoot.center
            scene.sendPointerEvent(PointerEventType.Press, point, timeMillis = time / 1_000_000)
            scene.sendPointerEvent(PointerEventType.Release, point, timeMillis = time / 1_000_000 + 1)
        }
        try {
            render("Complete-v3.png"); click("Review this trip"); click("Skip review — view history")
            mode = 1; render("Review-v3-unrated.png"); click("Save review")
            assertFalse("save" in events)
            click("Rate Overall experience 4 out of 5"); render("Review-v3.png")
            click("Save review"); assertEquals(4, rating)
            mode = 2; render("Feedback-v3-fixture.png"); click("Like"); render("Feedback-v3-liked.png")
            assertEquals(FeedbackChoice.LIKED, choice)
            click("Clear"); assertEquals(FeedbackChoice.NONE, choice)
            mode = 3; render("History-v3.png"); click("Details")
            mode = 4; render("Details-v3.png")
            scene.semanticsOwners.flatMap { all(it.rootSemanticsNode) }.first {
                it.config.getOrNull(SemanticsActions.ScrollBy) != null
            }.config[SemanticsActions.ScrollBy].action?.invoke(0f, 10_000f)
            render("Details-v3-scrolled.png")
            click("Repeat as a new trip")
            click("Edit a copy of these settings")
            assertEquals(listOf("review", "skip", "save", "details", "repeat", "copy"), events)
        } finally { scene.close() }
    }
}
