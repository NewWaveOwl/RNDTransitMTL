package com.example.rnd_transit_mtl.ui.global_ui_ellements.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.rnd_transit_mtl.model.geo.GeoPoint
import com.example.rnd_transit_mtl.model.trip.Trip

internal data class TransitMapData(
    val trip: Trip? = null,
    val progress: Float? = null,
    val recenterRequest: Int = 0,
    val interactive: Boolean = true,
    val topInset: Dp = 0.dp,
    val bottomInset: Dp = 0.dp,
    val sharedTripFlow: Boolean = false,
    val cameraInsetsReady: Boolean = true,
    val browsing: Boolean = false,
    val pickerOpen: Boolean = false,
    val attributionHandledByScreen: Boolean = false,
    val pin: GeoPoint? = null,
    val onPick: ((GeoPoint) -> Unit)? = null,
    val followPosition: Boolean = false,
    val onManualCameraMove: () -> Unit = {}
)

internal val LocalTransitMap = staticCompositionLocalOf<(@Composable (TransitMapData, Modifier) -> Unit)?> {
    null
}

// Android hosts the shared map outside navigation so tab transitions never move its surface.
internal val LocalSharedTransitMapHost = staticCompositionLocalOf<(@Composable (Modifier, Boolean) -> Unit)?> { null }

@Composable
internal fun TransitMap(
    data: TransitMapData,
    modifier: Modifier,
    bundledMap: @Composable () -> Unit
) {
    val renderer = LocalTransitMap.current
    if (renderer == null) bundledMap() else renderer(data, modifier)
}
