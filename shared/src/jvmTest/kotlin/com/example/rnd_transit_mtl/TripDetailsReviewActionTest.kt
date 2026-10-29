package com.example.rnd_transit_mtl

import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.*
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.example.rnd_transit_mtl.View.history.previews.reviewedHistoryPreviewTrip
import com.example.rnd_transit_mtl.View.trip_details.TripDetailsScreen
import com.example.rnd_transit_mtl.model.review.TripReview
import com.example.rnd_transit_mtl.ui.global_ui_ellements.trip.TripDetailsV3
import com.example.rnd_transit_mtl.navigation.*
import com.example.rnd_transit_mtl.state.trip.*
import com.example.rnd_transit_mtl.ui.theme.RNDTransitTheme
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

/** Guards the live V3 details path, which previously dropped the review navigation callback. */
@OptIn(ExperimentalComposeUiApi::class)
class TripDetailsReviewActionTest {
    @Test
    fun detailsRetainAllSavedFeedbackAndThePhotoLink() {
        val photo = "https://example.com/saved-review.jpg"
        val review = TripReview(overall = 5, quality = 4, interesting = 3, `fun` = 2,
            comment = "A memorable trip", imageUrl = photo)
        var trip by mutableStateOf(reviewedHistoryPreviewTrip.copy(review = review))
        var enabled by mutableStateOf(true)
        val opened = mutableListOf<String>()
        val scene = ImageComposeScene(412, 720) {
            CompositionLocalProvider(LocalInspectionMode provides true,
                LocalUriHandler provides object : UriHandler {
                    override fun openUri(uri: String) { opened += uri }
                }) {
                RNDTransitTheme { TripDetailsV3(trip, "2026-10-29", {}, {}, enabled = enabled) }
            }
        }
        var time = 0L
        fun render() { repeat(5) { time += 16_000_000; scene.render(time).close() } }
        fun all(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::all)
        fun nodes() = scene.semanticsOwners.flatMap { all(it.rootSemanticsNode) }
        fun hasDescription(text: String) = nodes().any {
            it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(text) == true
        }
        fun textNode(text: String) = nodes().firstOrNull {
            it.config.getOrNull(SemanticsProperties.Text)?.any { value -> value.text == text } == true
        }
        fun link() = nodes().first {
            it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == "Open review image" } == true &&
                it.config.contains(SemanticsActions.OnClick)
        }
        try {
            render()
            listOf("Overall experience: 5 out of 5", "Quality: 4 out of 5",
                "How interesting: 3 out of 5", "Fun: 2 out of 5").forEach { assertTrue(hasDescription(it), it) }
            assertNotNull(textNode("Review photo"))
            assertNotNull(textNode("Saved review photo preview"))
            assertNotNull(textNode(photo))
            assertTrue(nodes().any {
                it.config.getOrNull(SemanticsProperties.Text)?.any { text -> "A memorable trip" in text.text } == true
            })
            link().config[SemanticsActions.OnClick].action!!.invoke()
            assertEquals(listOf(photo), opened)
            enabled = false
            render()
            assertTrue(link().config.contains(SemanticsProperties.Disabled))

            trip = trip.copy(review = TripReview(overall = 3))
            render()
            listOf("Quality: not rated", "How interesting: not rated", "Fun: not rated")
                .forEach { assertTrue(hasDescription(it), it) }
            assertNotNull(textNode("No written comment."))
            assertNull(textNode("Review photo"))
            assertNull(textNode("Open review image"))

            trip = trip.copy(review = null)
            render()
            assertNotNull(textNode("Not reviewed"))
        } finally { scene.close() }
    }

    @Test
    fun savedDetailsExposeReviewOrEditReviewAndRespectDestinationActivity() {
        for (reviewed in listOf(false, true)) {
            val trip = reviewedHistoryPreviewTrip.let { if (reviewed) it else it.copy(review = null) }
            val store = assertNotNull(TripsStore.fromSavedStateJson(
                Json.encodeToString(TripsState(completedTrips = listOf(trip))), { 1_800_000_000_000L }))
            val navigation = TripNavigation(Navigator(NavBackStack<NavKey>(TripDetailsScreenKey(trip.id))), store)
            val requestedIds = mutableListOf<String>()
            var active by mutableStateOf(true)
            var reviewAvailable by mutableStateOf(true)
            val scene = ImageComposeScene(412, 720) {
                CompositionLocalProvider(LocalTripsStore provides store, LocalTripNavigation provides navigation) {
                    RNDTransitTheme {
                        TripDetailsScreen(trip.id, {},
                            onReview = if (reviewAvailable) { id -> requestedIds += id } else null,
                            isDestinationActive = active)
                    }
                }
            }
            var time = 0L
            fun render() { repeat(5) { time += 16_000_000; scene.render(time).close() } }
            fun all(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::all)
            fun action(label: String) = scene.semanticsOwners.flatMap { all(it.rootSemanticsNode) }.firstOrNull {
                it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == label } == true &&
                    it.config.contains(SemanticsActions.OnClick)
            }
            val label = if (reviewed) "Edit review" else "Review trip"
            try {
                render()
                val button = assertNotNull(action(label))
                button.config[SemanticsActions.OnClick].action!!.invoke()
                assertEquals(listOf(trip.id), requestedIds)

                active = false
                render()
                assertTrue(assertNotNull(action(label)).config.contains(SemanticsProperties.Disabled))
                // An outgoing view may retain its old callback; the coordinator still guards it.
                button.config[SemanticsActions.OnClick].action!!.invoke()
                assertEquals(listOf(trip.id), requestedIds)

                active = true
                reviewAvailable = false
                render()
                assertNull(action(label))
                assertEquals(trip, store.findCompleted(trip.id))
                assertNull(store.activeTrip)
            } finally { scene.close() }
        }
    }
}
