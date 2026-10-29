package com.example.rnd_transit_mtl

import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import com.example.rnd_transit_mtl.View.main.TransitOpeningScreen
import com.example.rnd_transit_mtl.View.main.Trip33Sample
import com.example.rnd_transit_mtl.ViewModels.main.MainPlannerViewModel
import com.example.rnd_transit_mtl.model.transport.TransportType
import com.example.rnd_transit_mtl.navigation.*
import com.example.rnd_transit_mtl.state.trip.*
import com.example.rnd_transit_mtl.ui.theme.RNDTransitTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.coroutines.delay
import java.io.File
import kotlin.test.*

/** Runs the real planner coordinator with explicit outcomes and an isolated store. */
@OptIn(ExperimentalComposeUiApi::class)
class Trip33FlowTest {
    private class BackInput : NavigationEventInput() {
        fun back() = dispatchOnBackCompleted()
    }

    private class Host(initialOutcome: Trip33Sample) : AutoCloseable {
        var outcome by mutableStateOf(initialOutcome)
        private val lifecycleOwner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this).apply {
                currentState = Lifecycle.State.RESUMED
            }
        }
        private val models = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
        val store = TripsStore(nowEpochMillis = { 2_000L })
        private val navigator = Navigator(NavBackStack<NavKey>(MainScreenKey))
        private val navigation = TripNavigation(navigator, store)
        private val input = BackInput()
        private val scene = ImageComposeScene(412, 716) {
            val dispatcher = rememberNavigationEventDispatcherOwner(parent = null)
            DisposableEffect(dispatcher) {
                dispatcher.navigationEventDispatcher.addInput(input)
                onDispose { dispatcher.navigationEventDispatcher.removeInput(input) }
            }
            CompositionLocalProvider(
                LocalNavigationEventDispatcherOwner provides dispatcher,
                LocalLifecycleOwner provides lifecycleOwner,
                LocalViewModelStoreOwner provides models,
                LocalTripsStore provides store,
                LocalNavigator provides navigator,
                LocalTripNavigation provides navigation
            ) {
                RNDTransitTheme {
                    TransitOpeningScreen(listOf(TransportType("walk", "Walk", false)), emptyList(),
                        onOpenCurrentTrip = { true }, sampleOutcome = outcome)
                }
            }
        }
        private var time = 0L
        val planner: MainPlannerViewModel
            get() = models.viewModelStore.keys().mapNotNull {
                models.viewModelStore[it] as? MainPlannerViewModel
            }.single()
        suspend fun render(frames: Int = 8) {
            repeat(frames) { yield(); time += 16_000_000; scene.render(time).close() }
        }
        private fun all(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::all)
        private fun nodes() = scene.semanticsOwners.flatMap { all(it.rootSemanticsNode) }
        fun has(label: String) = nodes().any {
            it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == label } == true
        }
        fun click(label: String) {
            val node = nodes().first {
                it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == label } == true
            }
            tap(node.boundsInRoot.center)
        }
        fun tap(point: Offset) {
            scene.sendPointerEvent(PointerEventType.Press, point, timeMillis = time / 1_000_000)
            scene.sendPointerEvent(PointerEventType.Release, point, timeMillis = time / 1_000_000 + 1)
        }
        suspend fun back() { input.back(); render() }
        suspend fun await(label: String) {
            repeat(100) {
                render(2)
                if (has(label)) return
                delay(5)
            }
            snapshot("Trip33-test-failure.png")
            error("Screen did not show $label; visible: " + nodes().mapNotNull { it.config.getOrNull(SemanticsProperties.Text) }.flatten().joinToString { it.text })
        }
        fun snapshot(name: String) {
            val folder = File("build/planner-previews").apply { mkdirs() }
            scene.render(time + 1).use { image ->
                image.encodeToData()!!.use { File(folder, name).writeBytes(it.bytes) }
            }
        }
        override fun close() {
            scene.close()
            models.viewModelStore.clear()
        }
    }

    @Test
    fun explicitFailuresKeepTheDraftAndNeverStartATrip() = runBlocking(Dispatchers.Main) {
        for ((outcome, heading) in listOf(
            Trip33Sample.NoRoute to "No trip fits these choices",
            Trip33Sample.ProviderError to "Routing service unavailable"
        )) {
            Host(outcome).use { host ->
                host.render()
                host.planner.setMinutes(45)
                host.render()
                val before = host.planner.uiState.value
                host.click("GO")
                host.await(heading)
                host.snapshot("Trip33-${outcome.name}.png")
                assertNull(host.store.activeTrip)
                assertEquals(before, host.planner.uiState.value)
                host.back()
                assertTrue(host.has("GO"))
                assertNull(host.store.activeTrip)
            }
        }
    }

    @Test
    fun deniedPermissionAllowsManualOriginAndBackDiscardsOnlyTheDraft() = runBlocking(Dispatchers.Main) {
        Host(Trip33Sample.PermissionDenied).use { host ->
            host.render()
            host.planner.setMinutes(45)
            host.render()
            val previous = host.planner.uiState.value
            host.click("GO")
            host.await("Location access is off")
            host.snapshot("Trip33-permission.png")
            host.click("Choose a point on the map")
            host.render()
            host.tap(Offset(130f, 180f))
            host.render()
            host.back()
            assertEquals(previous, host.planner.uiState.value)
            host.click("Choose a point on the map")
            host.render()
            host.tap(Offset(200f, 220f))
            host.render()
            host.click("Use this starting point")
            host.render()
            val selected = host.planner.uiState.value
            assertTrue(selected.originIsManual)
            assertEquals(previous.copy(origin = selected.origin, originIsManual = true), selected)
            assertNull(host.store.activeTrip)
            host.click("GO")
            host.await("Start Trip")
            host.snapshot("Trip33-manual-preview.png")
            assertNull(host.store.activeTrip)
            host.back()
            assertTrue(host.has("GO"))
            assertEquals(selected, host.planner.uiState.value)
            host.click("GO")
            host.await("Start Trip")
            host.click("Start Trip")
            host.render()
            assertEquals(selected.origin, assertNotNull(host.store.activeTrip).start)
            assertTrue(host.store.completedTrips.isEmpty())
        }
    }
}
