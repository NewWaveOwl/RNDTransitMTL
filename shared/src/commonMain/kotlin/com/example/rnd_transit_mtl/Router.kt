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

/**
 * Defines a navigation destination with a title for the shared header.
 *
 * Concrete destination keys are serializable so the back stack can be saved and restored.
 */
interface ScreenKey : NavKey {
    /** Title displayed by the shared top bar for this destination. */
    val screenTitle: String
}

/**
 * Serializable destination for the main trip planner.
 */
@Serializable
data object MainScreenKey : ScreenKey { override val screenTitle = "Home" }

/**
 * Serializable destination for the user profile.
 */
@Serializable
data object ProfileScreenKey : ScreenKey { override val screenTitle = "user" }

/**
 * Serializable destination for the team About page.
 */
@Serializable
data object AboutScreenKey : ScreenKey { override val screenTitle = "about" }

/**
 * Serializable destination for the settings placeholders.
 */
@Serializable
data object SettingsScreenKey : ScreenKey { override val screenTitle = "settings" }

/**
 * Serializable destination for the history placeholders.
 */
@Serializable
data object HistoryScreenKey : ScreenKey { override val screenTitle = "history" }

/**
 * Registers every destination serializer used to save and restore the shared back stack.
 *
 * Explicit polymorphic registration supports platforms where reflection-based discovery
 * is unavailable. New destination keys must also be registered here.
 */
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

/**
 * Provides access to the navigator supplied by App to screens and the shared header.
 *
 * Access outside the corresponding CompositionLocalProvider fails immediately
 * instead of creating an unrelated navigation stack.
 */
val LocalNavigator = compositionLocalOf<Navigator> {
    error("No Navigator found! Wrap your UI with CompositionLocalProvider.")
}

/**
 * Displays typed destinations using the shared back stack and slide transitions.
 *
 * Resolves each registered destination key to its screen and delegates Back actions
 * to the shared navigator.
 *
 * @param backStack The single navigation stack owned by App and shared with Navigator.
 * @param transportTypes Loaded transport options for Main, or null while unavailable.
 * @param transportRoutes Loaded transport routes for Main, or null while unavailable.
 * @param loadingError Whether Main should display the transport-loading error.
 */
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

        /** Map destination key types to the composables that render their screens. */
        entryProvider = entryProvider {
            entry<MainScreenKey> { MainScreen(transportTypes, transportRoutes, loadingError) }
            entry<ProfileScreenKey> { ProfileScreen() }
            entry<AboutScreenKey> { AboutScreen() }
            entry<SettingsScreenKey> { SettingsScreen() }
            entry<HistoryScreenKey> { HistoryScreen() }
        },

        /** Forward navigation brings the new screen from the right and moves the old screen left. */
        transitionSpec = {
            slideInHorizontally(initialOffsetX = { it }) togetherWith
                    slideOutHorizontally(targetOffsetX = { -it })
        },

        /** Back navigation reverses the forward slide direction. */
        popTransitionSpec = {
            slideInHorizontally(initialOffsetX = { -it }) togetherWith
                    slideOutHorizontally(targetOffsetX = { it })
        },

        /** Use the same reverse direction for predictive Back transitions. */
        predictivePopTransitionSpec = {
            slideInHorizontally(initialOffsetX = { -it }) togetherWith
                    slideOutHorizontally(targetOffsetX = { it })
        }
    )
}