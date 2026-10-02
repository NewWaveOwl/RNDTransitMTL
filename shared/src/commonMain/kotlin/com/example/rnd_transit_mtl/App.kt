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

/**
 * Keeps successfully loaded transport types and routes together.
 *
 * Publishing one TransportData value makes both lists available to the UI together.
 *
 * @param types Available transport options in display order.
 * @param routes Available routes, each linked to its owning transport type.
 */
private data class TransportData(
    val types: List<TransportType>,
    val routes: List<TransportRoute>
)

/**
 * Loads shared transport data and provides the navigator, theme, and common layout.
 *
 * Creates one restorable navigation stack rooted at Home. Transport data is loaded
 * when the effect enters composition, and failures are passed to the main screen.
 */
@Composable
fun App() {
    /** Restore or create the shared back stack, starting at Home for a new stack. */
    val backStack = rememberNavBackStack(backStackConfig, MainScreenKey)

    /** Reuse the navigator while it controls the same back stack. */
    val navigator = remember(backStack) { Navigator(backStack) }

    var transportData by remember { mutableStateOf<TransportData?>(null) }
    var loadingError by remember { mutableStateOf(false) }

    /** Load both repository lists once per effect lifetime, rather than on every recomposition. */
    LaunchedEffect(Unit) {
        try {
            /** Assign the data only after both repository calls complete successfully. */
            transportData = TransportData(
                types = FakeTransportTypeRepository().getTransportTypes(),
                routes = FakeTransportRouteRepository().getTransportRoutes()
            )
        } catch (cancelled: CancellationException) {
            /** Preserve coroutine cancellation instead of reporting it as a loading failure. */
            throw cancelled
        } catch (_: Exception) {
            /** Expose ordinary loading failures so MainScreen can display its error message. */
            loadingError = true
        }
    }

    RNDTransitTheme {
        /** Supply the same navigator to the common layout, router, and all descendant screens. */
        CompositionLocalProvider(LocalNavigator provides navigator) {
            MainLayout {
                Router(backStack, transportData?.types, transportData?.routes, loadingError)
            }
        }
    }
}