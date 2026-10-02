package com.example.rnd_transit_mtl.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
 * Coordinates the map, GOBox, expandable transport panel, intensity, and saved trips.
 *
 * The parent owns trip selections and saved results. This composable remembers
 * only which transport route list is expanded.
 *
 * @param minutes Selected trip duration in minutes.
 * @param onMinutesChange Receives the new duration when the user taps an arrow or drags the minute selector.
 * @param transportTypes Available transport options in display order.
 * @param transportRoutes Available routes, each linked to its owning transport type.
 * @param selectedTransportIds IDs of the transport modes currently included in the trip.
 * @param selectedRouteIds IDs of all selected routes across transport modes.
 * @param savedTrips Saved trip summaries available in the results view.
 * @param showTripResults Whether to display saved results instead of the planner controls.
 * @param onToggleTransport Toggles a transport ID in the parent-owned selection.
 * @param onToggleRoute Toggles a route using its transport ID and route ID.
 * @param intensity Current attraction intensity on the 0 to 100 scale.
 * @param onIntensityChange Receives the intensity chosen by tapping or dragging the slider.
 * @param validationMessage Validation message to display, or an empty string when there is no error.
 * @param onTripResultsVisibilityChange Requests showing or hiding saved results in response to a map swipe.
 * @param onRemoveTrip Receives the zero-based index of the saved trip to remove.
 * @param onGo Validates the current selections and requests creation of a trip summary.
 * @param modifier Layout and appearance modifiers supplied by the parent.
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
    /** Remember the open route list across recompositions; null means all lists are collapsed. */
    var expandedTransportId by remember { mutableStateOf<String?>(null) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(TransitMain)
    ) {
        /** Scale controls from the 402-unit reference width and limit extreme size changes. */
        val layoutScale = (maxWidth.value / 402f).coerceIn(0.7f, 1.4f)
        Image(
            painter = painterResource(Res.drawable.map_sample),
            contentDescription = "Illustrated transit map. Swipe left to show saved trips or right to return to trip selection.",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(savedTrips, showTripResults) {
                    /** Accumulate horizontal movement in pixels and apply navigation when the drag ends. */
                    var horizontalDrag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { horizontalDrag = 0f },
                        onHorizontalDrag = { _, dragAmount -> horizontalDrag += dragAmount },
                        onDragEnd = {
                            /**
                             * A left swipe of at least 80 pixels opens results when trips exist.
                             * A right swipe of at least 80 pixels returns from results to the planner.
                             */
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

        /** Switch between saved results and the planning controls using parent-owned visibility state. */
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
            /** Allow the bottom controls to scroll when their content exceeds the available height. */
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .heightIn(max = maxHeight)
                    .verticalScroll(rememberScrollState())
            ) {
                GOBox(
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
                        /** Tapping the open transport collapses it; tapping another opens that route list. */
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