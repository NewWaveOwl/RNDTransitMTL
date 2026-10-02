package com.example.rnd_transit_mtl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.rememberNavBackStack
import com.example.rnd_transit_mtl.data.FakeTransportRouteRepository
import com.example.rnd_transit_mtl.data.FakeTransportTypeRepository
import com.example.rnd_transit_mtl.layout.MainLayout
import com.example.rnd_transit_mtl.model.TransportRoute
import com.example.rnd_transit_mtl.model.TransportType
import com.example.rnd_transit_mtl.ui.theme.RNDTransitTheme
import kotlinx.coroutines.CancellationException

private data class TransportData(
    val types: List<TransportType>,
    val routes: List<TransportRoute>
)

/** Day 18: owns the one back stack and hoists the shared layout above Router. */
@Composable
fun App() {
    val backStack = rememberNavBackStack(backStackConfig, MainScreenKey)
    val navigator = remember(backStack) { Navigator(backStack) }
    var transportData by remember { mutableStateOf<TransportData?>(null) }
    var loadingError by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            transportData = TransportData(
                types = FakeTransportTypeRepository().getTransportTypes(),
                routes = FakeTransportRouteRepository().getTransportRoutes()
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            loadingError = true
        }
    }

    RNDTransitTheme {
        CompositionLocalProvider(LocalNavigator provides navigator) {
            MainLayout {
                Router(backStack, transportData?.types, transportData?.routes, loadingError)
            }
        }
    }
}
