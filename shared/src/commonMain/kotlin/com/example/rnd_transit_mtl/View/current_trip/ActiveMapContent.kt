package com.example.rnd_transit_mtl.View.current_trip

import com.example.rnd_transit_mtl.data.trips.LocalCloudTrips

import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.TransitMap
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.TransitMapData
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.LocalTransitMap
import com.example.rnd_transit_mtl.data.assets.toSampleMapPoint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.model.trip.guidanceAt
import com.example.rnd_transit_mtl.model.trip.compactGuidanceAt
import com.example.rnd_transit_mtl.model.geo.GeoPoint
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.MockTripMap
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.toSampleMapLegs
import com.example.rnd_transit_mtl.ui.global_ui_ellements.transitions.tripPanelEntrance
import com.example.rnd_transit_mtl.ui.theme.*
import kotlin.math.ceil
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.painterResource
import rnd_transit_mtl.shared.generated.resources.Res
import rnd_transit_mtl.shared.generated.resources.rnd_recenter
import rnd_transit_mtl.shared.generated.resources.rnd_turn_left
import rnd_transit_mtl.shared.generated.resources.rnd_warning

// Layout settings — dimensions in dp; the panel fraction keeps part of the route visible.
private const val ActiveMapPanelMaximumHeightFraction = 0.56f
private val ActiveMapHorizontalPadding = 24.dp
private val ActiveMapPanelPadding = 16.dp
private val ActiveMapPanelTopPadding = 4.dp
private val ActiveMapPanelBottomPadding = 12.dp
private val ActiveMapPanelHeaderSpacing = 4.dp
private val ActiveMapActionSpacing = 12.dp
private val ActiveMapDialogMaximumWidth = 620.dp
private val ActiveMapProgressHeight = 64.dp
private val ActiveMapProgressCornerRadius = 24.dp
private val ActiveMapPercentageWidth = 64.dp
private val ActiveMapPercentageInset = 8.dp
private val ActiveMapButtonMinimumHeight = 52.dp
private val ActiveMapRecenterSize = 48.dp
private val ActiveMapInstructionIconSize = 32.dp
private val ActiveMapWarningIconSize = 26.dp
private val ActiveMapWarningMinimumHeight = 88.dp
private val ActiveMapLegIndicatorSize = 12.dp
private val ActiveMapPanelHandleHeight = 24.dp
private val ActiveMapPanelMinimumHeight = 76.dp

// Animation settings — details grow down from the compact hint and retract upwards.
private const val ACTIVE_TRIP_DIRECTIONS_ANIMATION_DURATION_MILLIS = 280
private const val ACTIVE_TRIP_DIRECTIONS_FADE_DURATION_MILLIS = 160

/**
 * Optional presentation override for provider or preview instructions.
 * With no override, the panel derives guidance from the saved trip legs.
 * @param title Routing instruction heading.
 * @param detail Routing instruction details.
 * @param showLeftTurnIcon Whether to use the exported instruction icon.
 * @param nextAction Upcoming manoeuvre or section transition, when supplied.
 */
data class ActiveMapInstruction(
    val title: String,
    val detail: String,
    val showLeftTurnIcon: Boolean = false,
    val nextAction: String? = null
)

/** Explicit location availability; NotTracking is a paused route, not a GPS error. */
enum class TripLocationState {
    Sample,
    Stale,
    Missing,
    NotTracking,
    Live,
    LastKnown,
    Estimated,
    EstimatedStale,
    Waiting,
    PermissionDenied
}

/** Shared active-trip/overview map with explicit completion and confirmed cancellation.
 * @param trip Stored trip whose identity and route remain unchanged.
 * @param progress Estimated fraction from 0 to 1; invalid values display as zero.
 * @param actionsEnabled Whether lifecycle and navigation permit interaction.
 * @param onFinish Finishes the demo early, or requests review after completion.
 * @param onConfirmCancel Cancels only after confirmation; true when the user chose to save the trip to History.
 * @param modifier Parent layout constraints.
 * @param instruction Real routing instructions, or null for mock routes.
 * @param staleMessage Real location warning, or null when unavailable.
 * @param operationMessage Feedback from a trip operation.
 * @param mainOverview Whether to show the Main resume presentation.
 * @param onResume Opens the same stored trip.
 * @param onShowInformation Opens the existing information dialog.
 * @param locationState Explicit sample, stale, or missing location state.
 * @param sampleStateLabel Optional fixture label, never inferred from provider errors.
 * @param simulationEnabled Whether to label playback and offer the simulated finish action.
 * @param userPosition Measured location or an explicitly labelled estimate along the route.
 * @param onRequestLocation Requests a fresh reading or permission for recentering.
 */
@Composable
fun ActiveMapContent(
    trip: Trip,
    progress: Float,
    actionsEnabled: Boolean,
    onFinish: () -> Unit,
    onConfirmCancel: (keepInHistory: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    instruction: ActiveMapInstruction? = null,
    staleMessage: String? = null,
    operationMessage: String? = null,
    mainOverview: Boolean = false,
    onResume: () -> Unit = {},
    onShowInformation: () -> Unit = {},
    locationState: TripLocationState = TripLocationState.Sample,
    sampleStateLabel: String? = null,
    simulationEnabled: Boolean = true,
    userPosition: GeoPoint? = null,
    onRequestLocation: () -> Unit = {},
) {
    val fraction = if (progress.isFinite()) {
        progress.coerceIn(0f, 1f)
    } else {
        0f
    }
    val completed = fraction >= 1f
    val positionAvailable = if (simulationEnabled) locationState == TripLocationState.Sample || locationState == TripLocationState.Stale
        else userPosition != null && locationState in setOf(TripLocationState.Live, TripLocationState.LastKnown, TripLocationState.Estimated, TripLocationState.EstimatedStale, TripLocationState.Stale)
    val reference = trip.id.value.takeLast(3)
    val positionLabel = sampleStateLabel ?: when {
        !simulationEnabled && locationState == TripLocationState.LastKnown -> "Last known position"
        !simulationEnabled && (locationState == TripLocationState.Estimated || locationState == TripLocationState.EstimatedStale) -> "Estimated position"
        !simulationEnabled && locationState == TripLocationState.Stale && userPosition != null -> "Position paused"
        else -> null
    }

    var followPosition by rememberSaveable(trip.id) {
        mutableStateOf(false)
    }
    var retainedMapProgress by rememberSaveable(trip.id) {
        mutableStateOf(fraction)
    }

    LaunchedEffect(fraction, locationState, simulationEnabled) {
        if (locationState == TripLocationState.Sample || locationState == TripLocationState.Live || locationState == TripLocationState.Estimated || locationState == TripLocationState.EstimatedStale ||
            (!simulationEnabled && (locationState == TripLocationState.Stale || locationState == TripLocationState.LastKnown))) {
            retainedMapProgress = fraction
        }
    }

    val guidance = trip.guidanceAt(retainedMapProgress)
    val currentInstruction = instruction ?: ActiveMapInstruction(
        title = guidance.section,
        detail = guidance.currentAction,
        showLeftTurnIcon = guidance.nextIsLeftTurn,
        nextAction = guidance.nextAction
    )
    var directionsExpanded by rememberSaveable(trip.id.value) { mutableStateOf(false) }
    val compactInstruction = instruction?.detail ?: when {
        !simulationEnabled && userPosition == null -> when (locationState) {
            TripLocationState.PermissionDenied -> "Allow location to see directions"
            TripLocationState.NotTracking -> "Trip route"
            else -> "Finding your location"
        }
        else -> trip.compactGuidanceAt(retainedMapProgress, userPosition)
    }

    var confirmingFinish by rememberSaveable(trip.id) {
        mutableStateOf(false)
    }

    LaunchedEffect(completed, simulationEnabled) {
        if (completed || !simulationEnabled) confirmingFinish = false
    }

    var confirmingCancel by rememberSaveable(trip.id) {
        mutableStateOf(false)
    }

    // Completion can happen while confirmation is open.
    LaunchedEffect(completed) {
        if (completed) confirmingCancel = false
    }

    val liveMap = LocalTransitMap.current != null
    val density = LocalDensity.current
    var headerHeight by remember { mutableIntStateOf(0) }
    var panelHeight by remember { mutableIntStateOf(0) }
    var recenterRequest by remember(trip.id) { mutableIntStateOf(0) }
    // A continuous saved fraction: release leaves the panel exactly where it was dragged.
    var panelExpansion by rememberSaveable(trip.id.value) { mutableFloatStateOf(1f) }
    val panelScroll = rememberScrollState()

    BoxWithConstraints(modifier.fillMaxSize()) {
        val panelMaxHeight = maxHeight * ActiveMapPanelMaximumHeightFraction
        val panelMinHeight = ActiveMapPanelMinimumHeight.coerceAtMost(panelMaxHeight)
        val panelRangePx = with(density) { (panelMaxHeight - panelMinHeight).toPx() }
        val panelVisibleHeight = panelMinHeight + (panelMaxHeight - panelMinHeight) * panelExpansion
        val mapInteractive = actionsEnabled && !confirmingCancel && !confirmingFinish
        val resizePanel: (Float) -> Float = { delta ->
            if (mapInteractive && panelRangePx > 0f) {
                val previous = panelExpansion
                panelExpansion = (previous - delta / panelRangePx).coerceIn(0f, 1f)
                (previous - panelExpansion) * panelRangePx
            } else 0f
        }
        val latestResizePanel by rememberUpdatedState(resizePanel)
        val latestDragEnabled by rememberUpdatedState(mapInteractive)
        val latestPanelExpansion by rememberUpdatedState(panelExpansion)
        val panelNestedScroll = remember(panelScroll) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (!latestDragEnabled || source != NestedScrollSource.UserInput) return Offset.Zero
                    return if (available.y < 0f || !panelScroll.canScrollBackward) {
                        Offset(0f, latestResizePanel(available.y))
                    } else Offset.Zero
                }

                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                    if (latestDragEnabled && source == NestedScrollSource.UserInput) {
                        Offset(0f, latestResizePanel(available.y))
                    } else Offset.Zero

                override suspend fun onPreFling(available: Velocity): Velocity =
                    if (latestDragEnabled && latestPanelExpansion < 1f) Velocity(0f, available.y) else Velocity.Zero
            }
        }
        if (liveMap) {
            TransitMap(
                data = TransitMapData(
                    trip = trip,
                    progress = retainedMapProgress.takeIf { positionAvailable },
                    userPosition = userPosition.takeIf { !simulationEnabled && positionAvailable },
                    simulatePosition = simulationEnabled,
                    recenterRequest = recenterRequest,
                    followPosition = followPosition,
                    onManualCameraMove = { followPosition = false },
                    interactive = mapInteractive,
                    topInset = with(density) { headerHeight.toDp() },
                    bottomInset = with(density) { panelHeight.toDp() },
                    sharedTripFlow = true,
                    cameraInsetsReady = headerHeight > 0 && panelHeight > 0
                ),
                modifier = Modifier.fillMaxSize()
            ) {}
        } else {
            // Keep the bundled map's viewport fixed just like Android's shared map.
            // The panel and directions are overlays, never map-size constraints.
            MockTripMap(
                start = trip.origin.toSampleMapPoint(),
                destination = trip.destination.toSampleMapPoint(),
                legs = trip.toSampleMapLegs(),
                progress = retainedMapProgress,
                interactive = mapInteractive,
                matchPlannerBackground = true,
                followPosition = followPosition,
                onManualCameraMove = { followPosition = false },
                showControls = false,
                showPosition = positionAvailable,
                userPosition = userPosition?.toSampleMapPoint("Your location").takeIf { !simulationEnabled && positionAvailable },
                recenterRequest = recenterRequest,
                topInset = with(density) { headerHeight.toDp() },
                bottomInset = with(density) { panelHeight.toDp() },
                modifier = Modifier.fillMaxSize()
            )
        }

        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                Column(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth()
                        .onSizeChanged { headerHeight = it.height }
                        .tripPanelEntrance(liveMap, fromBottom = false)
                ) {
                    if (!mainOverview) {
                        Row(
                            Modifier
                                .padding(top = 16.dp)
                                .fillMaxWidth()
                                .background(TransitMain)
                                .clickable(
                                    enabled = actionsEnabled,
                                    role = Role.Button,
                                    onClickLabel = if (directionsExpanded) "Collapse directions" else "Expand directions",
                                    onClick = { directionsExpanded = !directionsExpanded }
                                )
                                .semantics { stateDescription = if (directionsExpanded) "Expanded" else "Collapsed" }
                                .padding(horizontal = ActiveMapHorizontalPadding, vertical = ActiveMapPanelPadding),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                Modifier.padding(top = 6.dp).size(ActiveMapLegIndicatorSize)
                                    .background(tripLegColor(guidance.legIndex), RoundedCornerShape(50))
                                    .border(1.dp, TransitWhite, RoundedCornerShape(50))
                            )
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    compactInstruction,
                                    color = TransitWhite,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                AnimatedVisibility(
                                    visible = directionsExpanded,
                                    enter = expandVertically(
                                        tween(ACTIVE_TRIP_DIRECTIONS_ANIMATION_DURATION_MILLIS, easing = FastOutSlowInEasing),
                                        expandFrom = Alignment.Top,
                                    ) + fadeIn(tween(ACTIVE_TRIP_DIRECTIONS_FADE_DURATION_MILLIS)),
                                    exit = shrinkVertically(
                                        tween(ACTIVE_TRIP_DIRECTIONS_ANIMATION_DURATION_MILLIS, easing = FastOutSlowInEasing),
                                        shrinkTowards = Alignment.Top,
                                    ) + fadeOut(tween(ACTIVE_TRIP_DIRECTIONS_FADE_DURATION_MILLIS)),
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            if (currentInstruction.showLeftTurnIcon) {
                                                Image(painterResource(Res.drawable.rnd_turn_left), contentDescription = null,
                                                    modifier = Modifier.size(ActiveMapInstructionIconSize))
                                            }
                                            Text(currentInstruction.title, color = TransitWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Text("Now: ${currentInstruction.detail}", color = TransitWhite, fontSize = 13.sp)
                                        currentInstruction.nextAction?.let { next ->
                                            Text("Next: $next", color = TransitWhite, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier.padding(16.dp),
                        color = TransitWhite,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            (if (simulationEnabled) "Sample trip · #$reference" else "Trip · #$reference") +
                                positionLabel?.let { " · $it" }.orEmpty(),
                            color = TransitMain,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(
                                horizontal = 10.dp,
                                vertical = 6.dp
                            )
                        )
                    }

                    if (!mainOverview) {
                        when (locationState) {
                            TripLocationState.Stale, TripLocationState.EstimatedStale -> TripWarning(
                                title = "Location is not updating",
                                detail = staleMessage
                                    ?: if (locationState == TripLocationState.EstimatedStale) "No reliable GPS reading for over 30 seconds. Position is estimated along your saved route."
                                    else "No reliable GPS reading for over 30 seconds. Route remains available."
                            )

                            TripLocationState.Missing -> TripWarning(
                                title = "Location unavailable",
                                detail = "Your saved route remains available."
                            )

                            TripLocationState.Sample, TripLocationState.Live, TripLocationState.Estimated -> {
                                if (staleMessage != null) {
                                    TripWarning(if (locationState == TripLocationState.Sample) "Location not updating" else "Check your route", staleMessage)
                                }
                            }

                            TripLocationState.PermissionDenied -> TripWarning("Location permission needed", "Tap recenter to allow location access.")
                            TripLocationState.NotTracking, TripLocationState.Waiting, TripLocationState.LastKnown -> Unit
                        }
                    }
                }

                Image(
                    painterResource(Res.drawable.rnd_recenter),
                    contentDescription = "Recenter on current position",
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 12.dp)
                        .size(ActiveMapRecenterSize)
                        .clickable(
                            enabled = actionsEnabled && !confirmingCancel && !confirmingFinish &&
                                (positionAvailable || !simulationEnabled),
                            role = Role.Button
                        ) {
                            followPosition = true
                            recenterRequest++
                            if (!simulationEnabled) onRequestLocation()
                        }
                )

                if (!liveMap) Text(
                    "© OpenStreetMap contributors · Sample map",
                    color = TransitComplementary,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .background(
                            TransitWhite,
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(panelVisibleHeight)
                    .clipToBounds()
                    .onSizeChanged { panelHeight = it.height }
                    .tripPanelEntrance(liveMap)
                    .background(TransitComplementary)
                    .nestedScroll(panelNestedScroll)
                    .draggable(
                        state = rememberDraggableState { latestResizePanel(it) },
                        orientation = Orientation.Vertical,
                        enabled = mapInteractive,
                    )
                    .semantics {
                        contentDescription = "Trip progress panel"
                        stateDescription = "${(panelExpansion * 100).roundToInt()} percent open"
                    }
                    .padding(start = ActiveMapPanelPadding, end = ActiveMapPanelPadding,
                        top = ActiveMapPanelTopPadding, bottom = ActiveMapPanelBottomPadding),
                verticalArrangement = Arrangement.spacedBy(ActiveMapPanelHeaderSpacing)
            ) {
                Box(
                    Modifier.fillMaxWidth().height(ActiveMapPanelHandleHeight)
                        .semantics { contentDescription = "Trip panel handle" },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.width(48.dp).height(4.dp).background(TransitWhite, RoundedCornerShape(2.dp)))
                }
                Column(
                    Modifier.weight(1f).verticalScroll(panelScroll, enabled = mapInteractive),
                    verticalArrangement = Arrangement.spacedBy(ActiveMapActionSpacing),
                ) {
                    if (mainOverview) {
                        Text(
                            "Your adventure is still running",
                            color = TransitWhite,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text("Current trip · #$reference", color = TransitWhite, fontSize = 14.sp)
                    if (locationState == TripLocationState.NotTracking) {
                        Text("Trip progress paused", color = TransitWhite, fontSize = 14.sp)
                    }
                    if (locationState == TripLocationState.Waiting) {
                        Text("Finding your location", color = TransitWhite, fontSize = 14.sp)
                    }
                    EstimatedTripProgress(trip, fraction)

                    if (operationMessage != null) {
                        Text(
                            operationMessage,
                            color = TransitWhite,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                        )
                    }

                    if (mainOverview) {
                        TripActionButton(
                            label = "Resume current trip",
                            enabled = actionsEnabled,
                            onClick = onResume
                        )
                        Text(
                            "Finish or cancel this trip before generating another.",
                            color = TransitWhite,
                            fontSize = 12.sp
                        )
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(ActiveMapActionSpacing)) {
                            if (completed || simulationEnabled) TripActionButton(
                                label = if (completed) "Review trip" else "Finish trip",
                                enabled = actionsEnabled,
                                onClick = {
                                    if (completed) onFinish()
                                    else { confirmingCancel = false; confirmingFinish = true }
                                },
                                modifier = Modifier.weight(1f)
                            )
                            TripActionButton(
                                label = "Cancel",
                                secondary = true,
                                enabled = actionsEnabled && !completed,
                                onClick = { confirmingFinish = false; confirmingCancel = true },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        TextButton(onClick = onShowInformation, enabled = actionsEnabled) {
                            Text("Trip information", color = TransitWhite)
                        }
                    }
                }
            }
        }
    }

    val alwaysSaveCancellation = LocalCloudTrips.current != null
    if (confirmingCancel && !completed) {
        Dialog(
            onDismissRequest = { confirmingCancel = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false
            )
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = ActiveMapDialogMaximumWidth)
                    .fillMaxWidth()
                    .background(TransitWhite)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Cancel this adventure?",
                    color = TransitComplementary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    if (alwaysSaveCancellation) "This ends trip #$reference and saves it to your History."
                    else "This ends trip #$reference. Do you want to save it " +
                        "to your History? Your planner choices stay saved either way.",
                    color = TransitComplementary,
                    fontSize = 16.sp
                )

                TripActionButton(
                    label = "Cancel and save to History",
                    enabled = actionsEnabled,
                    onClick = {
                        confirmingCancel = false
                        onConfirmCancel(true)
                    }
                )

                if (!alwaysSaveCancellation) TripActionButton(
                    label = "Cancel without saving",
                    enabled = actionsEnabled,
                    secondary = true,
                    onClick = {
                        confirmingCancel = false
                        onConfirmCancel(false)
                    }
                )

                TripActionButton(
                    label = "Keep travelling",
                    secondary = true,
                    onClick = { confirmingCancel = false }
                )
            }
        }
    }

    if (confirmingFinish && !completed && simulationEnabled) {
        Dialog(
            onDismissRequest = { confirmingFinish = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Column(
                Modifier
                    .widthIn(max = ActiveMapDialogMaximumWidth)
                    .fillMaxWidth()
                    .background(TransitWhite)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Finish this adventure?",
                    color = TransitComplementary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "This marks the sample trip as completed.",
                    color = TransitComplementary
                )
                TripActionButton(
                    "Yes, finish trip",
                    onClick = {
                        confirmingFinish = false
                        onFinish()
                    },
                    enabled = actionsEnabled
                )
                TripActionButton(
                    "Keep travelling",
                    onClick = { confirmingFinish = false },
                    secondary = true
                )
            }
        }
    }
}

/** Remaining estimates derived from the demo's progress fraction.
 * @param trip Trip duration, distance, and selected modes.
 * @param fraction Bounded progress from 0 to 1.
 */
@Composable
private fun EstimatedTripProgress(
    trip: Trip,
    fraction: Float
) {
    val remaining = 1f - fraction
    val minutes = ceil(trip.plannedMinutes * remaining).toInt()
    val distance = (trip.distanceKm * remaining * 10).roundToInt() / 10.0
    val percentage = (fraction * 100).roundToInt()

    Column {
        Text(
            if (fraction >= 1f) "Destination reached"
            else "$minutes min remaining",
            color = TransitWhite,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            "$distance km · " +
                trip.selectedTransports.joinToString(" + ") { it.label },
            color = TransitWhite,
            fontSize = 14.sp
        )

        Text(
            "Estimated progress",
            color = TransitWhite,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )

        BoxWithConstraints(
            Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .height(ActiveMapProgressHeight)
                .clip(RoundedCornerShape(ActiveMapProgressCornerRadius))
                .background(TransitWhite)
                .semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        fraction,
                        0f..1f
                    )
                }
        ) {
            if (fraction > 0f) {
                Box(
                    Modifier
                        .width(maxWidth * fraction)
                        .fillMaxHeight()
                        .background(
                            TransitMain,
                            RoundedCornerShape(24.dp)
                        )
                )
            }

            val fillWidth = maxWidth * fraction
            val labelWidth = ActiveMapPercentageWidth.coerceAtMost(maxWidth)
            val insideFill = fillWidth >= labelWidth + ActiveMapPercentageInset * 2
            val labelOffset = (if (insideFill) fillWidth - labelWidth - ActiveMapPercentageInset
                else fillWidth + ActiveMapPercentageInset)
                .coerceIn(0.dp, (maxWidth - labelWidth).coerceAtLeast(0.dp))

            // Plain percentage inside the blue fill; at low values use blue text on the white track.
            Text(
                "$percentage%",
                color = if (insideFill) TransitWhite else TransitMain,
                fontSize = 24.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = labelOffset)
                    .width(labelWidth)
            )
        }
    }
}

/** Announces a warning while retaining the surrounding trip content.
 * @param title Warning heading.
 * @param detail Explanation of the condition.
 * @param modifier Parent layout constraints.
 */
@Composable
internal fun TripWarning(
    title: String,
    detail: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = ActiveMapWarningMinimumHeight)
            .background(TransitOrange)
            .padding(
                start = 14.dp, end = 16.dp,
                top = 12.dp, bottom = 14.dp
            )
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Image(
            painterResource(Res.drawable.rnd_warning),
            contentDescription = null,
            modifier = Modifier
                .padding(top = 4.dp)
                .size(ActiveMapWarningIconSize)
        )

        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                title,
                color = TransitComplementary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                detail,
                color = TransitComplementary,
                fontSize = 14.sp
            )
        }
    }
}

/** Shared primary or secondary trip action.
 * @param label Visible action label.
 * @param onClick Requests the parent-owned operation.
 * @param modifier Parent layout constraints.
 * @param secondary Whether to use the outlined white style.
 * @param enabled Whether interaction is permitted.
 */
@Composable
internal fun TripActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    secondary: Boolean = false,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = ActiveMapButtonMinimumHeight),
        shape = RoundedCornerShape(24.dp),
        border = if (secondary) BorderStroke(1.dp, TransitMain) else null,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (secondary) TransitWhite else TransitMain,
            contentColor = if (secondary) TransitMain else TransitWhite
        ),
        contentPadding = PaddingValues(12.dp)
    ) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

/** Distinct routing-provider outcomes; not inferred from mock input validation. */
enum class RouteFailure {
    NoRoute,
    ProviderUnavailable
}

/** Failure UI for a future provider response or an explicit preview fixture.
 * @param failure Distinguishes an unavailable provider from a valid search with no route.
 * @param requestSummary Original request, retained for retry.
 * @param onRetry Repeats the request.
 * @param onEditSettings Returns to the saved planner draft.
 * @param modifier Placement over the generation map.
 */
@Composable
fun RouteFailurePanel(
    failure: RouteFailure,
    requestSummary: String,
    onRetry: () -> Unit,
    onEditSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val unavailable = failure == RouteFailure.ProviderUnavailable

    Column(
        modifier
            .fillMaxWidth()
            .background(TransitComplementary)
            .verticalScroll(rememberScrollState())
    ) {
        TripWarning(
            title = if (unavailable) {
                "Routing service unavailable"
            } else {
                "No trip fits these choices"
            },
            detail = if (unavailable) {
                "The service could not be reached. Your choices are saved."
            } else {
                "Try more time or another supported transport mode."
            }
        )

        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "Your request is unchanged",
                color = TransitWhite,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Text(requestSummary, color = TransitWhite)

            TripActionButton(
                label = if (unavailable) "Try again"
                else "Adjust time or modes",
                onClick = if (unavailable) onRetry else onEditSettings
            )

            TripActionButton(
                label = if (unavailable) "Edit trip settings"
                else "Try another search",
                secondary = true,
                onClick = if (unavailable) onEditSettings else onRetry
            )
        }
    }
}
