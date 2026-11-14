package com.example.rnd_transit_mtl

import com.example.rnd_transit_mtl.model.ids.TransportId
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import com.example.rnd_transit_mtl.View.main.FigmaOriginMap
import com.example.rnd_transit_mtl.View.main.OriginSelectionContent
import com.example.rnd_transit_mtl.View.main.MainPlannerContent
import com.example.rnd_transit_mtl.model.trip.TripPoint
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.LocalTransitMap
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.TransitMapData
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.bundledMapSize
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.mapImageRect
import com.example.rnd_transit_mtl.ui.theme.RNDTransitTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import java.io.File
import kotlin.test.*

/** Checks source-image picking, camera-only panning, and the Figma permission controls. */
@OptIn(ExperimentalComposeUiApi::class)
class PlannerOriginUiTest {
    @Test
    fun sheetAccessibilityActionsHideAndRestoreMapControls() = runBlocking(Dispatchers.Main) {
        var edited = 0
        var mapData = TransitMapData()
        val scene = ImageComposeScene(412, 800) {
            CompositionLocalProvider(LocalTransitMap provides { data, _ -> SideEffect { mapData = data } }) {
                RNDTransitTheme {
                    MainPlannerContent(30, {}, emptyList(), listOf(TransportId("walk")), emptyMap(), {}, { _, _ -> },
                        "Amble", 18, {}, emptyList(), {}, {}, "", "GO", true, true,
                        null, null, {}, {}, onEditOrigin = { edited++ })
                }
            }
        }
        var time = 0L
        // Render and run Compose effects on the same dispatcher. Yield between frames so
        // sheet motion and the field exit finish without racing a layout on the AWT thread.
        suspend fun render() {
            repeat(60) { yield(); time += 16_000_000; scene.render(time).close() }
        }
        fun all(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::all)
        fun nodes() = scene.semanticsOwners.flatMap { all(it.rootSemanticsNode) }
        fun originField() = nodes().firstOrNull {
            it.config.getOrNull(SemanticsActions.OnClick)?.label == "Choose starting point"
        }
        fun control(description: String) = nodes().first {
            it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(description) == true
        }
        suspend fun changeSheet(label: String) {
            assertTrue(control("Trip preferences").config[SemanticsActions.CustomActions]
                .first { it.label == label }.action())
            render()
        }
        try {
            render()
            assertNotNull(originField()).config[SemanticsActions.OnClick].action!!.invoke()
            assertEquals(1, edited)
            scene.render(time + 1).use { image ->
                val folder = File("build/planner-previews").apply { mkdirs() }
                image.encodeToData()!!.use { File(folder, "Origin-main.png").writeBytes(it.bytes) }
            }
            changeSheet("Expand preferences")
            assertNotNull(originField())
            changeSheet("Expand preferences")
            assertNull(originField())
            changeSheet("Collapse preferences")
            assertNotNull(originField())
            changeSheet("Collapse preferences")
            changeSheet("Collapse preferences")
            val goBounds = control("GO").boundsInRoot
            val handleBounds = control("Trip preferences").boundsInRoot
            assertEquals(10f, handleBounds.top - goBounds.bottom, 1f)
            assertEquals(800f, handleBounds.bottom, 1f)
            assertEquals(goBounds.top, 800f - mapData.bottomInset.value, 1f)
            assertEquals(assertNotNull(originField()).boundsInRoot.bottom, mapData.topInset.value, 1f)
            changeSheet("Expand preferences")
            assertTrue(control("GO").boundsInRoot.bottom < goBounds.bottom)
        } finally { scene.close() }
    }

    @Test
    fun tapsUseTheRenderedSourceRectangleInPortraitAndLandscape() = runBlocking(Dispatchers.Main) {
        for ((width, height) in listOf(412 to 720, 720 to 412)) {
            var picked: TripPoint? = null
            val scene = ImageComposeScene(width, height) {
                RNDTransitTheme {
                    FigmaOriginMap(null, Modifier.fillMaxSize(), onPick = { picked = it })
                }
            }
            try {
                repeat(4) { yield(); scene.render(it * 16_000_000L).close() }
                val tap = Offset(width * 0.27f, height * 0.24f)
                scene.sendPointerEvent(PointerEventType.Press, tap, timeMillis = 80)
                scene.sendPointerEvent(PointerEventType.Release, tap, timeMillis = 81)
                val base = mapImageRect(Size(width.toFloat(), height.toFloat()), bundledMapSize, true)
                val left = (width / 2f - 0.5f * base.width).coerceIn(minOf(0f, width - base.width), 0f)
                val top = (height * 0.3f - 0.35f * base.height).coerceIn(minOf(0f, height - base.height), 0f)
                assertEquals((tap.x - left) / base.width, assertNotNull(picked).x, 0.0001f)
                assertEquals((tap.y - top) / base.height, assertNotNull(picked).y, 0.0001f)
            } finally { scene.close() }
        }
    }

    @Test
    fun recenterRestoresTheCameraWithoutEditingOrigin() = runBlocking(Dispatchers.Main) {
        val origin = TripPoint("Saved starting point", 0.5f, 0.35f)
        var recenter by mutableStateOf(0)
        val scene = ImageComposeScene(412, 720) {
            RNDTransitTheme { FigmaOriginMap(origin, Modifier.fillMaxSize(), recenterRequest = recenter) }
        }
        var time = 0L
        suspend fun snapshot(): ByteArray {
            repeat(4) { yield(); time += 16_000_000; scene.render(time).close() }
            return scene.render(time + 1).use { image -> image.encodeToData()!!.use { it.bytes } }
        }
        try {
            val initial = snapshot()
            scene.sendPointerEvent(PointerEventType.Press, Offset(160f, 180f), timeMillis = 100)
            scene.sendPointerEvent(PointerEventType.Move, Offset(270f, 180f), timeMillis = 150)
            scene.sendPointerEvent(PointerEventType.Move, Offset(310f, 180f), timeMillis = 200)
            scene.sendPointerEvent(PointerEventType.Release, Offset(310f, 180f), timeMillis = 201)
            assertFalse(initial.contentEquals(snapshot()))
            recenter++
            assertContentEquals(initial, snapshot())
            assertEquals(TripPoint("Saved starting point", 0.5f, 0.35f), origin)
        } finally { scene.close() }
    }

    @Test
    fun permissionOptionsAndPointConfirmationRenderAtSmallSizes() = runBlocking(Dispatchers.Main) {
        for ((width, height) in listOf(412 to 716, 320 to 480)) {
            var picking by mutableStateOf(false)
            var draft by mutableStateOf<TripPoint?>(null)
            val events = mutableListOf<String>()
            val scene = ImageComposeScene(width, height) {
                val dispatcher = rememberNavigationEventDispatcherOwner(parent = null)
                CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides dispatcher) {
                    RNDTransitTheme {
                        OriginSelectionContent(draft, 45, true, picking, true, null,
                            { draft = it }, { picking = true }, { events += "confirm" },
                            { events += "search" }, { events += "retry" }, { events += "cancel" })
                    }
                }
            }
            var time = 0L
            suspend fun render(name: String) {
                repeat(4) { yield(); time += 16_000_000; scene.render(time).close() }
                scene.render(time + 1).use { image ->
                    val folder = File("build/planner-previews").apply { mkdirs() }
                    image.encodeToData()!!.use { File(folder, "$name-$width.png").writeBytes(it.bytes) }
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
                render("Origin-permission")
                click("Choose a point on the map")
                render("Origin-picking-empty")
                click("Use this starting point")
                assertEquals(emptyList(), events)
                scene.sendPointerEvent(PointerEventType.Press, Offset(100f, 120f), timeMillis = time / 1_000_000 + 5)
                scene.sendPointerEvent(PointerEventType.Release, Offset(100f, 120f), timeMillis = time / 1_000_000 + 6)
                render("Origin-picking-selected")
                assertNotNull(draft)
                click("Use this starting point")
                assertEquals(listOf("confirm"), events)
            } finally { scene.close() }
        }
    }
}
