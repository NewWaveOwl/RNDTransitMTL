package com.example.rnd_transit_mtl

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Runs the whole App off screen: start a trip, cancel it, keep it, and find it in History. */
@OptIn(ExperimentalComposeUiApi::class)
class CancelAndSaveFlowTest {
    private class Host : AutoCloseable {
        private val lifecycleOwner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this).apply {
                currentState = Lifecycle.State.RESUMED
            }
        }
        private val models = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
        private val scene = ImageComposeScene(412, 900) {
            val dispatcher = rememberNavigationEventDispatcherOwner(parent = null)
            DisposableEffect(dispatcher) { onDispose { } }
            CompositionLocalProvider(
                LocalNavigationEventDispatcherOwner provides dispatcher,
                LocalLifecycleOwner provides lifecycleOwner,
                LocalViewModelStoreOwner provides models
            ) {
                App()
            }
        }
        private var time = 0L

        suspend fun render(frames: Int = 8) {
            repeat(frames) { yield(); time += 16_000_000; scene.render(time).close() }
        }

        private fun all(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::all)
        private fun nodes() = scene.semanticsOwners.flatMap { all(it.rootSemanticsNode) }
        private fun texts(node: SemanticsNode): List<String> =
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
                node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()

        fun has(label: String) = nodes().any { label in texts(it) }
        fun hasTextContaining(part: String) = nodes().any { node -> texts(node).any { part in it } }

        suspend fun click(label: String) {
            await(label)
            // Let screen transitions finish; buttons stay disabled while a screen slides in.
            render(60)
            val point = nodes().first { label in texts(it) }.boundsInRoot.center
            scene.sendPointerEvent(PointerEventType.Press, point, timeMillis = time / 1_000_000)
            scene.sendPointerEvent(PointerEventType.Release, point, timeMillis = time / 1_000_000 + 1)
            render()
        }

        /** The welcome screen appears once per process, so later tests start on the planner. */
        suspend fun continueAsGuestIfAsked() {
            render(30)
            if (has("Continue as guest")) click("Continue as guest")
        }

        suspend fun await(label: String) {
            repeat(200) {
                render(2)
                if (has(label)) return
                delay(5)
            }
            snapshot("Cancel-flow-failure.png")
            error("Screen did not show \"$label\"; visible: " + nodes().flatMap(::texts).joinToString())
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
    fun cancelledTripKeptByTheUserAppearsInHistoryAndDetails() = runBlocking(Dispatchers.Main) {
        Host().use { host ->
            host.continueAsGuestIfAsked()
            host.click("GO")
            host.click("Start Trip")
            host.click("Cancel")
            host.snapshot("Cancel-flow-popup.png")
            host.click("Cancel and save to History")

            host.click("History")
            host.await("Cancelled")
            assertTrue(host.has("1 saved trip"))
            // The card starts collapsed: two summary lines and a More button.
            assertTrue(host.hasTextContaining(" min · "))
            assertFalse(host.has("Cancelled before the end, so it cannot be reviewed."))
            host.snapshot("Cancel-flow-history.png")

            host.click("More")
            host.await("Cancelled before the end, so it cannot be reviewed.")
            assertTrue(host.hasTextContaining("Cancelled: "))
            assertTrue(host.has("Less"))
            host.render(60)
            host.snapshot("Cancel-flow-history-expanded.png")

            host.click("Details")
            host.await("Repeat as a new trip")
            host.snapshot("Cancel-flow-details.png")
            assertTrue(host.hasTextContaining("Cancelled · "))
            assertFalse(host.has("Review trip"))
        }
    }

    @Test
    fun cancelledTripTheUserDiscardsIsNotInHistory() = runBlocking(Dispatchers.Main) {
        Host().use { host ->
            host.continueAsGuestIfAsked()
            host.click("GO")
            host.click("Start Trip")
            host.click("Cancel")
            host.click("Cancel without saving")

            host.click("History")
            host.await("No trips yet")
            // "Cancelled" is also a filter chip label, so check for a cancelled card instead.
            assertFalse(host.has("Cancelled before the end, so it cannot be reviewed."))
        }
    }
}
