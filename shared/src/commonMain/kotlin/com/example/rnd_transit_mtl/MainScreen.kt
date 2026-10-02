package com.example.rnd_transit_mtl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.rnd_transit_mtl.model.TransportRoute
import com.example.rnd_transit_mtl.model.TransportType
import com.example.rnd_transit_mtl.ui.theme.TransitHighlight
import com.example.rnd_transit_mtl.ui.theme.TransitWhite

/**
 * Displays the trip planner, a loading indicator, or a resource-loading error.
 *
 * Available data takes priority over the error flag. The loading indicator
 * remains visible while either list is unavailable and no failure is reported.
 *
 * @param transportTypes Loaded transport options, or null while data is unavailable.
 * @param transportRoutes Loaded routes, or null while data is unavailable.
 * @param loadingError Whether transport resource loading failed.
 */
@Composable
fun MainScreen(
    transportTypes: List<TransportType>?,
    transportRoutes: List<TransportRoute>?,
    loadingError: Boolean
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        /** Open the planner only when both data lists are available; otherwise show error or loading. */
        when {
            transportTypes != null && transportRoutes != null ->
                TransitOpeningScreen(transportTypes, transportRoutes)
            loadingError -> Text("Unable to load transport data.", color = TransitWhite)
            else -> CircularProgressIndicator(color = TransitHighlight)
        }
    }
}