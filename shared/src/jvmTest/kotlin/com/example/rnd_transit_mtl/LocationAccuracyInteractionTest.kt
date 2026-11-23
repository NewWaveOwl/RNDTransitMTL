package com.example.rnd_transit_mtl

import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.example.rnd_transit_mtl.View.current_trip.ActiveMapContent
import com.example.rnd_transit_mtl.View.current_trip.TripLocationState
import com.example.rnd_transit_mtl.View.main.MainPlannerContent
import com.example.rnd_transit_mtl.View.settings.SettingsContent
import com.example.rnd_transit_mtl.ViewModels.Settings.SettingsUIState
import com.example.rnd_transit_mtl.data.location.*
import com.example.rnd_transit_mtl.model.geo.GeoPoint
import com.example.rnd_transit_mtl.state.debug.DebugModeState
import com.example.rnd_transit_mtl.state.debug.LocalDebugModeState
import com.example.rnd_transit_mtl.ui.global_ui_ellements.location.LocationAccuracyNotice
import com.example.rnd_transit_mtl.ui.theme.RNDTransitTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import java.io.File
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class LocationAccuracyInteractionTest {
    private class Location : PlannerLocation {
        var now = 1_000_000L
        override val accuracyMonitor = LocationAccuracyMonitor(nowEpochMillis = { now })
        override val fix = LocationUpdate.Fix(GeoPoint(45.5, -73.6), 120.0, now)
        override val containsPoint: (GeoPoint) -> Boolean = { true }
        var appSettings = 0
        var deviceSettings = 0
        var requests = 0
        var canOpen = true
        override fun request(onResult: ((LocationUpdate.Fix?) -> Unit)?) { requests++ }
        override fun openAppLocationSettings(): Boolean { appSettings++; return canOpen }
        override fun openDeviceLocationSettings(): Boolean { deviceSettings++; return canOpen }
        fun weakSignal() {
            accuracyMonitor.updateAccess(LocationPermissionLevel.Precise, true)
            repeat(4) {
                accuracyMonitor.receive(fix.copy(timestampEpochMillis = now))
                if (it < 3) now += 5_000L
            }
        }
    }

    private class Host(location: Location, debug: DebugModeState? = null, content: @Composable () -> Unit) : AutoCloseable {
        private val scene = ImageComposeScene(412, 900) {
            CompositionLocalProvider(LocalPlannerLocation provides location, LocalDebugModeState provides debug) {
                RNDTransitTheme { content() }
            }
        }
        private var frame = 0L
        suspend fun render() { repeat(25) { yield(); frame += 16_000_000L; scene.render(frame).close() } }
        private fun all(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::all)
        private fun nodes() = scene.semanticsOwners.flatMap { all(it.rootSemanticsNode) }
        fun texts() = nodes().flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }.map { it.text }
        fun descriptions() = nodes().flatMap { it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }
        suspend fun click(text: String) {
            val center = nodes().first { it.config.getOrNull(SemanticsProperties.Text)?.any { it.text == text } == true }.boundsInRoot.center
            scene.sendPointerEvent(PointerEventType.Press, center, timeMillis = frame / 1_000_000L)
            scene.sendPointerEvent(PointerEventType.Release, center, timeMillis = frame / 1_000_000L + 1)
            render()
        }
        fun snapshot(name: String) {
            val directory = File("build/planner-previews").apply { mkdirs() }
            scene.render(frame).use { image -> image.encodeToData()!!.use { File(directory, name).writeBytes(it.bytes) } }
        }
        override fun close() = scene.close()
    }

    @Test
    fun settingsOpensSharedLiveDiagnosticsAndBothSystemActionsWithoutRequestingGps() = runBlocking(Dispatchers.Main) {
        val location = Location()
        location.accuracyMonitor.updateAccess(LocationPermissionLevel.Approximate, true)
        location.accuracyMonitor.receive(location.fix)
        Host(location) {
            SettingsContent(SettingsUIState(username = "Walker"), "", {}, {}, {}, {}, {})
        }.use { host ->
            host.render()
            host.click("Improve location accuracy")
            assertContains(host.texts(), "Location permission: Approximate")
            assertContains(host.texts(), "Estimated accuracy: about 120 m")
            assertContains(host.texts(), "Last reading: Just now")
            host.snapshot("Location-accuracy-settings.png")
            host.click("Open app settings")
            host.click("Open location settings")
            assertEquals(1, location.appSettings)
            assertEquals(1, location.deviceSettings)
            assertEquals(0, location.requests)
            host.click("Done")
            assertFalse(host.texts().contains("Open app settings"))
        }
    }

    @Test
    fun actualTripNoticeOpensHelpAndDismissesWithoutRemovingTheUserMarker() = runBlocking(Dispatchers.Main) {
        val location = Location().apply { weakSignal() }
        Host(location) {
            ActiveMapContent(guidanceTestTrip(), 0.1f, true, {}, {}, simulationEnabled = false,
                locationState = TripLocationState.LowAccuracy, userPosition = location.fix.point, userUncertaintyMeters = 120.0)
        }.use { host ->
            host.render()
            assertContains(host.texts(), "Location accuracy is low")
            assertFalse(host.texts().contains("Location is not updating"))
            host.snapshot("Location-accuracy-trip-notice.png")
            host.click("Improve accuracy")
            assertContains(host.texts(), "Improve location accuracy")
            host.click("Done")
            host.click("Dismiss")
            assertFalse(host.texts().contains("Location accuracy is low"))
            assertTrue(host.descriptions().any { "Directed yellow position pointer" in it && "Location uncertainty circle" in it })
        }
    }

    @Test
    fun permissionDowngradeOnHomeOffersSettingsAndHandlesDevicesThatCannotOpenThem() = runBlocking(Dispatchers.Main) {
        val location = Location()
        location.accuracyMonitor.updateAccess(LocationPermissionLevel.Precise, true)
        Host(location) {
            MainPlannerContent(minutes = 30, onMinutesChange = {}, transportTypes = emptyList(),
                selectedTransportIds = emptyList(), selectedZonesByTransport = emptyMap(),
                onToggleTransport = {}, onToggleZone = { _, _ -> }, walkingPreference = "Normal", bikeSpeedKmh = 15,
                onBikeSpeedChange = {}, selectedAttractionIds = emptyList(), onWalkingPreferenceChange = {},
                onToggleAttraction = {}, validationMessage = "", actionLabel = "GO", actionEnabled = true,
                inputsEnabled = true, activeTripSummary = null, pendingReviewSummary = null,
                onPrimaryAction = {}, onOpenDetailedSettings = {})
        }.use { host ->
            host.render()
            assertFalse(host.texts().contains("Precise location is disabled"))
            location.accuracyMonitor.updateAccess(LocationPermissionLevel.Approximate, true)
            host.render()
            assertContains(host.texts(), "Precise location is disabled")
            host.snapshot("Location-accuracy-home-notice.png")
            location.canOpen = false
            host.click("Open settings")
            assertEquals(1, location.appSettings)
            assertContains(host.texts(), "Couldn't open settings. Open your phone's Settings app.")
            location.accuracyMonitor.updateAccess(LocationPermissionLevel.Precise, true)
            host.render()
            assertFalse(host.texts().contains("Precise location is disabled"))
        }
    }

    @Test
    fun noticesAreHiddenDuringSimulationAndOnInactiveScreens() = runBlocking(Dispatchers.Main) {
        val location = Location().apply { weakSignal() }
        val debug = DebugModeState(simulationEnabled = true)
        var active by mutableStateOf(true)
        Host(location, debug) { LocationAccuracyNotice(enabled = active) }.use { host ->
            host.render()
            assertFalse(host.texts().contains("Location accuracy is low"))
            debug.selectSimulation(false)
            host.render()
            assertContains(host.texts(), "Location accuracy is low")
            active = false
            host.render()
            assertFalse(host.texts().contains("Location accuracy is low"))
        }
    }
}
