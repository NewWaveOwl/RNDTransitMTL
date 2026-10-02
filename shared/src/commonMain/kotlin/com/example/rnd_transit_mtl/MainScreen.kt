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

@Composable
fun MainScreen(
    transportTypes: List<TransportType>?,
    transportRoutes: List<TransportRoute>?,
    loadingError: Boolean
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            transportTypes != null && transportRoutes != null ->
                TransitOpeningScreen(transportTypes, transportRoutes)
            loadingError -> Text("Unable to load transport data.", color = TransitWhite)
            else -> CircularProgressIndicator(color = TransitHighlight)
        }
    }
}
