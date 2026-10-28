package com.example.rnd_transit_mtl.View.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.example.rnd_transit_mtl.View.main.components.GOBox
import com.example.rnd_transit_mtl.View.main.components.GoBoxCompactHeight
import com.example.rnd_transit_mtl.View.main.components.PlannerPreferenceBands
import com.example.rnd_transit_mtl.data.generation.TripGenerationBounds
import com.example.rnd_transit_mtl.model.transport.TransportType
import com.example.rnd_transit_mtl.model.trip.TripPoint
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.plannerTripBounds
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.validMapSourceSize
import com.example.rnd_transit_mtl.ui.global_ui_ellements.transitions.GO_TRIP_TRANSITION_MILLIS
import com.example.rnd_transit_mtl.ui.theme.*
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import rnd_transit_mtl.shared.generated.resources.Res
import rnd_transit_mtl.shared.generated.resources.map_sample

// Layout settings — widths/padding in dp; fractions and reference coordinates keep the sheet responsive.
private val PlannerSheetMaxWidth = 620.dp
private val PlannerGoBoxHorizontalPadding = 24.dp
private val PlannerOriginFieldHorizontalPadding = 16.dp
private val PlannerOriginFieldTopPadding = 16.dp
private val PlannerDragHandleWidth = 42.dp
private val PlannerDragHandleHeight = 4.dp
private val PlannerDragStripHeight = 16.dp
private val PlannerMapTitleClearance = 128.dp
private val PlannerReferenceWidth = 412f
private val PlannerReferenceViewportHeight = 800f
private val PlannerMinimumScale = 0.7f
private val PlannerMaximumScale = 1.5f
private val PlannerMainSheetTop = 400f
private val PlannerRoutesSheetTop = 256f
private val PlannerPreferencesSheetTop = 100f
private val PlannerShortWindowTopFraction = 0.3f
private val PlannerGoMainGap = 24f
private val PlannerGoRoutesGap = 7f
private val PlannerGoPreferencesGap = 12f

// Animation settings — larger millisecond values make snapping and handle taps slower.
private const val PLANNER_SHEET_ANIMATION_DURATION_MILLIS = 300
private const val PLANNER_ORIGIN_FIELD_ANIMATION_DURATION_MILLIS = 300
// GO exit timing is shared with navigation/footer transitions: edit ui/global_ui_ellements/transitions/TripTransition.kt.

/** Three resting positions corresponding to Figma main_screen, routes, and preferences. */
internal enum class PlannerSheetPosition { Main, Routes, Preferences }

/**
 * Displays a pannable sample map and three draggable preference positions.
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
 * @param bikeSpeedKmh Selected cycling speed in km/h, retained for future routing.
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
 * @param onControlsHidden Runs only after the complete slide-down transition.
 * @param initialSheetPosition Initial position for previews or a new planner entry.
 * @param origin Selected sample-map starting point, or none.
 * @param onEditOrigin Opens the origin options without changing saved choices.
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
    onEditOrigin: () -> Unit = {}
) {
    // Expansion is presentation state, separate from transport and zone selections.
    var expandedTransportId by rememberSaveable { mutableStateOf<String?>(null) }
    var recenterRequest by rememberSaveable {
        mutableStateOf(0)
    }

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
        contentAlignment = Alignment.TopCenter
    ) {
        // Reference frame: 412 x 892, with a 92 px header/status area.
        // Sheet handles sit at y=492, 348, 192; GO is 76 px tall with 24 px side margins.
        val panelWidth = maxWidth.coerceAtMost(PlannerSheetMaxWidth)
        val scale = (panelWidth.value / PlannerReferenceWidth).coerceIn(PlannerMinimumScale, PlannerMaximumScale)
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
                    sheet.settle(tween(PLANNER_SHEET_ANIMATION_DURATION_MILLIS))
                    return available
                }

                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                    if (!currentEnabled || !movedSheet) return Velocity.Zero
                    movedSheet = false
                    sheet.settle(tween(PLANNER_SHEET_ANIMATION_DURATION_MILLIS))
                    return available
                }
            }
        }

        FigmaOriginMap(
            origin = origin,
            enabled = enabled,
            recenterRequest = recenterRequest,
            modifier = Modifier.fillMaxSize()
        )


        // Move GO and the sheet together on trip opening; keep the map outside this layer.
        Box(Modifier.width(panelWidth).fillMaxHeight().graphicsLayer {
            translationY = exit.value * heightPx
        }) {
            val gapPx = geometry.goGap(sheetOffset)
            val goHeightPx = with(density) { (GoBoxCompactHeight * scale).toPx() }
            val goTop = (sheetOffset - goHeightPx - gapPx).coerceAtLeast(0f)

            val unitPx = with(density) { scale.dp.toPx() }

            // Hide these controls as GO approaches the fully expanded position.
            val showMapControls =
                controlsVisible && goTop >= 100f * unitPx

            // Keep the field in the shared moving layer during GO's slide-down exit.
            // Sheet expansion hides it upward; pulling the sheet down reveals it smoothly.
            AnimatedVisibility(
                visible = goTop >= 100f * unitPx,
                enter = slideInVertically(
                    animationSpec = tween(PLANNER_ORIGIN_FIELD_ANIMATION_DURATION_MILLIS),
                    initialOffsetY = { -it }
                ) + fadeIn(tween(PLANNER_ORIGIN_FIELD_ANIMATION_DURATION_MILLIS)),
                exit = slideOutVertically(
                    animationSpec = tween(PLANNER_ORIGIN_FIELD_ANIMATION_DURATION_MILLIS),
                    targetOffsetY = { -it }
                ) + fadeOut(tween(PLANNER_ORIGIN_FIELD_ANIMATION_DURATION_MILLIS)),
                modifier = Modifier
                    .padding(horizontal = PlannerOriginFieldHorizontalPadding * scale)
                    .offset(y = PlannerOriginFieldTopPadding * scale)
            ) {
                TripLabelValueField(
                    label = "Starting point",
                    value = origin?.label ?: "Search a place or address",
                    showLocationIcon = true,
                    modifier = Modifier.clickable(
                        enabled = enabled && showMapControls,
                        role = Role.Button,
                        onClickLabel = "Choose starting point",
                        onClick = onEditOrigin
                    )
                )
            }

            if (showMapControls) {
                val fraction = (
                    (sheetOffset - geometry.routes) /
                        (geometry.main - geometry.routes)
                ).coerceIn(0f, 1f)

                // Figma: gap above GO is 15 dp in Main and 12 dp in Routes.
                val recenterGap = 12f + 3f * fraction
                val recenterTop = (
                    goTop - (48f + recenterGap) * unitPx
                ).coerceAtLeast(0f)

                FigmaRecenterButton(
                    enabled = enabled && origin != null,
                    onClick = { recenterRequest += 1 },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 16.dp * scale)
                        .offset {
                            IntOffset(0, recenterTop.roundToInt())
                        }
                )
            }

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
                    .fillMaxWidth().padding(horizontal = PlannerGoBoxHorizontalPadding * scale)
            )
            Column(
                Modifier.offset { IntOffset(0, sheetOffset.roundToInt()) }
                    .fillMaxWidth()
                    .height(with(density) { (heightPx - sheetOffset).coerceAtLeast(0f).toDp() })
                    .background(TransitMain)
                    .nestedScroll(nestedScroll)
                    .anchoredDraggable(sheet, Orientation.Vertical, enabled = enabled)
            ) {
                fun moveSheet(position: PlannerSheetPosition) {
                    if (!enabled) return

                    scope.launch {
                        sheet.animateTo(
                            position,
                            tween(PLANNER_SHEET_ANIMATION_DURATION_MILLIS)
                        )
                    }
                }

                fun expandSheet() {
                    moveSheet(
                        when (sheet.targetValue) {
                            PlannerSheetPosition.Main -> PlannerSheetPosition.Routes
                            PlannerSheetPosition.Routes -> PlannerSheetPosition.Preferences
                            PlannerSheetPosition.Preferences -> PlannerSheetPosition.Preferences
                        }
                    )
                }

                fun collapseSheet() {
                    moveSheet(
                        when (sheet.targetValue) {
                            PlannerSheetPosition.Main -> PlannerSheetPosition.Main
                            PlannerSheetPosition.Routes -> PlannerSheetPosition.Main
                            PlannerSheetPosition.Preferences -> PlannerSheetPosition.Routes
                        }
                    )
                }

                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(PlannerDragStripHeight * scale)
                        .background(TransitComplementary)
                        .onPreviewKeyEvent { event ->
                            if (!enabled || event.type != KeyEventType.KeyDown) {
                                false
                            } else {
                                when (event.key) {
                                    Key.DirectionUp -> {
                                        expandSheet()
                                        true
                                    }
                                    Key.DirectionDown -> {
                                        collapseSheet()
                                        true
                                    }
                                    Key.MoveHome -> {
                                        moveSheet(PlannerSheetPosition.Main)
                                        true
                                    }
                                    Key.MoveEnd -> {
                                        moveSheet(PlannerSheetPosition.Preferences)
                                        true
                                    }
                                    else -> false
                                }
                            }
                        }
                        .clickable(
                            enabled = enabled,
                            role = Role.Button,
                            onClickLabel = "Expand or collapse preferences"
                        ) {
                            moveSheet(
                                when (sheet.targetValue) {
                                    PlannerSheetPosition.Main -> PlannerSheetPosition.Routes
                                    PlannerSheetPosition.Routes -> PlannerSheetPosition.Preferences
                                    PlannerSheetPosition.Preferences -> PlannerSheetPosition.Main
                                }
                            )
                        }
                        .semantics {
                            contentDescription = "Trip preferences"
                            stateDescription = sheet.targetValue.name
                            customActions = listOf(
                                CustomAccessibilityAction("Expand preferences") {
                                    if (enabled) expandSheet()
                                    enabled
                                },
                                CustomAccessibilityAction("Collapse preferences") {
                                    if (enabled) collapseSheet()
                                    enabled
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .size(
                                PlannerDragHandleWidth * scale,
                                PlannerDragHandleHeight * scale
                            )
                            .background(
                                TransitWhite,
                                RoundedCornerShape(2.dp)
                            )
                    )
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
                        onWalkingPreferenceChange, onToggleAttraction,
                        enabled, onOpenDetailedSettings, expandedTransportId,
                        bikeSpeedKmh = bikeSpeedKmh,
                        intensity = intensity, onIntensityChange = onIntensityChange,
                        onBikeSpeedChange = onBikeSpeedChange,
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
        (PlannerGoRoutesGap + (PlannerGoMainGap - PlannerGoRoutesGap) * fraction) * scalePx
    } else {
        val fraction = ((offset - preferences) / (routes - preferences)).coerceIn(0f, 1f)
        (PlannerGoPreferencesGap + (PlannerGoRoutesGap - PlannerGoPreferencesGap) * fraction) * scalePx
    }
}

/** Fits Figma handle positions below the existing header, preserving usable space on short windows.
 * @param heightPx Available viewport height below the shared header.
 * @param scalePx Pixels per Figma reference unit, including density and responsive width scaling.
 */
internal fun plannerSheetGeometry(heightPx: Float, scalePx: Float): PlannerSheetGeometry {
    val top = (PlannerPreferencesSheetTop * scalePx).coerceAtMost(heightPx * PlannerShortWindowTopFraction)
    val remaining = (heightPx - top).coerceAtLeast(1f)
    return PlannerSheetGeometry(
        main = top + remaining * ((PlannerMainSheetTop - PlannerPreferencesSheetTop) / (PlannerReferenceViewportHeight - PlannerPreferencesSheetTop)),
        routes = top + remaining * ((PlannerRoutesSheetTop - PlannerPreferencesSheetTop) / (PlannerReferenceViewportHeight - PlannerPreferencesSheetTop)),
        preferences = top,
        scalePx = scalePx
    )
}
