package com.example.rnd_transit_mtl.layout.previews

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.rememberNavBackStack
import com.example.rnd_transit_mtl.navigation.LocalNavigator
import com.example.rnd_transit_mtl.navigation.LocalTripNavigation
import com.example.rnd_transit_mtl.navigation.Navigator
import com.example.rnd_transit_mtl.navigation.ScreenKey
import com.example.rnd_transit_mtl.navigation.TripNavigation
import com.example.rnd_transit_mtl.navigation.backStackConfig
import com.example.rnd_transit_mtl.layout.MainLayout
import com.example.rnd_transit_mtl.state.trip.LocalTripsStore
import com.example.rnd_transit_mtl.state.user.LocalNicknameSession
import com.example.rnd_transit_mtl.state.user.NicknameSession
import com.example.rnd_transit_mtl.state.trip.TripsState
import com.example.rnd_transit_mtl.state.trip.TripsStore
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
