package com.example.rnd_transit_mtl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.rnd_transit_mtl.data.FakeTransportRouteRepository
import com.example.rnd_transit_mtl.data.FakeTransportTypeRepository
import com.example.rnd_transit_mtl.model.TransportRoute
import com.example.rnd_transit_mtl.model.TransportType
import com.example.rnd_transit_mtl.ui.theme.RNDTransitTheme
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitHighlight
import com.example.rnd_transit_mtl.ui.theme.TransitWhite
import kotlinx.coroutines.CancellationException

private data class TransportData(
    val types: List<TransportType>,
    val routes: List<TransportRoute>
)

/** Shared entry point for the Android, desktop, web, and iOS transit interface. */
@Composable
@Preview
fun App() {
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
        Box(
            modifier = Modifier.fillMaxSize().background(TransitMain).safeDrawingPadding(),
            contentAlignment = Alignment.Center
        ) {
            val data = transportData
            when {
                data != null -> TransitOpeningScreen(data.types, data.routes)
                loadingError -> Text("Unable to load transport data.", color = TransitWhite)
                else -> CircularProgressIndicator(color = TransitHighlight)
            }
        }
    }
}
