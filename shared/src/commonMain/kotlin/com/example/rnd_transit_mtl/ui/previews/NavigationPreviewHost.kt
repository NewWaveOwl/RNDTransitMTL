package com.example.rnd_transit_mtl.ui.previews

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.rememberNavBackStack
import com.example.rnd_transit_mtl.LocalNavigator
import com.example.rnd_transit_mtl.LocalTripNavigation
import com.example.rnd_transit_mtl.Navigator
import com.example.rnd_transit_mtl.ScreenKey
import com.example.rnd_transit_mtl.TripNavigation
import com.example.rnd_transit_mtl.backStackConfig
import com.example.rnd_transit_mtl.layout.MainLayout
import com.example.rnd_transit_mtl.state.LocalTripsStore
import com.example.rnd_transit_mtl.state.LocalNicknameSession
import com.example.rnd_transit_mtl.state.NicknameSession
import com.example.rnd_transit_mtl.state.TripsState
import com.example.rnd_transit_mtl.state.TripsStore
import com.example.rnd_transit_mtl.ui.theme.RNDTransitTheme
import kotlinx.serialization.json.Json

/**
 * Supplies the same provider/layout structure as App using fixed preview data.
 *
 * It does not render Router, generate trips, or start a simulation.
 */
@Composable
internal fun NavigationPreviewHost(
    vararg keys: ScreenKey,
    tripsState: TripsState = TripsState(),
    content: @Composable () -> Unit
) {
    val backStack = rememberNavBackStack(
        backStackConfig,
        *keys
    )
    val navigator = remember(backStack) {
        Navigator(backStack)
    }
    val store = remember(tripsState) {
        checkNotNull(
            TripsStore.fromSavedStateJson(
                encoded = Json.encodeToString(
                    TripsState.serializer(),
                    tripsState
                ),
                nowEpochMillis = { 1_800_000_000_000L }
            )
        )
    }
    val navigation = remember(navigator, store) {
        TripNavigation(navigator, store)
    }
    val nicknameSession = remember {
        NicknameSession().apply { signIn("Atiom") }
    }

    RNDTransitTheme {
        CompositionLocalProvider(
            LocalNavigator provides navigator,
            LocalTripsStore provides store,
            LocalTripNavigation provides navigation,
            LocalNicknameSession provides nicknameSession
        ) {
            MainLayout(content = content)
        }
    }
}
