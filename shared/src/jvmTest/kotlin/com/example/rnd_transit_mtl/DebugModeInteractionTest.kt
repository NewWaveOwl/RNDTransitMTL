package com.example.rnd_transit_mtl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.example.rnd_transit_mtl.View.current_trip.CurrentTripScreen
import com.example.rnd_transit_mtl.layout.SharedTopBarContent
import com.example.rnd_transit_mtl.data.location.LocalPlannerLocation
import com.example.rnd_transit_mtl.data.location.PlannerLocation
import com.example.rnd_transit_mtl.data.location.LocationUpdate
import com.example.rnd_transit_mtl.model.geo.GeoPoint
import kotlinx.coroutines.flow.MutableStateFlow
import com.example.rnd_transit_mtl.navigation.LocalTripNavigation
import com.example.rnd_transit_mtl.navigation.MainScreenKey
import com.example.rnd_transit_mtl.navigation.Navigator
import com.example.rnd_transit_mtl.navigation.ProfileScreenKey
import com.example.rnd_transit_mtl.navigation.HistoryScreenKey
import com.example.rnd_transit_mtl.navigation.ScreenKey
import com.example.rnd_transit_mtl.navigation.TripNavigation
import com.example.rnd_transit_mtl.state.debug.DebugModeState
import com.example.rnd_transit_mtl.state.debug.LocalDebugModeState
import com.example.rnd_transit_mtl.state.trip.LocalTripsStore
import com.example.rnd_transit_mtl.state.trip.TripsStore
import com.example.rnd_transit_mtl.ui.global_ui_ellements.debug.DebugModeDialog
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.LocalTransitMap
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.TransitMapData
import com.example.rnd_transit_mtl.ui.theme.RNDTransitTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Verifies the real logo gesture, mode chooser, and opt-in active-trip playback. */
@OptIn(ExperimentalComposeUiApi::class)
class DebugModeInteractionTest {
    private class UiHost(content: @Composable () -> Unit) : AutoCloseable {
        private val scene = ImageComposeScene(412, 900, content = content)
        private var frameNanos = 0L

        suspend fun render(frames: Int = 6) {
            repeat(frames) {
                yield()
                frameNanos += 16_000_000
                scene.render(frameNanos).close()
            }
        }

        private fun all(node: SemanticsNode): List<SemanticsNode> =
            listOf(node) + node.children.flatMap(::all)

        private fun matching(label: String) = scene.semanticsOwners
            .flatMap { all(it.rootSemanticsNode) }.filter { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == label } == true ||
                    node.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label) == true
            }

        fun has(label: String) = matching(label).isNotEmpty()

        fun snapshot(name: String) {
            val folder = File("build/planner-previews").apply { mkdirs() }
            scene.render(frameNanos).use { image ->
                image.encodeToData()!!.use { File(folder, name).writeBytes(it.bytes) }
            }
        }

        suspend fun click(label: String) {
            val point = matching(label).firstOrNull()?.boundsInRoot?.center
                ?: error("Missing action: $label")
            scene.sendPointerEvent(PointerEventType.Press, point, timeMillis = frameNanos / 1_000_000)
            scene.sendPointerEvent(PointerEventType.Release, point, timeMillis = frameNanos / 1_000_000 + 1)
            render()
        }

        override fun close() = scene.close()
    }

    @Test
    fun logoRequiresThreeQuickTapsAndOffersBothModesWithoutNavigating() = runBlocking(Dispatchers.Main) {
        val debugMode = DebugModeState()
        var homeRequests = 0
        var currentKey by mutableStateOf<ScreenKey>(ProfileScreenKey)
        UiHost {
            CompositionLocalProvider(LocalDebugModeState provides debugMode) {
                RNDTransitTheme {
                    SharedTopBarContent(currentKey) { homeRequests++ }
                    DebugModeDialog(debugMode)
                }
            }
        }.use { host ->
            host.render()
            host.click("Return to Main")
            host.click("Return to Main")
            assertFalse(debugMode.isModeChooserVisible)
            delay(400)
            host.render()
            assertEquals(1, homeRequests)
            assertFalse(debugMode.simulationEnabled)

            repeat(3) { host.click("Return to Main") }
            assertTrue(host.has("Debug mode"))
            assertEquals(1, homeRequests)
            delay(400)
            host.render()
            assertEquals(1, homeRequests)
            host.snapshot("Debug-mode-dialog.png")
            host.click("Trip simulation")
            assertTrue(debugMode.simulationEnabled)
            assertFalse(host.has("Debug mode"))
            host.render(20)

            repeat(3) { host.click("Return to Main") }
            assertTrue(debugMode.isModeChooserVisible)
            host.click("Normal app use")
            assertFalse(debugMode.simulationEnabled)
            assertEquals(1, homeRequests)
            host.render(20)

            host.click("Return to Main")
            delay(400)
            host.render()
            assertEquals(2, homeRequests)
            assertFalse(debugMode.isModeChooserVisible)

            host.click("Return to Main")
            currentKey = HistoryScreenKey
            host.render()
            delay(400)
            host.render()
            assertEquals(2, homeRequests)
        }
    }

    private class TripHost(private val location: PlannerLocation? = null) : AutoCloseable {
        val debugMode = DebugModeState()
        val store = TripsStore(nowEpochMillis = { 2_000L })
        private val trip = guidanceTestTrip()
        private val models = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
        private val lifecycleOwner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this).apply {
                currentState = Lifecycle.State.RESUMED
            }
        }
        private val navigation = TripNavigation(Navigator(NavBackStack<NavKey>(MainScreenKey)), store)
        var mapData = TransitMapData()
            private set
        var plannerRequests = 0
            private set
        val ui: UiHost

        init {
            store.start(trip)
            ui = UiHost {
                CompositionLocalProvider(
                    LocalDebugModeState provides debugMode,
                    LocalViewModelStoreOwner provides models,
                    LocalPlannerLocation provides location,
                    LocalTripsStore provides store,
                    LocalTripNavigation provides navigation,
                    LocalLifecycleOwner provides lifecycleOwner,
                    LocalTransitMap provides { data, _ -> SideEffect { mapData = data } },
                ) {
                    RNDTransitTheme {
                        CurrentTripScreen(
                            trip, isDestinationActive = true, onReviewRequested = { true },
                            onLeave = {}, onReturnToPlanner = { plannerRequests++ },
                        )
                    }
                }
            }
        }

        override fun close() {
            ui.close()
            models.viewModelStore.clear()
        }
    }

    @Test
    fun normalUseNeverSimulatesAndStillAllowsCancellation() = runBlocking(Dispatchers.Main) {
        TripHost().use { host ->
            host.ui.render()
            delay(80)
            host.ui.render()
            assertEquals(0L, host.store.elapsedMillis)
            assertTrue(host.store.completedTrips.isEmpty())
            assertNull(host.mapData.progress)
            assertFalse(host.ui.has("Location unavailable"))
            assertTrue(host.ui.has("Trip progress paused"))
            host.ui.snapshot("Normal-trip-paused.png")
            assertFalse(host.ui.has("Finish trip"))
            host.ui.click("Cancel")
            host.ui.click("Cancel without saving")
            assertNull(host.store.activeTrip)
            assertEquals(1, host.plannerRequests)
        }
    }

    @Test
    fun normalTripShowsRealGpsAndRecenterRequestsPermissionThenFollowsPosition() = runBlocking(Dispatchers.Main) {
        val readings = MutableStateFlow<LocationUpdate>(LocationUpdate.PermissionDenied)
        var requests = 0
        val location = object : PlannerLocation {
            override val fix get() = readings.value as? LocationUpdate.Fix
            override val automaticOriginUpdates = readings
            override val containsPoint: (GeoPoint) -> Boolean = { true }
            override fun request(onResult: ((LocationUpdate.Fix?) -> Unit)?) { requests++ }
        }
        TripHost(location).use { host ->
            host.ui.render()
            assertTrue(host.ui.has("Location permission needed"))
            val initialRequests = requests
            host.ui.click("Recenter on current position")
            assertEquals(initialRequests + 1, requests)
            assertTrue(host.mapData.followPosition)
            assertTrue(host.mapData.recenterRequest > 0)
            assertFalse(host.mapData.simulatePosition)
            val origin = guidanceTestTrip().origin.point
            readings.value = LocationUpdate.Fix(origin, 5.0)
            host.ui.render()
            assertEquals(origin, host.mapData.userPosition)
            assertTrue(host.ui.has("Start on Rue Sherbrooke"))
            val moved = GeoPoint(45.5001, -73.6)
            readings.value = LocationUpdate.Fix(moved, 5.0)
            host.ui.render()
            assertFalse(moved == host.mapData.userPosition, "Accepted GPS changes animate rather than teleport")
            host.ui.render(40)
            assertEquals(moved, host.mapData.userPosition)
            assertTrue(host.ui.has("Continue on Rue Sherbrooke"))
            assertTrue(host.store.progress > 0f)
            assertEquals(0L, host.store.elapsedMillis)
            host.mapData.onManualCameraMove()
            host.ui.render()
            assertFalse(host.mapData.followPosition)
            host.ui.click("Recenter on current position")
            assertTrue(host.mapData.followPosition)
            assertEquals(moved, host.mapData.userPosition)
        }
    }

    @Test
    fun simulationPausesInTheChooserAndNormalUseThenResumesTheSameTrip() = runBlocking(Dispatchers.Main) {
        TripHost().use { host ->
            val originalTrip = host.store.activeTrip
            host.debugMode.selectSimulation(true)
            host.ui.render()
            delay(80)
            host.ui.render()
            assertTrue(host.store.elapsedMillis > 0)
            assertTrue(host.ui.has("Finish trip"))

            host.debugMode.openModeChooser()
            host.ui.render()
            val paused = host.store.elapsedMillis
            delay(80)
            host.ui.render()
            assertEquals(paused, host.store.elapsedMillis)
            host.debugMode.dismissModeChooser()
            host.ui.render()
            delay(80)
            host.ui.render()
            assertTrue(host.store.elapsedMillis > paused)

            host.debugMode.selectSimulation(false)
            host.ui.render()
            val stopped = host.store.elapsedMillis
            delay(80)
            host.ui.render()
            assertEquals(stopped, host.store.elapsedMillis)
            assertEquals(originalTrip, host.store.activeTrip)
            assertNull(host.mapData.progress)
            assertFalse(host.ui.has("Finish trip"))

            // A restored trip near the end must also remain unfinished in normal use.
            host.store.updateElapsed(originalTrip!!.id, 9_990L)
            host.ui.render()
            delay(80)
            host.ui.render()
            assertEquals(9_990L, host.store.elapsedMillis)
            assertTrue(host.store.completedTrips.isEmpty())

            host.debugMode.selectSimulation(true)
            host.ui.render()
            delay(80)
            host.ui.render()
            assertNull(host.store.activeTrip)
            assertEquals(originalTrip.id, host.store.completedTrips.single().id)
        }
    }
}
