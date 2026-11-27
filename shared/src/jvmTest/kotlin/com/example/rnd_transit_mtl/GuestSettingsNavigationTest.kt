package com.example.rnd_transit_mtl

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.example.rnd_transit_mtl.View.settings.SettingsScreen
import com.example.rnd_transit_mtl.navigation.AuthScreenKey
import com.example.rnd_transit_mtl.navigation.LocalNavigator
import com.example.rnd_transit_mtl.navigation.MainScreenKey
import com.example.rnd_transit_mtl.navigation.Navigator
import com.example.rnd_transit_mtl.navigation.SettingsScreenKey
import com.example.rnd_transit_mtl.ui.theme.RNDTransitTheme
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

@OptIn(ExperimentalComposeUiApi::class)
class GuestSettingsNavigationTest {
    @Test
    fun guestSettingsRendersPromptAndSignInRoutesToAuthScreen() = runBlocking(Dispatchers.Main) {
        val backStack = NavBackStack<NavKey>(MainScreenKey, SettingsScreenKey)
        val navigator = Navigator(backStack)
        var frame = 0L

        val scene = ImageComposeScene(412, 892) {
            CompositionLocalProvider(LocalNavigator provides navigator) {
                RNDTransitTheme {
                    SettingsScreen(viewModel = null)
                }
            }
        }
        try {
            suspend fun render() {
                repeat(15) {
                    yield()
                    frame += 16_000_000L
                    scene.render(frame).close()
                }
            }
            fun all(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::all)
            fun nodes() = scene.semanticsOwners.flatMap { all(it.rootSemanticsNode) }
            fun texts() = nodes().flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }.map { it.text }

            render()
            assertContains(texts(), "Sign in to manage account settings.")
            assertContains(texts(), "Sign in")

            val signInCenter = nodes()
                .first { it.config.getOrNull(SemanticsProperties.Text)?.any { item -> item.text == "Sign in" } == true }
                .boundsInRoot
                .center
            scene.sendPointerEvent(PointerEventType.Press, signInCenter, timeMillis = frame / 1_000_000L)
            scene.sendPointerEvent(PointerEventType.Release, signInCenter, timeMillis = frame / 1_000_000L + 1)
            render()

            assertEquals(AuthScreenKey(register = false), navigator.current)
        } finally {
            scene.close()
        }
    }
}
