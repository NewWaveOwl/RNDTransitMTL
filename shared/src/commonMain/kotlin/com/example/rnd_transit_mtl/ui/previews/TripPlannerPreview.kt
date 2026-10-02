package com.example.rnd_transit_mtl.ui.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.rnd_transit_mtl.model.TransportRoute
import com.example.rnd_transit_mtl.model.TransportType
import com.example.rnd_transit_mtl.ui.TripPlannerContent
import com.example.rnd_transit_mtl.ui.theme.RNDTransitTheme

/**
 * Previews the trip planner with sample transport data and selected routes.
 */
@Preview(showBackground = true, widthDp = 350, heightDp = 580)
@Composable
fun TripPlannerPreview() {
    val previewTypes = listOf(
        TransportType("train", "Train", true),
        TransportType("rem", "REM", true),
        TransportType("walk", "Walk", false),
        TransportType("bus", "Bus", true),
        TransportType("metro", "Metro", true),
        TransportType("bike", "Bike", false)
    )
    val previewRoutes = listOf(
        TransportRoute("train:11", "train", "11"),
        TransportRoute("train:14", "train", "14"),
        TransportRoute("bus:401", "bus", "401"),
        TransportRoute("bus:747", "bus", "747")
    )

    RNDTransitTheme {
        TripPlannerContent(
            minutes = 30,
            onMinutesChange = {},
            transportTypes = previewTypes,
            transportRoutes = previewRoutes,
            selectedTransportIds = listOf("walk", "bike", "train", "bus"),
            selectedRouteIds = listOf("train:11", "train:14", "bus:401", "bus:747"),
            savedTrips = emptyList(),
            showTripResults = false,
            onToggleTransport = {},
            onToggleRoute = { _, _ -> },
            intensity = 90f,
            onIntensityChange = {},
            validationMessage = "",
            onTripResultsVisibilityChange = {},
            onRemoveTrip = {},
            onGo = {}
        )
    }
}
