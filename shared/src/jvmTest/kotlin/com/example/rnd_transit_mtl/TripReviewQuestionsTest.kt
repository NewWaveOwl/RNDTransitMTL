package com.example.rnd_transit_mtl

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import com.example.rnd_transit_mtl.View.history.previews.reviewedHistoryPreviewTrip
import com.example.rnd_transit_mtl.View.trip_review.TripReviewScreen
import com.example.rnd_transit_mtl.model.review.TripReviewMode
import com.example.rnd_transit_mtl.state.review.ReviewStep
import com.example.rnd_transit_mtl.state.trip.LocalTripsStore
import com.example.rnd_transit_mtl.state.trip.TripsState
import com.example.rnd_transit_mtl.state.trip.TripsStore
import com.example.rnd_transit_mtl.ui.theme.RNDTransitTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

/** Verifies that the live review screen reaches and saves all four existing rating categories. */
@OptIn(ExperimentalComposeUiApi::class)
class TripReviewQuestionsTest {
    @Test
    fun reviewQuestionsSaveIndependentRatingsAndSupportPrevious() = runBlocking(Dispatchers.Main) {
        val trip = reviewedHistoryPreviewTrip.copy(review = null)
        val store = assertNotNull(TripsStore.fromSavedStateJson(
            Json.encodeToString(TripsState(completedTrips = listOf(trip))), { 1_800_000_000_000L }))
        val lifecycleOwner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this).apply {
                currentState = Lifecycle.State.RESUMED
            }
        }
        var exits = 0
        val scene = ImageComposeScene(412, 900) {
            val dispatcher = rememberNavigationEventDispatcherOwner(parent = null)
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides dispatcher,
                LocalLifecycleOwner provides lifecycleOwner, LocalTripsStore provides store) {
                RNDTransitTheme {
                    TripReviewScreen(trip.id, TripReviewMode.INITIAL, true,
                        onOpenHistory = { exits++; true }, onReturnToOrigin = { true })
                }
            }
        }
        var time = 0L
        suspend fun render() {
            repeat(24) { yield(); time += 16_000_000; scene.render(time).close() }
        }
        fun all(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::all)
        fun nodes() = scene.semanticsOwners.flatMap { all(it.rootSemanticsNode) }
        fun clickText(text: String) {
            val node = nodes().first {
                it.config.getOrNull(SemanticsProperties.Text)?.any { value -> value.text == text } == true &&
                    it.config.contains(SemanticsActions.OnClick)
            }
            assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke())
        }
        fun rate(label: String, stars: Int) {
            val node = nodes().first {
                it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Rate $label $stars out of 5") == true
            }
            assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke())
        }
        try {
            render()
            rate("Overall experience", 5)
            render()
            clickText("Continue")
            render()
            assertEquals(ReviewStep.QUALITY, store.reviewDrafts[trip.id]?.step)
            rate("Quality", 4)
            render()
            clickText("Continue")
            render()
            rate("How interesting", 3)
            render()
            clickText("Previous question")
            render()
            assertEquals(4, store.reviewDrafts[trip.id]?.quality)
            clickText("Continue")
            render()
            assertEquals(3, store.reviewDrafts[trip.id]?.interesting)
            clickText("Continue")
            render()
            rate("Fun", 2)
            render()
            clickText("Save review")
            render()
            val review = assertNotNull(store.findCompleted(trip.id)?.review)
            assertEquals(listOf(5, 4, 3, 2), listOf(review.overall, review.quality, review.interesting, review.`fun`))
            assertNull(store.reviewDrafts[trip.id])
            assertEquals(1, exits)
        } finally { scene.close() }
    }
}
