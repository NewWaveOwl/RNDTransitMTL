package com.example.rnd_transit_mtl

import com.example.rnd_transit_mtl.model.ids.TransportId
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
import com.example.rnd_transit_mtl.data.assets.toSampleMapPlace
import com.example.rnd_transit_mtl.data.generation.TripGenerationResult
import com.example.rnd_transit_mtl.data.generation.BackendTripGenerator
import com.example.rnd_transit_mtl.data.generation.BackendGenerationRequest
import com.example.rnd_transit_mtl.data.generation.BackendGenerationResponse
import com.example.rnd_transit_mtl.data.generation.LocalBackendTripGenerator
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import com.example.rnd_transit_mtl.data.location.LocalPlannerLocation
import com.example.rnd_transit_mtl.data.location.LocationUpdate
import com.example.rnd_transit_mtl.data.location.PlannerLocation
import com.example.rnd_transit_mtl.model.geo.GeoPoint
import com.example.rnd_transit_mtl.model.trip.Place
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.LocalTransitMap
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.TransitMapData

/** Runs the real planner coordinator with explicit outcomes and an isolated store. */
@OptIn(ExperimentalComposeUiApi::class)
class Trip33FlowTest {
    private class BackInput : NavigationEventInput() {
        fun back() = dispatchOnBackCompleted()
    }

    private class Host(
        initialOutcome: Trip33Sample,
        liveMap: Boolean = false,
        backend: BackendTripGenerator? = null
    ) : AutoCloseable {
        var outcome by mutableStateOf(initialOutcome)
        var mapData = TransitMapData()
            private set
        private val location = object : PlannerLocation {
            override val fix: LocationUpdate.Fix? = null
            override val containsPoint: (GeoPoint) -> Boolean = { true }
            override fun request(onResult: ((LocationUpdate.Fix?) -> Unit)?) {
                onResult?.invoke(null)
            }
        }
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
                LocalTripNavigation provides navigation,
                LocalBackendTripGenerator provides backend,
                LocalPlannerLocation provides location.takeIf { liveMap },
                LocalTransitMap provides if (liveMap) ({ data, _ -> SideEffect { mapData = data } }) else null
            ) {
                RNDTransitTheme {
                    TransitOpeningScreen(listOf(TransportType(TransportId("walk"), "Walk", false)), emptyList(),
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
    fun goGeneratesTwoBackendProposalsFromTheSamePinAndStartsTheAcceptedRoute() = runBlocking(Dispatchers.Main) {
        val origin = Place("Confirmed origin", GeoPoint(45.5, -73.6))
        val requests = mutableListOf<BackendGenerationRequest>()
        val backend = BackendTripGenerator(post = { body ->
            val request = Json.decodeFromString<BackendGenerationRequest>(body)
            requests.add(request)
            val destination = Place("Destination ${requests.size}", GeoPoint(45.5 + requests.size * .005, -73.6))
            val delta = if (requests.size == 1) "g^" else "o}@"
            val otp = Json.parseToJsonElement("""
                {"data":{"planConnection":{"routingErrors":[],"edges":[{"node":{
                  "duration":600,"waitingTime":0,"legs":[{"mode":"WALK","distance":1000,"duration":600,
                    "legGeometry":{"length":2,"points":"_vutG~~e`M$delta?"}}]
                }}]}}}
            """.trimIndent()).jsonObject
            Json.encodeToString(BackendGenerationResponse("success", origin = request.origin,
                destination = destination, elapsedSeconds = 600, otp = otp))
        })
        Host(Trip33Sample.Normal, liveMap = true, backend = backend).use { host ->
            host.render()
            host.planner.setOrigin(origin)
            host.render()
            host.click("GO")
            host.await("Start Trip")
            val first = assertNotNull(host.mapData.trip)
            assertEquals(origin, first.origin)
            assertTrue(first.legs.any { it.isRouted })
            assertNull(host.store.activeTrip)
            host.snapshot("Backend-first-proposal.png")
            host.click("Generate another")
            host.await("Destination 2")
            val second = assertNotNull(host.mapData.trip)
            assertEquals(origin, second.origin)
            assertNotEquals(first.destination, second.destination)
            assertEquals(listOf(first.destination.point), requests.last().excludedDestinations)
            host.snapshot("Backend-second-proposal.png")
            host.click("Start Trip")
            host.render(20)
            assertEquals(second.copy(status = com.example.rnd_transit_mtl.model.trip.TripStatus.Active), host.store.activeTrip)
            assertEquals(2, requests.size)
        }
    }

    @Test
    fun liveErrorPickerKeepsTheOldOriginOnBackAndShowsTheConfirmedPin() = runBlocking(Dispatchers.Main) {
        val oldOrigin = Place("Previous origin", GeoPoint(45.50, -73.58))
        val newPoint = GeoPoint(45.52, -73.56)
        Host(Trip33Sample.Normal, liveMap = true).use { host ->
            host.render()
            host.planner.setOrigin(oldOrigin)
            val walking = TransportId("walk")
            host.planner.toggleTransport(walking, setOf(walking))
            host.render()
            host.click("GO")
            host.await("Check your trip settings")

            host.click("Starting point")
            host.render()
            assertTrue(host.mapData.pickerOpen)
            assertNotNull(host.mapData.onPick).invoke(newPoint)
            host.render()
            host.back()
            assertTrue(host.has("Check your trip settings"))
            assertEquals(oldOrigin, host.planner.uiState.value.originPlace)

            host.click("Starting point")
            host.render()
            assertNotNull(host.mapData.onPick).invoke(newPoint)
            host.render()
            host.click("Use this starting point")
            host.await("GO")
            assertEquals(Place("Pinned location", newPoint), host.planner.uiState.value.originPlace)
            assertNull(host.planner.uiState.value.origin)
            assertFalse(host.mapData.pickerOpen)
            assertEquals(newPoint, host.mapData.pin)
            host.click("GO")
            host.await("Check your trip settings")
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
            assertEquals(selected.origin?.toSampleMapPlace(), assertNotNull(host.store.activeTrip).origin)
            assertTrue(host.store.completedTrips.isEmpty())
        }
    }
}
