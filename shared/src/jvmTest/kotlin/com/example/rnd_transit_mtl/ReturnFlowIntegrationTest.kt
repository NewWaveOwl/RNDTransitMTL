package com.example.rnd_transit_mtl

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.*
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import com.example.rnd_transit_mtl.View.current_trip.CurrentTripScreen
import com.example.rnd_transit_mtl.data.generation.*
import com.example.rnd_transit_mtl.model.geo.GeoPoint
import com.example.rnd_transit_mtl.model.trip.*
import com.example.rnd_transit_mtl.navigation.*
import com.example.rnd_transit_mtl.state.trip.*
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.LocalTransitMap
import com.example.rnd_transit_mtl.ui.theme.RNDTransitTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.io.File
import kotlin.test.*

/** Runs completion -> request -> preview -> independent active return -> original review. */
@OptIn(ExperimentalComposeUiApi::class)
class ReturnFlowIntegrationTest {
    private class BackInput : NavigationEventInput() { fun back() = dispatchOnBackCompleted() }
    private class Host(private val failFirst: Boolean = false, width: Int = 412) : AutoCloseable {
        val store = TripsStore { 200_000L }.also {
            it.start(guidanceTestTrip()); it.updateElapsed(guidanceTestTrip().id, TRIP_SIMULATION_DURATION_MILLIS)
        }
        val original = assertNotNull(store.findCompleted(guidanceTestTrip().id))
        val navigator = Navigator(NavBackStack<NavKey>(MainScreenKey, CurrentTripScreenKey(original)))
        private val navigation = TripNavigation(navigator, store)
        val requests = mutableListOf<BackendGenerationRequest>()
        private val models = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
        private val lifecycle = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        }
        private val backInput = BackInput()
        private val backend = BackendTripGenerator(post = { body, _ ->
            val request = Json.decodeFromString<BackendGenerationRequest>(body)
            requests += request
            assertEquals(original.origin, request.destination)
            assertEquals(original.destination, request.origin)
            if (failFirst && requests.size == 1) """{"status":"no_trip"}"""
            else response(request)
        }, idFactory = { "return-${requests.size}" }, now = { 200_000L })
        private val scene = ImageComposeScene(width, 716) {
            val dispatcher = rememberNavigationEventDispatcherOwner(parent = null)
            DisposableEffect(dispatcher) {
                dispatcher.navigationEventDispatcher.addInput(backInput)
                onDispose { dispatcher.navigationEventDispatcher.removeInput(backInput) }
            }
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides dispatcher,
                LocalLifecycleOwner provides lifecycle, LocalViewModelStoreOwner provides models,
                LocalTripsStore provides store, LocalNavigator provides navigator,
                LocalTripNavigation provides navigation, LocalBackendTripGenerator provides backend,
                LocalTransitMap provides { _, _ -> }) {
                RNDTransitTheme {
                    val current = navigator.current
                    if (current is CurrentTripScreenKey) key(current.trip.id) {
                        CurrentTripScreen(current.trip, true, { navigation.openInitialReview(it) },
                            {}, { navigation.returnToPlanner() })
                    } else Text("Review saved outbound trip")
                }
            }
        }
        private var time = 0L
        suspend fun render() { repeat(55) { yield(); time += 16_000_000; scene.render(time).close() } }
        private fun all(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::all)
        private fun nodes() = scene.semanticsOwners.flatMap { all(it.rootSemanticsNode) }
        fun has(label: String) = nodes().any { it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == label } == true }
        suspend fun click(label: String) {
            val node = nodes().first {
                it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == label } == true ||
                    it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label) == true
            }
            scene.sendPointerEvent(PointerEventType.Press, node.boundsInRoot.center, timeMillis = time / 1_000_000)
            scene.sendPointerEvent(PointerEventType.Release, node.boundsInRoot.center, timeMillis = time / 1_000_000 + 1)
            render()
        }
        suspend fun scrollPanelToBottom() {
            nodes().first { it.config.getOrNull(SemanticsActions.ScrollBy) != null }
                .config[SemanticsActions.ScrollBy].action!!.invoke(0f, 10_000f)
            render()
        }
        suspend fun back() { backInput.back(); render() }
        fun snapshot(name: String) {
            scene.render(time + 1).use { image ->
                val folder = File("build/planner-previews").apply { mkdirs() }
                image.encodeToData()!!.use { File(folder, name).writeBytes(it.bytes) }
            }
        }
        private fun response(request: BackendGenerationRequest): String {
            val destination = requireNotNull(request.destination)
            val bend = GeoPoint((request.origin.point.latitude + destination.point.latitude) / 2, -73.601)
            var lat = 0; var lon = 0
            fun delta(number: Int): String {
                var value = if (number < 0) (number shl 1).inv() else number shl 1
                return buildString {
                    while (value >= 32) { append(((32 or (value and 31)) + 63).toChar()); value = value ushr 5 }
                    append((value + 63).toChar())
                }
            }
            val polyline = listOf(request.origin.point, bend, destination.point).joinToString("") {
                val a = (it.latitude * 1e5).toInt(); val b = (it.longitude * 1e5).toInt()
                val text = delta(a - lat) + delta(b - lon); lat = a; lon = b; text
            }
            val leg = buildJsonObject {
                put("mode", "WALK"); put("distance", 500); put("duration", 600)
                put("legGeometry", buildJsonObject { put("length", 3); put("points", polyline) })
            }
            val otp = buildJsonObject { put("data", buildJsonObject { put("planConnection", buildJsonObject {
                put("routingErrors", JsonArray(emptyList()))
                put("edges", buildJsonArray { add(buildJsonObject { put("node", buildJsonObject {
                    put("duration", 600); put("waitingTime", 0); put("legs", buildJsonArray { add(leg) })
                }) }) })
            }) }) }
            return Json.encodeToString(BackendGenerationResponse("success", origin = request.origin,
                destination = destination, elapsedSeconds = 600, otp = otp))
        }
        override fun close() { scene.close(); models.viewModelStore.clear() }
    }

    @Test fun splitActionsRemainVisibleOnNarrowScreens() = runBlocking(Dispatchers.Main) {
        Host(width = 320).use { host ->
            host.render()
            assertTrue(host.has("Return trip / Fastest trip"))
            assertTrue(host.has("Same transport"))
            host.scrollPanelToBottom()
            host.snapshot("Return-split-button-320.png")
            host.click("Same transport")
            assertFalse(host.requests.single().compareModes)
            assertTrue(host.has("Back with the same transport"))
        }
    }

    @Test fun acceptsARealReturnAndReviewsTheOriginalTripAfterGettingBack() = runBlocking(Dispatchers.Main) {
        Host().use { host ->
            host.render()
            host.snapshot("Return-split-button.png")
            host.click("Return trip / Fastest trip")
            assertTrue(host.has("Start return trip"))
            assertEquals(host.original, host.store.findCompleted(host.original.id))
            assertEquals(host.original.id, host.store.pendingReviewTripId)
            assertNull(host.store.activeTrip)
            host.snapshot("Return-route-ready.png")
            host.click("Start return trip")
            val returning = assertNotNull(host.store.activeTrip)
            assertNotEquals(host.original.id, returning.id)
            assertEquals(host.original.origin, returning.destination)
            assertEquals(host.original, host.store.findCompleted(host.original.id))
            assertTrue(host.has("Returning to your start"))
            host.scrollPanelToBottom()
            host.click("I'm back at the start")
            host.click("Yes, I'm back")
            assertNull(host.store.activeTrip)
            assertEquals(2, host.store.completedTrips.size)
            assertTrue(host.has("You're back at the start!"))
            host.click("Trip Review")
            assertEquals(host.original.id, assertIs<TripReviewScreenKey>(host.navigator.current).tripId)
            assertEquals(host.original, host.store.findCompleted(host.original.id))
        }
    }

    @Test fun failureRetryAndBackPreserveTheCompletedTripAndItsReview() = runBlocking(Dispatchers.Main) {
        Host(failFirst = true).use { host ->
            host.render()
            val before = host.store.toSavedStateJson()
            host.click("Return trip / Fastest trip")
            assertTrue(host.has("No return route found"))
            assertEquals(before, host.store.toSavedStateJson())
            host.snapshot("Return-route-unavailable.png")
            host.click("Retry return route")
            assertTrue(host.has("Start return trip"))
            host.back()
            assertTrue(host.has("Adventure complete!"))
            assertEquals(before, host.store.toSavedStateJson())
            host.click("Same transport")
            assertFalse(host.requests.last().compareModes)
            assertEquals(listOf("walk", "bus"), host.requests.last().modes)
            assertTrue(host.has("Back with the same transport"))
            host.click("Change return option")
            assertEquals(before, host.store.toSavedStateJson())
        }
    }
}
