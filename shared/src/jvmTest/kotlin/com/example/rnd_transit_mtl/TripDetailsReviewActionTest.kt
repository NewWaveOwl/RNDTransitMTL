package com.example.rnd_transit_mtl

import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.*
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.example.rnd_transit_mtl.View.history.previews.reviewedHistoryPreviewTrip
import com.example.rnd_transit_mtl.View.trip_details.TripDetailsScreen
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
