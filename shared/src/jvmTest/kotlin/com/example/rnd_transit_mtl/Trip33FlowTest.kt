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
    @Test
    fun failedFetchAndGuestLimitKeepTheLastRouteAvailableToStart() = runBlocking(Dispatchers.Main) {
        for (reason in listOf("offline", "guest_daily_limit")) {
            var calls = 0
            val backend = BackendTripGenerator(post = { body, _ ->
                calls++
                if (calls > 1) {
                    if (reason == "offline") error("Fixture fetch failure")
                    throw com.example.rnd_transit_mtl.data.generation.GenerationHttpFailure(429, 3600, reason)
                }
                val request = Json.decodeFromString<BackendGenerationRequest>(body)
                val otp = Json.parseToJsonElement("""
                    {"data":{"planConnection":{"routingErrors":[],"edges":[{"node":{
                      "duration":600,"waitingTime":0,"legs":[{"mode":"WALK","distance":560,"duration":600,
                        "legGeometry":{"length":2,"points":"_vutG~~e`Mg^?"}}]
                    }}]}}}
                """.trimIndent()).jsonObject
                Json.encodeToString(BackendGenerationResponse("success", origin = request.origin,
                    destination = Place("Last valid destination", GeoPoint(45.505, -73.6)), elapsedSeconds = 600, otp = otp))
            })
            Host(Trip33Sample.Normal, liveMap = true, backend = backend).use { host ->
                host.render()
                host.planner.setOrigin(Place("Confirmed origin", GeoPoint(45.5, -73.6)))
                host.render()
                host.click("GO")
                host.await("Start Trip")
                val accepted = assertNotNull(host.mapData.trip)
                host.click("Generate another")
                host.await("retained proposal after $reason") {
                    host.has("Start Trip") && host.has(if (reason == "offline")
                        "Could not reach the trip planner. Check your connection and retry." else
                        "Guests can generate five trips per day. Try again after midnight in Montréal or sign in. Your last trip is still available.")
                }
                assertEquals(accepted, host.mapData.trip)
                assertEquals(2, calls)
                host.render(30) // Settle the proposal entrance before recording visual evidence.
                host.snapshot("Issue99-retained-$reason.png")
                host.click("Start Trip")
                host.render(20)
                assertEquals(accepted.id, host.store.activeTrip?.id)
            }
        }
    }
    private class BackInput : NavigationEventInput() {
        fun back() = dispatchOnBackCompleted()
    }

    private class Host(
        initialOutcome: Trip33Sample,
        liveMap: Boolean = false,
        backend: BackendTripGenerator? = null,
        providedLocation: PlannerLocation? = null
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
                LocalPlannerLocation provides (providedLocation ?: location).takeIf { liveMap },
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
        suspend fun await(label: String, ready: () -> Boolean = { has(label) }) {
            repeat(100) {
                render(2)
                if (ready()) return
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
    fun goWaitsForFreshPositionAndRoutesFromItInsteadOfTheEarlierAutomaticOrigin() = runBlocking(Dispatchers.Main) {
        val old = LocationUpdate.Fix(GeoPoint(45.5, -73.6), 5.0)
        val fresh = LocationUpdate.Fix(GeoPoint(45.51, -73.59), 5.0)
        val updates = kotlinx.coroutines.flow.MutableStateFlow<LocationUpdate>(old)
        var requested: ((LocationUpdate.Fix?) -> Unit)? = null
        val location = object : PlannerLocation {
            override val fix = old
            override val automaticOriginUpdates = updates
            override val containsPoint: (GeoPoint) -> Boolean = { true }
            override fun request(onResult: ((LocationUpdate.Fix?) -> Unit)?) { requested = onResult }
        }
        val requests = mutableListOf<BackendGenerationRequest>()
        val backend = BackendTripGenerator(post = { body, _ ->
            requests += Json.decodeFromString<BackendGenerationRequest>(body)
            """{"status":"no_trip"}"""
        })
        Host(Trip33Sample.Normal, liveMap = true, backend = backend, providedLocation = location).use { host ->
            host.render()
            assertEquals(old.point, host.planner.uiState.value.originPlace?.point)
            host.click("GO")
            host.render()
            assertTrue(requests.isEmpty(), "GO must wait for a fresh measurement")
            assertNotNull(requested).invoke(fresh)
            host.await("No trip fits these choices")
            assertEquals(fresh.point, requests.single().origin.point)
            assertFalse(host.planner.uiState.value.originIsManual)
        }
    }

    @Test
    fun switchingToRoundTripKeepsARecentGpsOriginWithoutAnotherRefresh() = runBlocking(Dispatchers.Main) {
        val recent = LocationUpdate.Fix(GeoPoint(45.51, -73.59), 5.0,
            timestampEpochMillis = kotlin.time.Clock.System.now().toEpochMilliseconds() - 8_000L)
        var refreshes = 0
        val location = object : PlannerLocation {
            override val fix = recent
            override val automaticOriginUpdates = kotlinx.coroutines.flow.MutableStateFlow<LocationUpdate>(recent)
            override val containsPoint: (GeoPoint) -> Boolean = { true }
            override fun request(onResult: ((LocationUpdate.Fix?) -> Unit)?) {
                refreshes++
                onResult?.invoke(null)
            }
        }
        val requests = mutableListOf<BackendGenerationRequest>()
        val backend = BackendTripGenerator(post = { body, _ ->
            requests += Json.decodeFromString<BackendGenerationRequest>(body)
            """{"status":"no_trip"}"""
        })
        Host(Trip33Sample.Normal, liveMap = true, backend = backend, providedLocation = location).use { host ->
            host.render()
            host.planner.setRoundTrip(true)
            host.render()
            host.click("GO")
            host.await("No trip fits these choices")
            assertTrue(requests.single().roundTrip)
            assertEquals(recent.point, requests.single().origin.point)
            assertEquals(0, refreshes)
            assertFalse(host.has("Location access is off"))
        }
    }

    @Test
    fun roundTripGpsOutageShowsTheCorrectWarningAndRetryRequestsLocationAgain() = runBlocking(Dispatchers.Main) {
        var available = false
        var refreshes = 0
        val fresh = LocationUpdate.Fix(GeoPoint(45.51, -73.59), 5.0)
        val location = object : PlannerLocation {
            override val fix: LocationUpdate.Fix? = null
            override val containsPoint: (GeoPoint) -> Boolean = { true }
            override fun request(onResult: ((LocationUpdate.Fix?) -> Unit)?) {
                refreshes++
                onResult?.invoke(if (available) fresh else null)
            }
        }
        val requests = mutableListOf<BackendGenerationRequest>()
        val backend = BackendTripGenerator(post = { body, _ ->
            requests += Json.decodeFromString<BackendGenerationRequest>(body)
            """{"status":"no_trip"}"""
        })
        Host(Trip33Sample.Normal, liveMap = true, backend = backend, providedLocation = location).use { host ->
            host.render()
            host.planner.setDefaultOrigin(Place("Current location", fresh.point))
            host.planner.setRoundTrip(true)
            host.render()
            host.click("GO")
            host.await("Current location unavailable")
            assertFalse(host.has("Location access is off"))
            assertTrue(requests.isEmpty())
            available = true
            host.click("Try current location again")
            host.await("No trip fits these choices")
            assertEquals(2, refreshes)
            assertTrue(requests.single().roundTrip)
            assertEquals(fresh.point, requests.single().origin.point)
        }
    }

    @Test
    fun goGeneratesTwoBackendProposalsFromTheSamePinAndStartsTheAcceptedRoute() = runBlocking(Dispatchers.Main) {
        val origin = Place("Confirmed origin", GeoPoint(45.5, -73.6))
        val requests = mutableListOf<BackendGenerationRequest>()
        val backend = BackendTripGenerator(post = { body, _ ->
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
            // The preview shows an editable trip name, not the destination label.
            // Wait for both the proposal actions and the newly published route.
            host.await("second backend proposal") {
                host.has("Start Trip") && host.mapData.trip?.destination?.label == "Destination 2"
            }
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
            host.click("Start here")
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
    fun browsingLiveMapStagesEachPinUntilConfirmedAndCancelOrBackKeepsTheDraft() = runBlocking(Dispatchers.Main) {
        Host(Trip33Sample.Normal, liveMap = true).use { host ->
            host.render()
            host.planner.setOrigin(Place("Previous origin", GeoPoint(45.50, -73.58)))
            host.planner.setMinutes(45)
            host.render()
            val previous = host.planner.uiState.value
            val first = GeoPoint(45.52, -73.56)
            val second = GeoPoint(45.53, -73.57)

            assertNotNull(host.mapData.onPick).invoke(first)
            host.render()
            assertTrue(host.has("Start here"))
            assertEquals(first, host.mapData.pin)
            assertEquals(previous, host.planner.uiState.value)
            host.click("Cancel")
            host.render()
            assertTrue(host.has("GO"))
            assertEquals(previous, host.planner.uiState.value)
            assertEquals(previous.originPlace?.point, host.mapData.pin)

            assertNotNull(host.mapData.onPick).invoke(first)
            host.render()
            host.back()
            assertTrue(host.has("GO"))
            assertEquals(previous, host.planner.uiState.value)

            assertNotNull(host.mapData.onPick).invoke(first)
            host.render()
            assertNotNull(host.mapData.onPick).invoke(second)
            host.render()
            assertEquals(previous, host.planner.uiState.value)
            host.click("Start here")
            host.render()
            assertEquals(previous.copy(origin = null, originPlace = Place("Pinned location", second), originIsManual = true),
                host.planner.uiState.value)
            assertEquals(second, host.mapData.pin)
            assertFalse(host.mapData.pickerOpen)
            assertNull(host.store.activeTrip)
        }
    }

    @Test
    fun browsingSampleMapStagesATapAndCancelPreservesTheSavedOrigin() = runBlocking(Dispatchers.Main) {
        Host(Trip33Sample.Normal).use { host ->
            host.render()
            val previous = host.planner.uiState.value
            host.tap(Offset(130f, 150f))
            host.render()
            assertTrue(host.has("Start here"))
            assertEquals(previous, host.planner.uiState.value)
            host.click("Cancel")
            host.render()
            assertTrue(host.has("GO"))
            assertEquals(previous, host.planner.uiState.value)

            host.tap(Offset(160f, 160f))
            host.render()
            host.click("Start here")
            host.render()
            val selected = host.planner.uiState.value
            assertNotEquals(previous.origin, selected.origin)
            assertEquals(previous.copy(origin = selected.origin, originIsManual = true), selected)
            assertNull(host.store.activeTrip)
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
