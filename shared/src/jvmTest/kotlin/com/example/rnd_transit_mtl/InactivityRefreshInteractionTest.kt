package com.example.rnd_transit_mtl

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.rnd_transit_mtl.state.refresh.APP_IDLE_REFRESH_MILLIS
import com.example.rnd_transit_mtl.state.refresh.rememberInactivityRefresh
import com.example.rnd_transit_mtl.ui.global_ui_ellements.refresh.InactivityRefreshFeedback
import com.example.rnd_transit_mtl.ui.theme.RNDTransitTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import java.io.File
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class InactivityRefreshInteractionTest {
    @Test fun resumeReplaysBootAndRetryKeepsTheExistingScreenComposed() = runBlocking(Dispatchers.Main) {
        var now = 0L
        var requests = 0
        var screenCreations = 0
        val response = CompletableDeferred<Unit>()
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        }
        val scene = ImageComposeScene(412, 800) {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                RNDTransitTheme {
                    val refresh = rememberInactivityRefresh("account:test", clock = { now }) {
                        requests++
                        if (requests == 1) { response.await(); error("Offline") }
                    }
                    Box {
                        val draft = remember { screenCreations++; "Unsent review draft" }
                        Text(draft)
                        InactivityRefreshFeedback(refresh)
                    }
                }
            }
        }
        var frame = 0L
        suspend fun render(count: Int = 160) { repeat(count) { yield(); frame += 16_000_000; scene.render(frame).close() } }
        fun all(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::all)
        fun nodes() = scene.semanticsOwners.flatMap { all(it.rootSemanticsNode) }
        fun text(label: String) = nodes().firstOrNull {
            it.config.getOrNull(SemanticsProperties.Text)?.any { it.text == label } == true
        }
        try {
            render(5)
            assertEquals(0, requests)
            owner.lifecycle.currentState = Lifecycle.State.CREATED
            now += APP_IDLE_REFRESH_MILLIS
            owner.lifecycle.currentState = Lifecycle.State.RESUMED
            render()
            assertEquals(1, requests)
            assertNotNull(text("Refreshing app data…"))
            scene.render(frame).use { image ->
                File("build/planner-previews").mkdirs()
                image.encodeToData()!!.use { File("build/planner-previews/Idle-refresh.png").writeBytes(it.bytes) }
            }
            response.complete(Unit)
            render()
            assertNotNull(text("Unsent review draft"))
            val retry = assertNotNull(text("Retry")).boundsInRoot.center
            scene.sendPointerEvent(PointerEventType.Press, retry, timeMillis = frame / 1_000_000)
            scene.sendPointerEvent(PointerEventType.Release, retry, timeMillis = frame / 1_000_000 + 1)
            render()
            assertEquals(2, requests)
            assertEquals(1, screenCreations)
            assertNull(text("Retry"))
            assertNotNull(text("Unsent review draft"))
            Unit
        } finally { scene.close() }
    }
}
