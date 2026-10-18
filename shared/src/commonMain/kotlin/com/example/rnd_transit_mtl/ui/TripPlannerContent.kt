package com.example.rnd_transit_mtl.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.rnd_transit_mtl.data.TripGenerationBounds
import com.example.rnd_transit_mtl.model.TransportRoute
import com.example.rnd_transit_mtl.model.TransportType
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitWhite
import org.jetbrains.compose.resources.painterResource
import rnd_transit_mtl.shared.generated.resources.Res
import rnd_transit_mtl.shared.generated.resources.map_sample

/** Stateless map-first planner. Selections and GO decisions belong to the parent. */
@Composable
fun TripPlannerContent(
    minutes: Int,
    onMinutesChange: (Int) -> Unit,
    transportTypes: List<TransportType>,
    transportRoutes: List<TransportRoute>,
    selectedTransportIds: List<String>,
    selectedRouteIds: List<String>,
    expandedTransportId: String?,
    onExpandedTransportChange: (String) -> Unit,
    onToggleTransport: (String) -> Unit,
    onToggleRoute: (String, String) -> Unit,
    intensity: Float,
    onIntensityChange: (Float) -> Unit,
    validationMessage: String,
    actionLabel: String,
    actionEnabled: Boolean,
    activeTripSummary: String?,
    pendingReviewSummary: String?,
    onPrimaryAction: () -> Unit,
    modifier: Modifier = Modifier,
    controlsVisible: Boolean = true,
    onMapViewportReady: (TripGenerationBounds) -> Unit = {},
    onControlsHidden: () -> Unit = {}
) {
    val controlsState = remember { MutableTransitionState(controlsVisible) }
    val latestControlsHidden by rememberUpdatedState(onControlsHidden)
    SideEffect { controlsState.targetState = controlsVisible }
    LaunchedEffect(controlsVisible, controlsState.isIdle, controlsState.currentState) {
        if (!controlsVisible && controlsState.isIdle && !controlsState.currentState) {
            latestControlsHidden()
        }
    }

    val mapPainter = painterResource(Res.drawable.map_sample)
    val sourceSize = validMapSourceSize(mapPainter.intrinsicSize)
    var viewport by remember { mutableStateOf(Size.Zero) }
    // Reserve the current-trip title and its zoom controls above new endpoints.
    val titleHeight = with(LocalDensity.current) { 128.dp.toPx() }
    val latestViewportReady by rememberUpdatedState(onMapViewportReady)
    LaunchedEffect(viewport, sourceSize, titleHeight) {
        if (viewport.width > 0f && viewport.height > 0f) {
            latestViewportReady(plannerTripBounds(viewport, sourceSize, titleHeight))
        }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize().clipToBounds().background(TransitMain)
            .onSizeChanged { viewport = Size(it.width.toFloat(), it.height.toFloat()) },
        contentAlignment = Alignment.TopCenter
    ) {
        val availableHeight = maxHeight
        // Wide windows expose the map around the panel's rounded upper corners.
        val panelTopRadius = if (maxWidth >= 600.dp) 24.dp else 0.dp
        val layoutScale = (maxWidth.value.coerceAtMost(430f) / 402f)
            .coerceIn(0.7f, 1.1f)
        val controlHeight = 96.dp * layoutScale
        // GO is centered in the available screen. Expanded choices remain scrollable.
        val topSpace = ((availableHeight - controlHeight) / 2f).coerceAtLeast(24.dp)

        Image(
            painter = mapPainter,
            contentDescription = "Bundled mock transit map",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Move every planner control down as one group; the map stays still.
        AnimatedVisibility(
            visibleState = controlsState,
            modifier = Modifier.widthIn(max = 620.dp).fillMaxSize(),
            enter = EnterTransition.None,
            exit = slideOutVertically(
                tween(GO_TRIP_TRANSITION_MILLIS),
                targetOffsetY = { it }
            )
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(topSpace))

                GOBox(
                    minutes = minutes,
                    onMinutesChange = onMinutesChange,
                    onGo = onPrimaryAction,
                    layoutScale = layoutScale,
                    actionLabel = actionLabel,
                    actionEnabled = actionEnabled,
                    minutesEnabled = actionEnabled,
                    modifier = Modifier.fillMaxWidth(0.92f)
                )

                // The parent freezes these labels while the group slides away.
                val summary = activeTripSummary ?: pendingReviewSummary
                if (summary != null || validationMessage.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .background(TransitWhite, RoundedCornerShape(12.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (summary != null) {
                            Text(summary, color = TransitMain,
                                style = MaterialTheme.typography.bodyMedium)
                        }
                        if (validationMessage.isNotEmpty()) {
                            Text(
                                text = validationMessage,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.semantics {
                                    liveRegion = LiveRegionMode.Polite
                                }
                            )
                        }
                    }
                } else {
                    Spacer(Modifier.height(12.dp))
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(
                            min = (availableHeight - topSpace - controlHeight - 12.dp)
                                .coerceAtLeast(0.dp)
                        )
                        .clip(
                            RoundedCornerShape(
                                topStart = panelTopRadius,
                                topEnd = panelTopRadius
                            )
                        )
                        .background(TransitMain)
                ) {
                    TransportPanel(
                        layoutScale = layoutScale,
                        transportTypes = transportTypes,
                        transportRoutes = transportRoutes,
                        selectedTransportIds = selectedTransportIds,
                        selectedRouteIds = selectedRouteIds,
                        expandedTransportId = expandedTransportId,
                        onExpandedTransportChange = onExpandedTransportChange,
                        onToggleTransport = onToggleTransport,
                        onToggleRoute = onToggleRoute,
                        modifier = Modifier.fillMaxWidth()
                    )

                    IntensityPanel(
                        layoutScale = layoutScale,
                        intensity = intensity,
                        onIntensityChange = onIntensityChange,
                        validationMessage = "",
                        modifier = Modifier.fillMaxWidth().height(120.dp * layoutScale)
                    )
                }
            }
        }
    }
}
