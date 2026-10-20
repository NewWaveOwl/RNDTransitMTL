package com.example.rnd_transit_mtl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.rememberNavBackStack
import com.example.rnd_transit_mtl.data.FakeTransportRouteRepository
import com.example.rnd_transit_mtl.data.FakeTransportTypeRepository
import com.example.rnd_transit_mtl.layout.MainLayout
import com.example.rnd_transit_mtl.model.TransportRoute
import com.example.rnd_transit_mtl.model.TransportType
import com.example.rnd_transit_mtl.state.LocalTripsStore
import com.example.rnd_transit_mtl.state.LocalNicknameSession
import com.example.rnd_transit_mtl.state.appNicknameSession
import com.example.rnd_transit_mtl.state.TripsStore
import com.example.rnd_transit_mtl.ui.theme.RNDTransitTheme
import kotlinx.coroutines.CancellationException

private data class TransportData(
    val types: List<TransportType>,
    val routes: List<TransportRoute>
)

/**
 * Owns one saved back stack, one Navigator, and one restorable TripsStore.
 *
 * TripNavigation is recreated from those restored objects. Its temporary
 * callbacks are not part of saved state.
 *
 * MainLayout remains outside NavDisplay's destination transitions.
 * The introductory nickname gate uses memory-only process state. It neither
 * adds another back stack nor includes the nickname in restoration snapshots.
 */
@Composable
fun App() {
    val backStack = rememberNavBackStack(
        backStackConfig,
        MainScreenKey
    )
    val navigator = remember(backStack) {
        Navigator(backStack)
    }

    val tripsStore = rememberSaveable(
        saver = TripsStore.Saver
    ) {
        TripsStore()
    }

    val tripNavigation = remember(navigator, tripsStore) {
        TripNavigation(navigator, tripsStore)
    }

    var transportData by remember {
        mutableStateOf<TransportData?>(null)
    }
    var loadingError by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(Unit) {
        try {
            transportData = TransportData(
                types = FakeTransportTypeRepository()
                    .getTransportTypes(),
                routes = FakeTransportRouteRepository()
                    .getTransportRoutes()
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            loadingError = true
        }
    }

    RNDTransitTheme {
        CompositionLocalProvider(
            LocalNavigator provides navigator,
            LocalTripsStore provides tripsStore,
            LocalTripNavigation provides tripNavigation,
            LocalNicknameSession provides appNicknameSession
        ) {
            if (appNicknameSession.nickname == null) {
                LandingScreen(onLogin = appNicknameSession::signIn)
            } else {
                MainLayout {
                    Router(
                        backStack = backStack,
                        transportTypes = transportData?.types,
                        transportRoutes = transportData?.routes,
                        loadingError = loadingError
                    )
                }
            }
        }
    }
}
