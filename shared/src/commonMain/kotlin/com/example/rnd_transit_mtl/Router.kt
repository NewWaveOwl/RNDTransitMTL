package com.example.rnd_transit_mtl

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.example.rnd_transit_mtl.model.TransportRoute
import com.example.rnd_transit_mtl.model.TransportType
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

/** Day 18, slide 25: screen metadata belongs to the navigation key. */
interface ScreenKey : NavKey {
    val screenTitle: String
}

@Serializable
data object MainScreenKey : ScreenKey { override val screenTitle = "Home" }

@Serializable
data object ProfileScreenKey : ScreenKey { override val screenTitle = "user" }

@Serializable
data object AboutScreenKey : ScreenKey { override val screenTitle = "about" }

@Serializable
data object SettingsScreenKey : ScreenKey { override val screenTitle = "settings" }

@Serializable
data object HistoryScreenKey : ScreenKey { override val screenTitle = "history" }

/** Explicit registration avoids Android-only reflection on web and iOS. */
val backStackConfig = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(MainScreenKey::class, MainScreenKey.serializer())
            subclass(ProfileScreenKey::class, ProfileScreenKey.serializer())
            subclass(AboutScreenKey::class, AboutScreenKey.serializer())
            subclass(SettingsScreenKey::class, SettingsScreenKey.serializer())
            subclass(HistoryScreenKey::class, HistoryScreenKey.serializer())
        }
    }
}

val LocalNavigator = compositionLocalOf<Navigator> {
    error("No Navigator found! Wrap your UI with CompositionLocalProvider.")
}

/** Day 18's hoisted router: receives the one shared back stack from App. */
@Composable
fun Router(
    backStack: NavBackStack<NavKey>,
    transportTypes: List<TransportType>?,
    transportRoutes: List<TransportRoute>?,
    loadingError: Boolean
) {
    val navigator = LocalNavigator.current
    NavDisplay(
        modifier = Modifier.fillMaxSize(),
        backStack = backStack,
        onBack = { navigator.pop() },
        entryProvider = entryProvider {
            entry<MainScreenKey> { MainScreen(transportTypes, transportRoutes, loadingError) }
            entry<ProfileScreenKey> { ProfileScreen() }
            entry<AboutScreenKey> { AboutScreen() }
            entry<SettingsScreenKey> { SettingsScreen() }
            entry<HistoryScreenKey> { HistoryScreen() }
        },
        transitionSpec = {
            slideInHorizontally(initialOffsetX = { it }) togetherWith
                slideOutHorizontally(targetOffsetX = { -it })
        },
        popTransitionSpec = {
            slideInHorizontally(initialOffsetX = { -it }) togetherWith
                slideOutHorizontally(targetOffsetX = { it })
        },
        predictivePopTransitionSpec = {
            slideInHorizontally(initialOffsetX = { -it }) togetherWith
                slideOutHorizontally(targetOffsetX = { it })
        }
    )
}
