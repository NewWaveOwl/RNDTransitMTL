package com.example.rnd_transit_mtl.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.unit.dp
import rnd_transit_mtl.shared.generated.resources.Res
import rnd_transit_mtl.shared.generated.resources.map_sample
import com.example.rnd_transit_mtl.model.TransportRoute
import com.example.rnd_transit_mtl.model.TransportType
import com.example.rnd_transit_mtl.ui.theme.TransitMain

/**
 * Coordinates the transit settings sections over the responsive map background.
 *
 * The caller owns persistent trip state. This composable owns only the temporary expanded
 * transport ID and passes display work to focused, stateless section composables.
 */
@Composable
fun TripPlannerContent(
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
    }
}

