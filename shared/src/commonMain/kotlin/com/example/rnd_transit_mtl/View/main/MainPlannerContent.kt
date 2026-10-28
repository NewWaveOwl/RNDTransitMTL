package com.example.rnd_transit_mtl.View.main

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.example.rnd_transit_mtl.View.main.components.PlannerPreferenceBands
import com.example.rnd_transit_mtl.data.TripGenerationBounds
import com.example.rnd_transit_mtl.model.TransportType
import com.example.rnd_transit_mtl.ui.GOBox
import com.example.rnd_transit_mtl.ui.GO_TRIP_TRANSITION_MILLIS
import com.example.rnd_transit_mtl.ui.plannerTripBounds
import com.example.rnd_transit_mtl.ui.validMapSourceSize
import com.example.rnd_transit_mtl.ui.theme.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import rnd_transit_mtl.shared.generated.resources.Res
import rnd_transit_mtl.shared.generated.resources.map_sample
import kotlin.math.roundToInt

/** Three resting positions corresponding to Figma main_screen, routes, and preferences. */
internal enum class PlannerSheetPosition { Main, Routes, Preferences }

/**
 * Displays a stationary sample map and three draggable preference positions.
 *
 * GO follows the sheet. Expanded content scrolls after the sheet reaches its top stop.
 * Sheet position is presentation state; all editable choices belong to the caller.
 *
 * @param minutes Available minutes displayed by GO.
 * @param onMinutesChange Requests a bounded time change.
 * @param transportTypes Supported transport catalogue.
 * @param selectedTransportIds Enabled transport IDs.
 * @param selectedZonesByTransport Independently selected zone IDs for each transit mode.
 * @param onToggleTransport Requests an independent transport toggle.
 * @param onToggleZone Requests a zone toggle for the supplied transport ID and zone ID.
 * @param intensity Attraction intensity from 0 to 100.
 * @param onIntensityChange Requests an intensity change.
 * @param walkingPreference Selected walking style, retained for future routing.
 * @param selectedAttractionIds Selected attraction labels, retained for future routing.
 * @param onWalkingPreferenceChange Requests a walking-style change.
 * @param onToggleAttraction Requests an attraction toggle.
 * @param validationMessage Validation feedback from the coordinator.
 * @param actionLabel GO, Resume trip, or the completion action.
 * @param actionEnabled Whether the primary action can run.
 * @param inputsEnabled Whether planner choices and sheet gestures can change.
 * @param activeTripSummary Description of an unfinished trip.
 * @param pendingReviewSummary Description of a completed trip awaiting review.
 * @param onPrimaryAction Invokes the existing trip coordinator.
 * @param onOpenDetailedSettings Opens detailed Settings.
 * @param modifier Layout supplied by the caller.
 * @param controlsVisible Whether planner controls should remain visible.
 * @param onMapViewportReady Reports normalized map-generation bounds.
 * @param onControlsHidden Runs only after the complete slide-down transition.
 * @param initialSheetPosition Initial position for previews or a new planner entry.
 */
@Composable
internal fun MainPlannerContent(
    minutes: Int,
    onMinutesChange: (Int) -> Unit,
    transportTypes: List<TransportType>,
    selectedTransportIds: List<String>,
    selectedZonesByTransport: Map<String, List<String>>,
    onToggleTransport: (String) -> Unit,
    onToggleZone: (String, String) -> Unit,
    intensity: Float,
    onIntensityChange: (Float) -> Unit,
    walkingPreference: String,
    selectedAttractionIds: List<String>,
    onWalkingPreferenceChange: (String) -> Unit,
    onToggleAttraction: (String) -> Unit,
    validationMessage: String,
    actionLabel: String,
    actionEnabled: Boolean,
    inputsEnabled: Boolean,
    activeTripSummary: String?,
    pendingReviewSummary: String?,
    onPrimaryAction: () -> Unit,
    onOpenDetailedSettings: () -> Unit,
    modifier: Modifier = Modifier,
    controlsVisible: Boolean = true,
    onMapViewportReady: (TripGenerationBounds) -> Unit = {},
    onControlsHidden: () -> Unit = {},
    initialSheetPosition: PlannerSheetPosition = PlannerSheetPosition.Main
) {
    // Expansion is presentation state, separate from transport and zone selections.
    var expandedTransportId by rememberSaveable { mutableStateOf<String?>(null) }
    val exit = remember { Animatable(if (controlsVisible) 0f else 1f) }
    val latestControlsHidden by rememberUpdatedState(onControlsHidden)
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) exit.snapTo(0f) else {
            exit.animateTo(1f, tween(GO_TRIP_TRANSITION_MILLIS))
            latestControlsHidden()
        }
    }

    val painter = painterResource(Res.drawable.map_sample)
    val sourceSize = validMapSourceSize(painter.intrinsicSize)
    var viewport by remember { mutableStateOf(Size.Zero) }
    val density = LocalDensity.current
    val titleHeight = with(density) { 128.dp.toPx() }
    val latestViewportReady by rememberUpdatedState(onMapViewportReady)
    LaunchedEffect(viewport, sourceSize, titleHeight) {
        if (viewport.width > 0f && viewport.height > 0f) {
            latestViewportReady(plannerTripBounds(viewport, sourceSize, titleHeight))
        }
    }

    BoxWithConstraints(
        modifier.fillMaxSize().clipToBounds().onSizeChanged {
            viewport = Size(it.width.toFloat(), it.height.toFloat())
        },
        contentAlignment = Alignment.TopCenter
    ) {
        // Reference frame: 412 x 892, with a 92 px header/status area.
        // Sheet handles sit at y=492, 348, 192; GO is 76 px tall with 24 px side margins.
        val panelWidth = maxWidth.coerceAtMost(620.dp)
        val scale = (panelWidth.value / 412f).coerceIn(0.7f, 1.5f)
        val heightPx = with(density) { maxHeight.toPx() }
        val geometry = plannerSheetGeometry(heightPx, with(density) { scale.dp.toPx() })
        val anchors = remember(geometry) {
            DraggableAnchors {
                PlannerSheetPosition.Main at geometry.main
                PlannerSheetPosition.Routes at geometry.routes
                PlannerSheetPosition.Preferences at geometry.preferences
            }
        }
        val sheet = rememberSaveable(saver = AnchoredDraggableState.Saver()) {
            AnchoredDraggableState(initialSheetPosition, anchors)
        }
        SideEffect { sheet.updateAnchors(anchors, sheet.targetValue) }
        val sheetOffset = sheet.offset.takeIf { it.isFinite() } ?: geometry.main
        val scope = rememberCoroutineScope()
        val list = rememberLazyListState()
        LaunchedEffect(sheet.settledValue) {
            // Returning to a lower stop restores the transport overview, keeping draft choices.
            if (sheet.settledValue != PlannerSheetPosition.Preferences) list.scrollToItem(0)
        }
        val enabled = inputsEnabled && controlsVisible
        val currentEnabled by rememberUpdatedState(enabled)
        val nestedScroll = remember(sheet, list) {
            object : NestedScrollConnection {
                private var movedSheet = false

                private fun consume(delta: Float): Offset {
                    val consumed = sheet.dispatchRawDelta(delta)
                    movedSheet = movedSheet || consumed != 0f
                    return Offset(0f, consumed)
                }

                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (!currentEnabled || source != NestedScrollSource.UserInput) return Offset.Zero
                    return if (available.y < 0f || !list.canScrollBackward) consume(available.y)
                    else Offset.Zero
                }

                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                    if (currentEnabled && source == NestedScrollSource.UserInput) consume(available.y)
                    else Offset.Zero

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (!currentEnabled || !movedSheet) return Velocity.Zero
                    movedSheet = false
                    sheet.settle(tween(300))
                    return available
                }

                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                    if (!currentEnabled || !movedSheet) return Velocity.Zero
                    movedSheet = false
                    sheet.settle(tween(300))
                    return available
                }
            }
        }

        Image(painter, "Bundled sample transit map", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)

        // Move GO and the sheet together on trip opening; keep the map outside this layer.
        Box(Modifier.width(panelWidth).fillMaxHeight().graphicsLayer {
            translationY = exit.value * heightPx
        }) {
            val gapPx = geometry.goGap(sheetOffset)
            val goHeightPx = with(density) { (76.dp * scale).toPx() }
            val goTop = (sheetOffset - goHeightPx - gapPx).coerceAtLeast(0f)

            if (goTop > with(density) { 120.dp.toPx() }) {
                Surface(
                    modifier = Modifier.offset { IntOffset(0, (goTop - with(density) { 22.dp.toPx() }).roundToInt()) }
                        .padding(start = 8.dp),
                    color = TransitWhite,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("© OpenStreetMap contributors · Sample map", color = TransitComplementary,
                        style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
            GOBox(
                minutes = minutes,
                onMinutesChange = onMinutesChange,
                onGo = onPrimaryAction,
                actionLabel = actionLabel,
                actionEnabled = actionEnabled,
                minutesEnabled = inputsEnabled,
                layoutScale = scale,
                compact = true,
                modifier = Modifier.offset { IntOffset(0, goTop.roundToInt()) }
                    .fillMaxWidth().padding(horizontal = 24.dp * scale)
            )
            Column(
                Modifier.offset { IntOffset(0, sheetOffset.roundToInt()) }
                    .fillMaxWidth()
                    .height(with(density) { (heightPx - sheetOffset).coerceAtLeast(0f).toDp() })
                    .background(TransitMain)
                    .nestedScroll(nestedScroll)
                    .anchoredDraggable(sheet, Orientation.Vertical, enabled = enabled)
            ) {
                Box(
                    Modifier.fillMaxWidth().height(16.dp * scale).background(TransitComplementary)
                        .clickable(enabled = enabled, role = Role.Button,
                            onClickLabel = "Show next preference position") {
                            scope.launch {
                                val next = when (sheet.currentValue) {
                                    PlannerSheetPosition.Main -> PlannerSheetPosition.Routes
                                    PlannerSheetPosition.Routes -> PlannerSheetPosition.Preferences
                                    PlannerSheetPosition.Preferences -> PlannerSheetPosition.Main
                                }
                                sheet.animateTo(next, tween(300))
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.size(42.dp * scale, 4.dp * scale)
                        .background(TransitWhite, RoundedCornerShape(2.dp)))
                }
                LazyColumn(Modifier.fillMaxWidth().weight(1f), state = list, userScrollEnabled = enabled) {
                    val summary = activeTripSummary ?: pendingReviewSummary
                    if (summary != null || validationMessage.isNotEmpty()) {
                        item(key = "feedback") {
                            Column(Modifier.fillMaxWidth().background(TransitWhite).padding(16.dp)) {
                                summary?.let { Text(it, color = TransitComplementary) }
                                if (validationMessage.isNotEmpty()) {
                                    Text(validationMessage, color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                                }
                            }
                        }
                    }
                    PlannerPreferenceBands(
                        scale, transportTypes, selectedTransportIds, selectedZonesByTransport,
                        onToggleTransport, onToggleZone, walkingPreference, selectedAttractionIds,
                        onWalkingPreferenceChange, onToggleAttraction, intensity, onIntensityChange,
                        enabled, onOpenDetailedSettings, expandedTransportId,
                        onExpandedTransportChange = { transportId ->
                            if (enabled) {
                                expandedTransportId = if (expandedTransportId == transportId) null else transportId
                            }
                        }
                    )
                }
            }
        }
    }
}

/** Pixel positions of the three stops and GO gaps, independent of density. */
internal data class PlannerSheetGeometry(
    val main: Float,
    val routes: Float,
    val preferences: Float,
    val scalePx: Float
) {
    /** Interpolates the Figma spacing so GO follows the handle continuously.
     * @param offset Current sheet-handle offset in pixels.
     */
    fun goGap(offset: Float): Float = if (offset >= routes) {
        val fraction = ((offset - routes) / (main - routes)).coerceIn(0f, 1f)
        (7f + 17f * fraction) * scalePx
    } else {
        val fraction = ((offset - preferences) / (routes - preferences)).coerceIn(0f, 1f)
        (12f - 5f * fraction) * scalePx
    }
}

/** Fits Figma handle positions below the existing header, preserving usable space on short windows.
 * @param heightPx Available viewport height below the shared header.
 * @param scalePx Pixels per Figma reference unit, including density and responsive width scaling.
 */
internal fun plannerSheetGeometry(heightPx: Float, scalePx: Float): PlannerSheetGeometry {
    val top = (100f * scalePx).coerceAtMost(heightPx * 0.3f)
    val remaining = (heightPx - top).coerceAtLeast(1f)
    return PlannerSheetGeometry(
        main = top + remaining * (300f / 700f),
        routes = top + remaining * (156f / 700f),
        preferences = top,
        scalePx = scalePx
    )
}
