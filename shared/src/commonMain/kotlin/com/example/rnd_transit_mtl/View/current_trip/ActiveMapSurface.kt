package com.example.rnd_transit_mtl.View.current_trip

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.rnd_transit_mtl.data.assets.toSampleMapPoint
import com.example.rnd_transit_mtl.model.geo.GeoPoint
import com.example.rnd_transit_mtl.model.geo.uncertaintyRing
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.model.trip.explorationWaypoints
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.MockTripMap
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.TransitMap
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.TransitMapData
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.toSampleMapLegs

// Layout settings — fills the parent viewport; camera insets are supplied in dp by ActiveMapContent.kt.

/**
 * Renders either the live platform [TransitMap] or the calibrated fallback [MockTripMap] for an active trip.
 *
 * @param liveMap Whether a platform live map host is provided.
 * @param trip Active trip supplying origin, destination, waypoints, and route legs.
 * @param retainedMapProgress Route progress fraction (`0f..1f`) shown on the map.
 * @param positionAvailable Whether a simulated or measured user position can be displayed.
 * @param renderedUserPosition Smoothed WGS84 user coordinate (`latitude`, `longitude`), when available.
 * @param userUncertaintyMeters Optional horizontal GPS uncertainty radius in metres around [renderedUserPosition].
 * @param simulationEnabled Whether position playback is simulated along the route.
 * @param recenterRequest Monotonic counter incremented when the user taps recenter.
 * @param followPosition Whether the camera tracks the moving user position.
 * @param mapInteractive Whether pan/zoom gestures are enabled on the map.
 * @param topInset Measured top overlay height in dp reserved for camera framing.
 * @param bottomInset Measured bottom sheet height in dp reserved for camera framing.
 * @param onManualCameraMove Callback invoked when a user gesture detaches follow-position tracking.
 */
@Composable
internal fun ActiveMapSurface(
    liveMap: Boolean,
    trip: Trip,
    retainedMapProgress: Float,
    positionAvailable: Boolean,
    renderedUserPosition: GeoPoint?,
    userUncertaintyMeters: Double?,
    simulationEnabled: Boolean,
    recenterRequest: Int,
    followPosition: Boolean,
    mapInteractive: Boolean,
    topInset: Dp,
    bottomInset: Dp,
    onManualCameraMove: () -> Unit,
) {
    if (liveMap) {
        TransitMap(
            data = TransitMapData(
                trip = trip,
                progress = retainedMapProgress.takeIf { positionAvailable },
                userPosition = renderedUserPosition.takeIf { !simulationEnabled && positionAvailable },
                userUncertaintyMeters = userUncertaintyMeters.takeIf { !simulationEnabled && positionAvailable },
                simulatePosition = simulationEnabled,
                recenterRequest = recenterRequest,
                followPosition = followPosition,
                onManualCameraMove = onManualCameraMove,
                interactive = mapInteractive,
                topInset = topInset,
                bottomInset = bottomInset,
                sharedTripFlow = true,
                cameraInsetsReady = topInset > 0.dp && bottomInset > 0.dp,
            ),
            modifier = Modifier.fillMaxSize(),
        ) {}
    } else {
        // Keep the bundled map's viewport fixed just like Android's shared map.
        // The panel and directions are overlays, never map-size constraints.
        MockTripMap(
            start = trip.origin.toSampleMapPoint(),
            destination = trip.destination.toSampleMapPoint(),
            waypoints = trip.explorationWaypoints.map { it.toSampleMapPoint() },
            legs = trip.toSampleMapLegs(),
            progress = retainedMapProgress,
            interactive = mapInteractive,
            matchPlannerBackground = true,
            followPosition = followPosition,
            onManualCameraMove = onManualCameraMove,
            showControls = false,
            showPosition = positionAvailable,
            userPosition = renderedUserPosition?.toSampleMapPoint("Your location")
                .takeIf { !simulationEnabled && positionAvailable },
            userUncertaintyRing = remember(renderedUserPosition, userUncertaintyMeters, simulationEnabled) {
                if (simulationEnabled) emptyList()
                else renderedUserPosition?.let { point ->
                    userUncertaintyMeters?.let { radius ->
                        point.uncertaintyRing(radius).map { it.toSampleMapPoint("Location uncertainty") }
                    }
                }.orEmpty()
            },
            recenterRequest = recenterRequest,
            topInset = topInset,
            bottomInset = bottomInset,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
