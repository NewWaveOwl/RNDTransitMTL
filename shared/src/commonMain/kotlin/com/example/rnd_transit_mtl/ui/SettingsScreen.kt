package com.example.rnd_transit_mtl.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.rnd_transit_mtl.ui.theme.TransitWhite
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import rnd_transit_mtl.shared.generated.resources.Res
import rnd_transit_mtl.shared.generated.resources.map_sample
import rnd_transit_mtl.shared.generated.resources.ic_account_circle
import rnd_transit_mtl.shared.generated.resources.ic_receipt_long
import rnd_transit_mtl.shared.generated.resources.ic_settings
import com.example.rnd_transit_mtl.model.TransportRoute
import com.example.rnd_transit_mtl.model.TransportType
import com.example.rnd_transit_mtl.ui.theme.RNDTransitTheme
import com.example.rnd_transit_mtl.ui.theme.TransitMain

/**
 * Coordinates the transit settings sections over the responsive map background.
 *
 * The caller owns persistent trip state. This composable owns only the temporary expanded
 * transport ID and passes display work to focused, stateless section composables.
 */
@Composable
fun SettingsScreen(
    minutes: Int,
    onMinutesChange: (Int) -> Unit,
    transportTypes: List<TransportType>,
    transportRoutes: List<TransportRoute>,
    selectedTransportIds: List<String>,
    selectedRouteIds: List<String>,
    savedTrips: List<String>,
    showTripResults: Boolean,
    onToggleTransport: (String) -> Unit,
    onToggleRoute: (String, String) -> Unit,
    intensity: Float,
    onIntensityChange: (Float) -> Unit,
    validationMessage: String,
    onTripResultsVisibilityChange: (Boolean) -> Unit,
    onRemoveTrip: (Int) -> Unit,
    onGo: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedTransportId by remember { mutableStateOf<String?>(null) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(TransitMain)
    ) {
        // The reference frame is 402 units wide. Scale controls with the available width.
        val layoutScale = (maxWidth.value / 402f).coerceIn(0.7f, 1.4f)
        Image(
            painter = painterResource(Res.drawable.map_sample),
            contentDescription = "Illustrated transit map. Swipe left to show saved trips or right to return to trip selection.",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(savedTrips, showTripResults) {
                    var horizontalDrag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { horizontalDrag = 0f },
                        onHorizontalDrag = { _, dragAmount -> horizontalDrag += dragAmount },
                        onDragEnd = {
                            when {
                                horizontalDrag <= -80f && savedTrips.isNotEmpty() ->
                                    onTripResultsVisibilityChange(true)

                                horizontalDrag >= 80f && showTripResults ->
                                    onTripResultsVisibilityChange(false)
                            }
                            horizontalDrag = 0f
                        },
                        onDragCancel = { horizontalDrag = 0f }
                    )
                }
        )

        if (showTripResults) {
            TripResults(
                trips = savedTrips,
                onRemoveTrip = onRemoveTrip,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(maxHeight * 0.68f)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            ) {
                TripControls(
                    layoutScale = layoutScale,
                    minutes = minutes,
                    onMinutesChange = onMinutesChange,
                    onGo = onGo,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(12.dp * layoutScale))
                TransportPanel(
                    layoutScale = layoutScale,
                    transportTypes = transportTypes,
                    transportRoutes = transportRoutes,
                    selectedTransportIds = selectedTransportIds,
                    selectedRouteIds = selectedRouteIds,
                    expandedTransportId = expandedTransportId,
                    onExpandedTransportChange = { transportId ->
                        expandedTransportId = transportId.takeUnless {
                            it == expandedTransportId
                        }
                    },
                    onToggleTransport = onToggleTransport,
                    onToggleRoute = onToggleRoute,
                    modifier = Modifier.fillMaxWidth()
                )
                IntensityPanel(
                    layoutScale = layoutScale,
                    intensity = intensity,
                    onIntensityChange = onIntensityChange,
                    validationMessage = validationMessage,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(136.dp * layoutScale)
                )
            }
        }
        TransitHeader(
            layoutScale = layoutScale,
            onShowTrips = { onTripResultsVisibilityChange(true) },
            onShowSettings = { onTripResultsVisibilityChange(false) },
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

/** Places the profile, saved-trip, and settings symbols on the main-colour header. */
@Composable
private fun TransitHeader(
    layoutScale: Float,
    onShowTrips: () -> Unit,
    onShowSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(68.dp * layoutScale)
            .background(TransitMain, RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
            .padding(horizontal = 10.dp * layoutScale)
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_account_circle),
            contentDescription = "Profile",
            tint = TransitWhite,
            modifier = Modifier.size(54.dp * layoutScale)
        )
        IconButton(onClick = onShowTrips, modifier = Modifier.size(60.dp * layoutScale)) {
            Icon(
                painter = painterResource(Res.drawable.ic_receipt_long),
                contentDescription = "Show saved trips",
                tint = TransitWhite,
                modifier = Modifier.size(50.dp * layoutScale)
            )
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onShowSettings, modifier = Modifier.size(60.dp * layoutScale)) {
            Icon(
                painter = painterResource(Res.drawable.ic_settings),
                contentDescription = "Trip settings",
                tint = TransitWhite,
                modifier = Modifier.size(54.dp * layoutScale)
            )
        }
    }
}

/** Supplies sample state for the shared settings-screen preview. */
@Preview(showBackground = true, widthDp = 350, heightDp = 580)
@Composable
private fun SettingsPreview() {
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
        SettingsScreen(
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
