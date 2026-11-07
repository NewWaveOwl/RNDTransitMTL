package com.example.rnd_transit_mtl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.example.rnd_transit_mtl.data.routing.DebugTripLoader
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.rememberNavBackStack
import com.example.rnd_transit_mtl.data.fake.FakeTransportRouteRepository
import com.example.rnd_transit_mtl.data.fake.FakeTransportTypeRepository
import com.example.rnd_transit_mtl.layout.MainLayout
import com.example.rnd_transit_mtl.model.transport.TransportRoute
import com.example.rnd_transit_mtl.model.transport.TransportType
import com.example.rnd_transit_mtl.state.trip.LocalTripsStore
import com.example.rnd_transit_mtl.state.user.LocalNicknameSession
import com.example.rnd_transit_mtl.state.user.appNicknameSession
import com.example.rnd_transit_mtl.state.trip.TripsStore
import com.example.rnd_transit_mtl.ui.theme.RNDTransitTheme
import kotlinx.coroutines.CancellationException
import com.example.rnd_transit_mtl.View.landing.LandingScreen
import com.example.rnd_transit_mtl.navigation.LocalNavigator
import com.example.rnd_transit_mtl.navigation.LocalTripNavigation
import com.example.rnd_transit_mtl.navigation.MainScreenKey
import com.example.rnd_transit_mtl.navigation.Navigator
import com.example.rnd_transit_mtl.navigation.Router
import com.example.rnd_transit_mtl.navigation.TripNavigation
import com.example.rnd_transit_mtl.navigation.backStackConfig

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
fun App(
    loadDebugStmTrip: DebugTripLoader? = null,
    openDebugStmPreview: Boolean = false,
    loadDebugExoTrip: DebugTripLoader? = null,
    openDebugExoPreview: Boolean = false
) {
    var pendingDebugPreview by rememberSaveable {
        mutableStateOf(when {
            openDebugExoPreview && loadDebugExoTrip != null -> DebugTripSource.Exo.name
            openDebugStmPreview && loadDebugStmTrip != null -> DebugTripSource.Stm.name
            else -> null
        })
    }
    val consumeDebugPreview = remember { { pendingDebugPreview = null } }
    LaunchedEffect(pendingDebugPreview) {
        if (pendingDebugPreview != null && appNicknameSession.nickname == null) appNicknameSession.signIn("Guest")
    }
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
            LocalDebugStmTripLoader provides loadDebugStmTrip,
            LocalDebugExoTripLoader provides loadDebugExoTrip,
            LocalDebugTripLaunch provides pendingDebugPreview?.let {
                DebugTripLaunch(DebugTripSource.valueOf(it), consumeDebugPreview)
            },
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

internal val LocalDebugStmTripLoader = staticCompositionLocalOf<DebugTripLoader?> { null }
internal val LocalDebugExoTripLoader = staticCompositionLocalOf<DebugTripLoader?> { null }
internal enum class DebugTripSource { Stm, Exo }
internal data class DebugTripLaunch(val source: DebugTripSource, val consume: () -> Unit)
internal val LocalDebugTripLaunch = staticCompositionLocalOf<DebugTripLaunch?> { null }
