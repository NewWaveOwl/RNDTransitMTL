package com.example.rnd_transit_mtl

import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.example.rnd_transit_mtl.View.current_trip.*
import com.example.rnd_transit_mtl.View.history.previews.reviewedHistoryPreviewTrip
import com.example.rnd_transit_mtl.model.trip.pointAlongRoute
import com.example.rnd_transit_mtl.ui.theme.RNDTransitTheme
import java.io.File
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.*

/** Exercises active/overview controls and explicit provider-error fixtures without inventing provider state. */
class ActiveMapContentTest {
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun estimatedPositionStaysVisibleWithoutAnErrorAndStalePositionWarnsUntilGpsRecovers() {
        val trip = guidanceTestTrip()
        var location by mutableStateOf(TripLocationState.Estimated)
        var progress by mutableStateOf(0.2f)
        val scene = ImageComposeScene(412, 720) {
            RNDTransitTheme {
                ActiveMapContent(trip, progress, true, {}, {}, simulationEnabled = false,
                    locationState = location, userPosition = trip.pointAlongRoute(progress))
            }
        }
        var time = 0L
        fun render(name: String? = null) {
            repeat(10) { time += 16_000_000; scene.render(time).close() }
            if (name != null) scene.render(time).use { image ->
                val folder = File("build/planner-previews").apply { mkdirs() }
                image.encodeToData()!!.use { File(folder, name).writeBytes(it.bytes) }
            }
        }
        fun all(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::all)
        fun nodes() = scene.semanticsOwners.flatMap { all(it.rootSemanticsNode) }
        fun texts() = nodes().flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }.map { it.text }
        try {
            render("Active-trip-estimated-position.png")
            assertTrue(texts().any { "Estimated position" in it })
            assertFalse(texts().any { "not updating" in it || "Location unavailable" in it })
            val recenter = nodes().first {
                it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Recenter on current position") == true
            }
            assertFalse(recenter.config.contains(SemanticsProperties.Disabled))
            location = TripLocationState.EstimatedStale
            progress = 0.21f
            render("Active-trip-estimated-location-warning.png")
            assertContains(texts(), "Location is not updating")
            assertTrue(texts().any { "Estimated position" in it })
            assertTrue(texts().any { "30 seconds" in it })
            location = TripLocationState.Stale
            render("Active-trip-location-paused.png")
            assertContains(texts(), "Location is not updating")
            assertTrue(texts().any { "Position paused" in it })
            assertTrue(texts().any { "30 seconds" in it })
            location = TripLocationState.Live
            render()
            assertFalse(texts().any { "Estimated position" in it || "Position paused" in it || "not updating" in it })
        } finally { scene.close() }
    }

    /** Map-only pixels exclude the hint, attribution, recenter button and teal overlay. */
    @OptIn(ExperimentalComposeUiApi::class)
    private fun mapPixels(scene: ImageComposeScene, frameNanos: Long): IntArray =
        scene.render(frameNanos).use { image ->
            image.encodeToData()!!.use { data ->
                ByteArrayInputStream(data.bytes).use { input ->
                    ImageIO.read(input).getRGB(10, 180, 260, 45, null, 0, 260)
                }
            }
        }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun panelTracksTheFingerAndKeepsPartialHeightsWithoutTapOrReleaseSnapping() {
        val scene = ImageComposeScene(412, 720) {
            RNDTransitTheme {
                ActiveMapContent(reviewedHistoryPreviewTrip, 0.4f, true, {}, {})
            }
        }
        var time = 0L
        fun render(frames: Int = 40) { repeat(frames) { time += 16_000_000; scene.render(time).close() } }
        fun all(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::all)
        fun nodes() = scene.semanticsOwners.flatMap { all(it.rootSemanticsNode) }
        fun panel() = nodes().first {
            it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Trip progress panel") == true
        }
        fun handle() = nodes().first {
                it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Trip panel handle") == true
        }
        fun drag(distance: Float, fromBody: Boolean = false): Float {
            val start = if (fromBody) nodes().first {
                it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text.startsWith("Current trip") } == true
            }.boundsInRoot.center else handle().boundsInRoot.center
            scene.sendPointerEvent(PointerEventType.Press, start, timeMillis = time / 1_000_000)
            for (step in 1..6) {
                time += 20_000_000
                scene.sendPointerEvent(PointerEventType.Move,
                    start.copy(y = start.y + distance * step / 6),
                    timeMillis = time / 1_000_000)
                render(1)
            }
            val heightBeforeRelease = panel().boundsInRoot.height
            scene.sendPointerEvent(PointerEventType.Release,
                start.copy(y = start.y + distance), timeMillis = time / 1_000_000 + 1)
            render()
            assertEquals(heightBeforeRelease, panel().boundsInRoot.height, 1f, "Releasing must preserve the dragged height")
            return heightBeforeRelease
        }
        try {
            render()
            val recenter = nodes().first {
                it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Recenter on current position") == true
            }.boundsInRoot.center
            scene.sendPointerEvent(PointerEventType.Press, recenter, timeMillis = time / 1_000_000)
            scene.sendPointerEvent(PointerEventType.Release, recenter, timeMillis = time / 1_000_000 + 1)
            render(50)
            val cameraBeforePanelDrag = mapPixels(scene, time)
            val expandedBounds = panel().boundsInRoot
            val titleTop = nodes().first {
                it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text.startsWith("Current trip") } == true
            }.boundsInRoot.top
            assertTrue(titleTop - expandedBounds.top < 40f, "The panel handle/header must stay compact")
            val point = handle().boundsInRoot.center
            assertNull(handle().config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.OnClick))
            scene.sendPointerEvent(PointerEventType.Press, point, timeMillis = time / 1_000_000)
            scene.sendPointerEvent(PointerEventType.Release, point, timeMillis = time / 1_000_000 + 1)
            render()
            assertEquals(expandedBounds.height, panel().boundsInRoot.height, "Tapping must not toggle the panel")
            val firstPartialHeight = drag(60f)
            assertTrue(firstPartialHeight < expandedBounds.height && firstPartialHeight > expandedBounds.height / 2)
            val secondPartialHeight = drag(40f, fromBody = true)
            assertTrue(secondPartialHeight < firstPartialHeight, "Dragging the panel body must also resize it")
            assertTrue(cameraBeforePanelDrag.contentEquals(mapPixels(scene, time)),
                "Resizing the panel must not pan, zoom, or recenter the map while follow is enabled")
            scene.render(time).use { image ->
                val folder = File("build/planner-previews").apply { mkdirs() }
                image.encodeToData()!!.use { File(folder, "Active-trip-partly-open.png").writeBytes(it.bytes) }
            }
            val raisedPartialHeight = drag(-35f)
            assertTrue(raisedPartialHeight > secondPartialHeight && raisedPartialHeight < expandedBounds.height)
            drag(-400f)
            assertEquals(expandedBounds.height, panel().boundsInRoot.height, 1f)
            assertTrue(nodes().any {
                it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == "Finish trip" } == true
            })
        } finally { scene.close() }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun recenterMovesThroughIntermediateCameraFramesInsteadOfJumping() {
        val scene = ImageComposeScene(412, 720) {
            RNDTransitTheme { ActiveMapContent(reviewedHistoryPreviewTrip, 0.4f, true, {}, {}) }
        }
        var time = 0L
        fun render(frames: Int) { repeat(frames) { time += 16_000_000; scene.render(time).close() } }
        fun all(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::all)
        try {
            render(30)
            val before = mapPixels(scene, time)
            val recenter = scene.semanticsOwners.flatMap { all(it.rootSemanticsNode) }.first {
                it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Recenter on current position") == true
            }.boundsInRoot.center
            scene.sendPointerEvent(PointerEventType.Press, recenter, timeMillis = time / 1_000_000)
            scene.sendPointerEvent(PointerEventType.Release, recenter, timeMillis = time / 1_000_000 + 1)
            render(8)
            val during = mapPixels(scene, time)
            render(40)
            val after = mapPixels(scene, time)
            assertFalse(before.contentEquals(after), "The fixture must require a camera move")
            assertFalse(before.contentEquals(during), "Recentering must begin moving before its final frame")
            assertFalse(during.contentEquals(after), "Recentering must pass through intermediate positions")
        } finally { scene.close() }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun directionsDropDownThroughIntermediateHeightsAndRetractToTheSameCompactHint() {
        val scene = ImageComposeScene(360, 720) {
            RNDTransitTheme { ActiveMapContent(guidanceTestTrip(), 0f, true, {}, {}) }
        }
        var time = 0L
        fun render(frames: Int) { repeat(frames) { time += 16_000_000; scene.render(time).close() } }
        fun all(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::all)
        fun hint() = scene.semanticsOwners.flatMap { all(it.rootSemanticsNode) }.first {
            it.config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.OnClick)?.label in
                setOf("Expand directions", "Collapse directions")
        }
        fun tapHint() {
            val point = hint().boundsInRoot.center
            scene.sendPointerEvent(PointerEventType.Press, point, timeMillis = time / 1_000_000)
            scene.sendPointerEvent(PointerEventType.Release, point, timeMillis = time / 1_000_000 + 1)
        }
        try {
            render(24)
            val compact = hint().boundsInRoot
            tapHint()
            render(6)
            val dropping = hint().boundsInRoot
            render(24)
            val expanded = hint().boundsInRoot
            assertEquals(compact.top, expanded.top, "Expansion must stay attached to the top")
            assertTrue(dropping.height > compact.height && dropping.height < expanded.height,
                "The hint must visibly grow downward instead of jumping to full height")
            scene.render(time).use { image ->
                val folder = File("build/planner-previews").apply { mkdirs() }
                image.encodeToData()!!.use { File(folder, "Directions-drop-down-expanded.png").writeBytes(it.bytes) }
            }
            tapHint()
            render(6)
            val retracting = hint().boundsInRoot
            assertTrue(retracting.height > compact.height && retracting.height < expanded.height)
            render(24)
            assertEquals(compact.height, hint().boundsInRoot.height, 1f)
        } finally { scene.close() }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun guidanceFollowsTheVisibleLegAndKeepsItsLastPositionWhenLocationIsStale() {
        var progress by mutableStateOf(0f)
        var location by mutableStateOf(TripLocationState.Sample)
        val trip = guidanceTestTrip()
        val scene = ImageComposeScene(320, 640) {
            RNDTransitTheme {
                ActiveMapContent(trip, progress, true, {}, {}, locationState = location)
            }
        }
        var time = 0L
        fun render(name: String? = null) {
            repeat(24) { time += 16_000_000; scene.render(time).close() }
            if (name != null) scene.render(time).use { image ->
                val folder = File("build/planner-previews").apply { mkdirs() }
                image.encodeToData()!!.use { File(folder, name).writeBytes(it.bytes) }
            }
        }
        fun all(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::all)
        fun texts() = scene.semanticsOwners.flatMap { all(it.rootSemanticsNode) }
            .flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }.map { it.text }
        try {
            render("Leg-guidance-compact-small.png")
            assertContains(texts(), "Start on Rue Sherbrooke")
            assertFalse(texts().any { it.startsWith("Section ") || it.startsWith("Next:") })
            val expand = scene.semanticsOwners.flatMap { all(it.rootSemanticsNode) }.first {
                it.config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.OnClick)?.label == "Expand directions"
            }
            expand.config[androidx.compose.ui.semantics.SemanticsActions.OnClick].action!!.invoke()
            render("Leg-guidance-walk-small.png")
            assertContains(texts(), "Section 1 of 3: Walk")
            assertContains(texts(), "Next: In 50 m, turn left onto Rue Saint-Denis")
            progress = 0.5f
            render("Leg-guidance-bus-small.png")
            assertContains(texts(), "Section 2 of 3: Bus 24")
            assertContains(texts(), "Now: Ride Bus 24 towards East")
            location = TripLocationState.Stale
            progress = 0.9f
            render()
            assertContains(texts(), "Section 2 of 3: Bus 24")
            location = TripLocationState.Sample
            render()
            assertContains(texts(), "Section 3 of 3: Walk")
        } finally { scene.close() }
    }

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
                    "Expand directions"
            }
            section.config[androidx.compose.ui.semantics.SemanticsActions.OnClick].action!!.invoke()
            render()
            assertEquals(0, informationRequests)
            assertTrue(nodes().any {
                it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == "Sample turn" } == true
            })
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
                    0 -> CurrentTripContent(trip, progress, true, null, {}, {},
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

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun cancelPopupAsksWhetherToSaveTheTrip() {
        val choices = mutableListOf<Boolean>()
        val scene = ImageComposeScene(412, 720) {
            RNDTransitTheme {
                ActiveMapContent(reviewedHistoryPreviewTrip, 0.4f, true, {}, { keepInHistory -> choices += keepInHistory })
            }
        }
        var time = 0L
        fun render(name: String? = null) {
            repeat(16) { time += 16_000_000; scene.render(time).close() }
            if (name != null) scene.render(time).use { image ->
                val folder = File("build/planner-previews").apply { mkdirs() }
                image.encodeToData()!!.use { File(folder, name).writeBytes(it.bytes) }
            }
        }
        fun all(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::all)
        fun node(label: String) = scene.semanticsOwners.flatMap { all(it.rootSemanticsNode) }.firstOrNull {
            it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == label } == true
        }
        fun click(label: String) {
            val point = assertNotNull(node(label), "Missing \"$label\"").boundsInRoot.center
            scene.sendPointerEvent(PointerEventType.Press, point, timeMillis = time / 1_000_000)
            scene.sendPointerEvent(PointerEventType.Release, point, timeMillis = time / 1_000_000 + 1)
            render()
        }
        try {
            render()
            click("Cancel")
            render("Cancel-confirmation.png")
            assertNotNull(node("Cancel this adventure?"))
            click("Cancel and save to History")
            assertNull(node("Cancel this adventure?"))

            click("Cancel")
            click("Cancel without saving")

            click("Cancel")
            click("Keep travelling")
            assertNull(node("Cancel this adventure?"))

            assertEquals(listOf(true, false), choices)
        } finally { scene.close() }
    }
}
