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
import com.example.rnd_transit_mtl.model.ids.TransportId
import com.example.rnd_transit_mtl.model.ids.RouteId
import com.example.rnd_transit_mtl.model.ids.ZoneId
import com.example.rnd_transit_mtl.model.trip.TransitTiming
import com.example.rnd_transit_mtl.model.trip.LegNavigation
import com.example.rnd_transit_mtl.model.trip.TripTransportSnapshot
import com.example.rnd_transit_mtl.model.trip.TripRouteSnapshot
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/** Verifies the supplied loading/preview controls render and dispatch their separate actions. */
class TripProposalContentTest {
    @OptIn(ExperimentalComposeUiApi::class)
    @Test fun transitPreviewShowsOneCompactDepartureRowWithoutDiagnostics() {
        val bus = TransportId("bus")
        val route = RouteId("bus:411")
        val now = Clock.System.now()
        val departure = now + 20.minutes
        val timing = TransitTiming(
            departure.toString(), (departure + 15.minutes).toString(),
            receivedAt = now.toEpochMilliseconds(), alerts = listOf("Test service alert"),
            departureDelay = "PT1M", arrivalDelay = "PT2M")
        val trip = reviewedHistoryPreviewTrip.copy(
            legs = reviewedHistoryPreviewTrip.legs.map { it.copy(transportId = bus, routeId = route,
                transitTiming = timing, navigation = LegNavigation("411")) },
            selectedTransports = listOf(TripTransportSnapshot(bus, "Bus", true)),
            selectedRoutes = listOf(TripRouteSnapshot(route, bus, "411")),
            selectedZoneIds = listOf(ZoneId("A")),
            selectedZonesByTransport = emptyMap())
        val scene = ImageComposeScene(412, 720) {
            RNDTransitTheme { TripProposalContent(trip, {}, {}, {}) }
        }
        try {
            repeat(8) { scene.render((it + 1) * 16_000_000L).close() }
            fun all(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::all)
            val texts = scene.semanticsOwners.flatMap { all(it.rootSemanticsNode) }
                .flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }.map { it.text }
            assertEquals(1, texts.count { it.matches(Regex("Bus 411 in [0-9]+ min \\([0-9]{2}:[0-9]{2}\\)")) })
            assertEquals(1, texts.count { it == "Start Trip" })
            assertFalse(texts.any { it.contains("delay", true) || it.contains("alert", true) || it.startsWith("Scheduled:") })
            scene.render(160_000_000L).use { image ->
                val folder = File("build/planner-previews").apply { mkdirs() }
                image.encodeToData()!!.use { File(folder, "Trip-proposal-compact-transit.png").writeBytes(it.bytes) }
            }
        } finally { scene.close() }
    }

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
            assertEquals(1, texts.count { it == "Starting point" })
            assertEquals(1, texts.count { it == proposal.selectedTransports.joinToString(" + ") { mode -> mode.label } })
            val editor = scene.semanticsOwners.flatMap { all(it.rootSemanticsNode) }.first {
                it.config.getOrNull(SemanticsActions.SetText) != null
            }
            assertEquals(proposal.title, editor.config[SemanticsProperties.EditableText].text)
            editor.config[SemanticsActions.SetText].action?.invoke(AnnotatedString("  Evening walk  "))
            render("Trip-proposal-name-draft.png")
            assertEquals("Evening walk", proposal.title)
            click("Generate another")
            click("Edit settings")
            click("Start Trip")
            assertEquals(listOf("cancel", "another", "edit", "start:Evening walk"), events)
        } finally { scene.close() }
    }
}
