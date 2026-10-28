package com.example.rnd_transit_mtl

import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.example.rnd_transit_mtl.View.current_trip.*
import com.example.rnd_transit_mtl.View.history.previews.reviewedHistoryPreviewTrip
import com.example.rnd_transit_mtl.ui.theme.RNDTransitTheme
import java.io.File
import kotlin.test.*

/** Exercises active/overview controls and explicit provider-error fixtures without inventing provider state. */
class ActiveMapContentTest {
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun staleAndMissingLocationsKeepTheRouteWithoutAdvancingTheMarker() {
        var progress by mutableStateOf(0.4f)
        var location by mutableStateOf(TripLocationState.Sample)
        var informationRequests = 0
        val scene = ImageComposeScene(412, 720) {
            RNDTransitTheme {
                ActiveMapContent(reviewedHistoryPreviewTrip, progress, true, {}, {},
                    instruction = ActiveMapInstruction("Sample turn", "Sample section", true),
                    locationState = location, onShowInformation = { informationRequests++ })
            }
        }
        var time = 0L
        fun render() { repeat(8) { time += 16_000_000; scene.render(time).close() } }
        fun all(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::all)
        fun nodes() = scene.semanticsOwners.flatMap { all(it.rootSemanticsNode) }
        fun map() = nodes().first {
            it.config.getOrNull(SemanticsProperties.ContentDescription)?.any { text ->
                text.startsWith("Mock transit map")
            } == true
        }
        try {
            render()
            assertEquals("40 percent complete", map().config[SemanticsProperties.StateDescription])
            location = TripLocationState.Stale
            progress = 0.7f
            render()
            assertEquals("40 percent complete", map().config[SemanticsProperties.StateDescription])
            location = TripLocationState.Missing
            progress = 0.8f
            render()
            assertEquals("40 percent complete", map().config[SemanticsProperties.StateDescription])
            assertTrue(map().config[SemanticsProperties.ContentDescription].any { "Position unavailable" in it })
            val recenter = nodes().first {
                it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Recenter on current position") == true
            }
            assertTrue(recenter.config.contains(SemanticsProperties.Disabled))
            val section = nodes().first {
                it.config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.OnClick)?.label ==
                    "Open current section information"
            }
            section.config[androidx.compose.ui.semantics.SemanticsActions.OnClick].action!!.invoke()
            assertEquals(1, informationRequests)
            location = TripLocationState.Sample
            render()
            assertEquals("80 percent complete", map().config[SemanticsProperties.StateDescription])
        } finally { scene.close() }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun sharedPanelsKeepTheirActionsAndProgressSeparate() {
        var mode by mutableStateOf(0)
        var progress by mutableStateOf(0.4f)
        val events = mutableListOf<String>()
        val trip = reviewedHistoryPreviewTrip
        val scene = ImageComposeScene(412, 720) {
            RNDTransitTheme {
                when (mode) {
                    0 -> CurrentTripContent(trip, progress, true, true, null, {}, {},
                        onRequestReview = { events += "review" },
                        onFinishRequested = { events += "finish"; progress = 1f },
                        onOpenImageReference = {})
                    1, 2 -> ActiveMapContent(trip, 0.4f, true, {}, {},
                        mainOverview = mode == 1, onResume = { events += "resume" },
                        staleMessage = if (mode == 2) "Last location is retained." else null)
                    else -> RouteFailurePanel(
                        if (mode == 3) RouteFailure.NoRoute else RouteFailure.ProviderUnavailable,
                        "30 minutes · Walk", { events += "retry" }, { events += "edit" })
                }
            }
        }
        var time = 0L
        fun render(name: String) {
            repeat(16) { time += 16_000_000; scene.render(time).close() }
            scene.render(time).use { image ->
                val folder = File("build/planner-previews").apply { mkdirs() }
                image.encodeToData()!!.use { File(folder, name).writeBytes(it.bytes) }
            }
        }
        fun all(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::all)
        fun node(label: String) = scene.semanticsOwners.flatMap { all(it.rootSemanticsNode) }.firstOrNull {
            it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == label } == true
        }
        fun click(label: String) {
            val point = assertNotNull(node(label)).boundsInRoot.center
            scene.sendPointerEvent(PointerEventType.Press, point, timeMillis = time / 1_000_000)
            scene.sendPointerEvent(PointerEventType.Release, point, timeMillis = time / 1_000_000 + 1)
        }
        try {
            render("Active-trip.png")
            click("Finish trip")
            render("Finish-confirmation.png")
            assertFalse("finish" in events)
            click("Yes, finish trip")
            render("Active-trip-completed.png")
            click("Review trip")
            mode = 1
            render("Main-active-overview.png")
            assertNull(node("Finish trip"))
            click("Resume current trip")
            mode = 2
            render("Active-trip-stale-fixture.png")
            assertNotNull(node("Location not updating"))
            mode = 3
            render("No-route-fixture.png")
            click("Adjust time or modes")
            click("Try another search")
            mode = 4
            render("Provider-error-fixture.png")
            click("Try again")
            click("Edit trip settings")
            assertEquals(listOf("finish", "review", "resume", "edit", "retry", "retry", "edit"), events)
        } finally { scene.close() }
    }
}
