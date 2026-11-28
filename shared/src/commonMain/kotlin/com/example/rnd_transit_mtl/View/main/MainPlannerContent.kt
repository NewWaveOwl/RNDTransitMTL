package com.example.rnd_transit_mtl.View.main

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.rnd_transit_mtl.View.main.components.GOBox
import com.example.rnd_transit_mtl.View.main.components.GoBoxCompactHeight
import com.example.rnd_transit_mtl.View.main.components.PlannerDragStripHeight
import com.example.rnd_transit_mtl.View.main.components.PlannerPreferenceBands
import com.example.rnd_transit_mtl.View.main.components.PlannerSheetDragHandle
import com.example.rnd_transit_mtl.View.main.components.PlannerTripTypeBand
import com.example.rnd_transit_mtl.data.generation.TripGenerationBounds
import com.example.rnd_transit_mtl.data.location.LocalPlannerLocation
import com.example.rnd_transit_mtl.model.geo.GeoPoint
import com.example.rnd_transit_mtl.model.ids.TransportId
import com.example.rnd_transit_mtl.model.ids.ZoneId
import com.example.rnd_transit_mtl.model.transport.TransportType
import com.example.rnd_transit_mtl.model.trip.Place
import com.example.rnd_transit_mtl.model.trip.TripPoint
import com.example.rnd_transit_mtl.state.NullableTransportIdSaver
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.TransitMap
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.TransitMapData
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.plannerTripBounds
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.validMapSourceSize
import com.example.rnd_transit_mtl.ui.global_ui_ellements.transitions.GO_TRIP_TRANSITION_MILLIS
import com.example.rnd_transit_mtl.ui.theme.TransitComplementary
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitWhite
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import rnd_transit_mtl.shared.generated.resources.Res
import rnd_transit_mtl.shared.generated.resources.map_sample

// Layout settings — widths/padding in dp; fractions and reference coordinates keep the sheet responsive.
// Sheet stops live in PlannerSheetGeometry.kt; drag-handle size lives in components/PlannerSheetDragHandle.kt.
private val PlannerSheetMaxWidth = 620.dp
private val PlannerGoBoxHorizontalPadding = 24.dp
private val PlannerMapTitleClearance = 128.dp
private const val PlannerReferenceWidth = 412f
private const val PlannerMinimumScale = 0.7f
private const val PlannerMaximumScale = 1.5f

// Animation settings — sheet snap timing lives in PlannerSheetGeometry.kt; GO exit timing lives in TripTransition.kt.

/**
 * Displays a pannable map and four draggable preference positions.
 *
 * GO follows the sheet and provides a larger area to drag the same preferences panel.
 * Expanded content scrolls after the sheet reaches its top stop.
 * Sheet position is presentation state; all editable choices belong to the caller.
 *
 * @param minutes Available minutes displayed by GO.
 * @param onMinutesChange Requests a bounded time change.
 * @param transportTypes Supported transport catalogue.
 * @param selectedTransportIds Enabled transport IDs.
 * @param selectedZonesByTransport Independently selected zone IDs for each transit mode.
 * @param onToggleTransport Requests an independent transport toggle.
 * @param onToggleZone Requests a zone toggle for the supplied transport ID and zone ID.
 * @param walkingPreference Selected walking style, used for walking routes and transit access.
 * @param bikeSpeedKmh Selected cycling speed in km/h, used for accelerated pedestrian routing.
 * @param onBikeSpeedChange Requests a cycling-speed change.
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
 * @param onControlsHidden Runs after the controls fade out.
 * @param initialSheetPosition Initial position for previews or a new planner entry.
 * @param origin Selected sample-map starting point, or none.
 * @param onEditOrigin Opens the origin options without changing saved choices.
 * @param liveOrigin Confirmed live geographic starting point, when available.
 * @param originIsManual Whether the origin was manually placed rather than auto-tracked.
 * @param originStatus Optional automatic-origin availability message shown beneath the field.
 * @param onTapOrigin Stages a sample-map point for confirmation.
 * @param onTapLiveOrigin Stages a geographic map point for confirmation.
 * @param roundTrip Whether round-trip planning is currently selected.
 * @param onRoundTripChange Requests toggling between one-way and round-trip modes.
 * @param onRecenter Resolves and confirms a fresh live origin before moving the camera.
 * @param liveRecenterRequest Incremented when the live origin should be centered, even at the same point.
 */
@Composable
internal fun MainPlannerContent(
    minutes: Int,
    onMinutesChange: (Int) -> Unit,
    transportTypes: List<TransportType>,
    selectedTransportIds: List<TransportId>,
    selectedZonesByTransport: Map<TransportId, List<ZoneId>>,
    onToggleTransport: (TransportId) -> Unit,
    onToggleZone: (TransportId, ZoneId) -> Unit,
    walkingPreference: String,
    bikeSpeedKmh: Int,
    onBikeSpeedChange: (Int) -> Unit,
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
    initialSheetPosition: PlannerSheetPosition = PlannerSheetPosition.Main,
    origin: TripPoint? = TripPoint("Current location", 0.5f, 0.35f),
    onEditOrigin: () -> Unit = {},
    liveOrigin: Place? = null,
    originIsManual: Boolean = true,
    originStatus: String? = null,
    onTapOrigin: (TripPoint) -> Unit = {},
    onTapLiveOrigin: (GeoPoint) -> Unit = {},
    roundTrip: Boolean = false,
    onRoundTripChange: (Boolean) -> Unit = {},
    onRecenter: (() -> Unit)? = null,
    liveRecenterRequest: Int = 0,
) {
    val liveLocation = LocalPlannerLocation.current
    // Expansion is presentation state, separate from transport and zone selections.
    var expandedTransportId by rememberSaveable(stateSaver = NullableTransportIdSaver) { mutableStateOf<TransportId?>(null) }
    var recenterRequest by rememberSaveable { mutableStateOf(0) }

    val controlsOpacity = remember { Animatable(if (controlsVisible) 1f else 0f) }
    val latestControlsHidden by rememberUpdatedState(onControlsHidden)
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) controlsOpacity.snapTo(1f)
        else {
            controlsOpacity.animateTo(0f, tween(GO_TRIP_TRANSITION_MILLIS))
            latestControlsHidden()
        }
    }

    val painter = painterResource(Res.drawable.map_sample)
    val sourceSize = validMapSourceSize(painter.intrinsicSize)
    var viewport by remember { mutableStateOf(Size.Zero) }
    val density = LocalDensity.current
    val titleHeight = with(density) { PlannerMapTitleClearance.toPx() }
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
        contentAlignment = Alignment.TopCenter,
    ) {
        val panelWidth = maxWidth.coerceAtMost(PlannerSheetMaxWidth)
        val scale = (panelWidth.value / PlannerReferenceWidth).coerceIn(PlannerMinimumScale, PlannerMaximumScale)
        val heightPx = with(density) { maxHeight.toPx() }
        val unitPx = with(density) { scale.dp.toPx() }
        val dragStripHeight = PlannerDragStripHeight * scale
        val geometry = plannerSheetGeometry(heightPx, unitPx)
        val anchors = remember(geometry) {
            DraggableAnchors {
                PlannerSheetPosition.Collapsed at geometry.collapsed
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
        val nestedScroll = rememberPlannerSheetNestedScroll(sheet, list, enabled)

        val gapPx = geometry.goGap(sheetOffset)
        val goHeightPx = with(density) { (GoBoxCompactHeight * scale).toPx() }
        val goTop = (sheetOffset - goHeightPx - gapPx).coerceIn(0f, (heightPx - goHeightPx).coerceAtLeast(0f))
        val showMapControls = controlsVisible && goTop >= 100f * unitPx
        var originFieldHeight by remember { mutableIntStateOf(0) }
        val visibleMapTop = if (showMapControls) {
            (PlannerOriginFieldTopPadding * scale + with(density) { originFieldHeight.toDp() })
                .coerceAtMost(with(density) { goTop.toDp() })
        } else 0.dp

        TransitMap(
            data = TransitMapData(
                interactive = enabled,
                topInset = visibleMapTop,
                bottomInset = if (controlsVisible) with(density) { (heightPx - goTop).toDp() } else 0.dp,
                sharedTripFlow = true,
                attributionHandledByScreen = true,
                cameraInsetsReady = !showMapControls || originFieldHeight > 0,
                browsing = true,
                recenterRequest = liveRecenterRequest,
                pin = liveOrigin?.point,
                centerOnPin = originIsManual,
                onPick = if (enabled) onTapLiveOrigin else null,
            ),
            modifier = Modifier.fillMaxSize(),
        ) {
            FigmaOriginMap(
                origin = origin,
                enabled = enabled,
                recenterRequest = recenterRequest,
                onTap = onTapOrigin,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Box(Modifier.width(panelWidth).fillMaxHeight()) {
            PlannerOriginOverlay(
                visible = goTop >= 100f * unitPx,
                scale = scale,
                originValue = liveOrigin?.label ?: origin?.label ?: "Search a place or address",
                originStatus = originStatus,
                enabled = enabled && showMapControls,
                onEditOrigin = onEditOrigin,
                onMeasuredHeight = { originFieldHeight = it },
            )

            if (showMapControls) {
                val fraction = ((sheetOffset - geometry.routes) / (geometry.main - geometry.routes)).coerceIn(0f, 1f)
                // Figma: gap above GO is 15 dp in Main and 12 dp in Routes.
                val recenterGap = 12f + 3f * fraction
                val recenterTop = (goTop - (48f + recenterGap) * unitPx).coerceAtLeast(0f)
                FigmaRecenterButton(
                    enabled = enabled && (origin != null || liveLocation != null),
                    onClick = {
                        if (onRecenter != null) onRecenter()
                        else if (liveLocation != null) liveLocation.request()
                        else recenterRequest += 1
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 16.dp * scale)
                        .offset { IntOffset(0, recenterTop.roundToInt()) },
                )
            }

            if (controlsVisible && goTop > with(density) { 120.dp.toPx() }) {
                PlannerMapAttributionBadge(
                    goTopPx = goTop,
                    density = density,
                )
            }

            Column(
                Modifier
                    .offset { IntOffset(0, sheetOffset.roundToInt()) }
                    .fillMaxWidth()
                    .height(with(density) { (heightPx - sheetOffset).coerceAtLeast(0f).toDp() })
                    .graphicsLayer { alpha = controlsOpacity.value }
                    .background(TransitMain)
                    .nestedScroll(nestedScroll)
                    .anchoredDraggable(sheet, Orientation.Vertical, enabled = enabled),
            ) {
                val moveSheet: (PlannerSheetPosition) -> Unit = { position ->
                    if (enabled) {
                        scope.launch { sheet.animateTo(position, tween(PLANNER_SHEET_ANIMATION_DURATION_MILLIS)) }
                    }
                }
                PlannerSheetDragHandle(
                    currentTarget = sheet.targetValue,
                    dragStripHeight = dragStripHeight,
                    scale = scale,
                    enabled = enabled,
                    onMoveSheet = moveSheet,
                )

                LazyColumn(Modifier.fillMaxWidth().weight(1f), state = list, userScrollEnabled = enabled) {
                    val summary = activeTripSummary ?: pendingReviewSummary
                    if (summary != null || validationMessage.isNotEmpty()) {
                        item(key = "feedback") {
                            Column(Modifier.fillMaxWidth().background(TransitWhite).padding(16.dp)) {
                                summary?.let { Text(it, color = TransitComplementary) }
                                if (validationMessage.isNotEmpty()) {
                                    Text(
                                        validationMessage,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                    )
                                }
                            }
                        }
                    }
                    PlannerTripTypeBand(scale, roundTrip, onRoundTripChange, enabled)
                    PlannerPreferenceBands(
                        scale,
                        transportTypes,
                        selectedTransportIds,
                        selectedZonesByTransport,
                        onToggleTransport,
                        onToggleZone,
                        walkingPreference,
                        selectedAttractionIds,
                        onWalkingPreferenceChange,
                        onToggleAttraction,
                        enabled,
                        onOpenDetailedSettings,
                        expandedTransportId,
                        bikeSpeedKmh = bikeSpeedKmh,
                        onBikeSpeedChange = onBikeSpeedChange,
                        onExpandedTransportChange = { transportId ->
                            if (enabled) {
                                expandedTransportId = if (expandedTransportId == transportId) null else transportId
                            }
                        },
                    )
                }
            }
        }

        // Draw last so even a very short window cannot cover GO with the sheet.
        GOBox(
            minutes = minutes,
            onMinutesChange = onMinutesChange,
            onGo = onPrimaryAction,
            actionLabel = actionLabel,
            actionEnabled = actionEnabled && controlsVisible,
            minutesEnabled = enabled,
            layoutScale = scale,
            compact = true,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, goTop.roundToInt()) }
                .width(panelWidth)
                // GO enlarges the teal panel's drag area; both use the same anchors and state.
                .anchoredDraggable(sheet, Orientation.Vertical, enabled = enabled)
                .padding(horizontal = PlannerGoBoxHorizontalPadding * scale)
                .graphicsLayer { alpha = controlsOpacity.value },
        )
    }
}
