package com.example.rnd_transit_mtl.View.current_trip

import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rnd_transit_mtl.data.assets.toSampleMapPoint
import com.example.rnd_transit_mtl.data.trips.LocalCloudTrips
import com.example.rnd_transit_mtl.model.geo.GeoPoint
import com.example.rnd_transit_mtl.model.geo.uncertaintyRing
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.model.trip.TripJourneyKind
import com.example.rnd_transit_mtl.model.trip.compactGuidanceAt
import com.example.rnd_transit_mtl.model.trip.explorationWaypoints
import com.example.rnd_transit_mtl.model.trip.guidanceAt
import com.example.rnd_transit_mtl.ui.global_ui_ellements.location.LocationAccuracyNotice
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.LocalTransitMap
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.MockTripMap
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.TransitMap
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.TransitMapData
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.rememberAnimatedMapPosition
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.toSampleMapLegs
import com.example.rnd_transit_mtl.ui.global_ui_ellements.transitions.tripPanelEntrance
import com.example.rnd_transit_mtl.ui.theme.TransitComplementary
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitWhite
import org.jetbrains.compose.resources.painterResource
import rnd_transit_mtl.shared.generated.resources.Res
import rnd_transit_mtl.shared.generated.resources.rnd_recenter

// Layout settings — fraction and dp dimensions for the active map viewport overlays.
// Sheet and directions-banner settings live in ActiveMapBottomSheet.kt and ActiveMapDirectionsBanner.kt.
private const val ActiveMapPanelMaximumHeightFraction = 0.56f
private val ActiveMapRecenterSize = 48.dp
private val ActiveMapBadgePadding = 16.dp
private val ActiveMapBadgeCornerRadius = 16.dp

/**
 * Optional presentation override for provider or preview instructions.
 * With no override, the panel derives guidance from the saved trip legs.
 *
 * @property title Routing instruction heading.
 * @property detail Routing instruction details.
 * @property showLeftTurnIcon Whether to use the exported instruction icon.
 * @property nextAction Upcoming manoeuvre or section transition, when supplied.
 */
data class ActiveMapInstruction(
    val title: String,
    val detail: String,
    val showLeftTurnIcon: Boolean = false,
    val nextAction: String? = null,
)

/** Explicit location availability; NotTracking is a paused route, not a GPS error. */
enum class TripLocationState {
    Sample,
    Stale,
    Missing,
    NotTracking,
    Live,
    LastKnown,
    LowAccuracy,
    MatchedLowAccuracy,
    Waiting,
    PermissionDenied,
}

/**
 * Shared active-trip/overview map with explicit completion and confirmed cancellation.
 *
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
 * @param userUncertaintyMeters Optional uncertainty radius in metres around [userPosition].
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
    userUncertaintyMeters: Double? = null,
) {
    val fraction = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f
    val completed = fraction >= 1f
    val renderedUserPosition = rememberAnimatedMapPosition(userPosition.takeUnless { simulationEnabled })
    val positionAvailable = if (simulationEnabled) {
        locationState == TripLocationState.Sample || locationState == TripLocationState.Stale
    } else {
        userPosition != null
    }
    val reference = trip.id.value.takeLast(3)
    val positionLabel = resolvePositionLabel(sampleStateLabel, simulationEnabled, locationState, userPosition)

    var followPosition by rememberSaveable(trip.id) { mutableStateOf(false) }
    var retainedMapProgress by rememberSaveable(trip.id) { mutableFloatStateOf(fraction) }

    LaunchedEffect(fraction, locationState, simulationEnabled) {
        if (locationState == TripLocationState.Sample || !simulationEnabled) {
            retainedMapProgress = fraction
        }
    }

    val guidance = trip.guidanceAt(retainedMapProgress)
    val currentInstruction = instruction ?: ActiveMapInstruction(
        title = guidance.section,
        detail = guidance.currentAction,
        showLeftTurnIcon = guidance.nextIsLeftTurn,
        nextAction = guidance.nextAction,
    )
    var directionsExpanded by rememberSaveable(trip.id.value) { mutableStateOf(false) }
    val compactInstruction = resolveCompactInstruction(
        instruction = instruction,
        trip = trip,
        retainedMapProgress = retainedMapProgress,
        simulationEnabled = simulationEnabled,
        locationState = locationState,
        userPosition = userPosition,
    )

    var confirmingFinish by rememberSaveable(trip.id) { mutableStateOf(false) }
    LaunchedEffect(completed, simulationEnabled) {
        if (completed || !simulationEnabled && trip.journey?.kind != TripJourneyKind.Return) {
            confirmingFinish = false
        }
    }

    var confirmingCancel by rememberSaveable(trip.id) { mutableStateOf(false) }
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
        val panelMinHeight = ActiveSheetMinimumHeight.coerceAtMost(panelMaxHeight)
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
        val panelNestedScroll = rememberSheetNestedScrollConnection(
            panelScroll = panelScroll,
            dragEnabled = mapInteractive,
            panelExpansion = panelExpansion,
            resizePanel = resizePanel,
        )

        ActiveMapSurface(
            liveMap = liveMap,
            trip = trip,
            retainedMapProgress = retainedMapProgress,
            positionAvailable = positionAvailable,
            renderedUserPosition = renderedUserPosition,
            userUncertaintyMeters = userUncertaintyMeters,
            simulationEnabled = simulationEnabled,
            recenterRequest = recenterRequest,
            followPosition = followPosition,
            mapInteractive = mapInteractive,
            topInset = with(density) { headerHeight.toDp() },
            bottomInset = with(density) { panelHeight.toDp() },
            onManualCameraMove = { followPosition = false },
        )

        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                Column(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .onSizeChanged { headerHeight = it.height }
                        .tripPanelEntrance(liveMap, fromBottom = false),
                ) {
                    if (!mainOverview) {
                        ActiveMapDirectionsBanner(
                            legIndex = guidance.legIndex,
                            compactInstruction = compactInstruction,
                            currentInstruction = currentInstruction,
                            expanded = directionsExpanded,
                            actionsEnabled = actionsEnabled,
                            onToggleExpanded = { directionsExpanded = !directionsExpanded },
                        )
                    }

                    Surface(
                        modifier = Modifier.padding(ActiveMapBadgePadding),
                        color = TransitWhite,
                        shape = RoundedCornerShape(ActiveMapBadgeCornerRadius),
                    ) {
                        Text(
                            (if (simulationEnabled) "Sample trip · #$reference" else "Trip · #$reference") +
                                positionLabel?.let { " · $it" }.orEmpty(),
                            color = TransitMain,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }

                    if (!simulationEnabled) {
                        LocationAccuracyNotice(
                            modifier = Modifier.padding(horizontal = ActiveMapBadgePadding),
                            enabled = actionsEnabled,
                        )
                    }
                    if (!mainOverview) {
                        ActiveMapLocationWarning(locationState = locationState, staleMessage = staleMessage)
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
                            enabled = mapInteractive && (positionAvailable || !simulationEnabled),
                            role = Role.Button,
                        ) {
                            followPosition = true
                            recenterRequest++
                            if (!simulationEnabled) onRequestLocation()
                        },
                )

                if (!liveMap) {
                    Text(
                        "© OpenStreetMap contributors · Sample map",
                        color = TransitComplementary,
                        fontSize = 10.sp,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                            .background(TransitWhite, RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
            }

            ActiveMapBottomSheet(
                trip = trip,
                reference = reference,
                fraction = fraction,
                completed = completed,
                mainOverview = mainOverview,
                simulationEnabled = simulationEnabled,
                locationState = locationState,
                operationMessage = operationMessage,
                actionsEnabled = actionsEnabled,
                mapInteractive = mapInteractive,
                liveMap = liveMap,
                panelVisibleHeight = panelVisibleHeight,
                panelMaxHeight = panelMaxHeight,
                panelExpansion = panelExpansion,
                panelScroll = panelScroll,
                panelNestedScroll = panelNestedScroll,
                onResizePanel = { latestResizePanel(it) },
                onPanelHeightChanged = { panelHeight = it },
                onResume = onResume,
                onPrimaryFinishAction = {
                    if (completed) onFinish()
                    else {
                        confirmingCancel = false
                        confirmingFinish = true
                    }
                },
                onRequestCancel = {
                    confirmingFinish = false
                    confirmingCancel = true
                },
                onShowInformation = onShowInformation,
            )
        }
    }

    val alwaysSaveCancellation = LocalCloudTrips.current != null
    if (confirmingCancel && !completed) {
        CancelTripConfirmationDialog(
            reference = reference,
            alwaysSaveCancellation = alwaysSaveCancellation,
            actionsEnabled = actionsEnabled,
            onConfirmCancel = { keepInHistory ->
                confirmingCancel = false
                onConfirmCancel(keepInHistory)
            },
            onDismiss = { confirmingCancel = false },
        )
    }

    if (confirmingFinish && !completed && (simulationEnabled || trip.journey?.kind == TripJourneyKind.Return)) {
        FinishTripConfirmationDialog(
            isReturnTrip = trip.journey?.kind == TripJourneyKind.Return,
            actionsEnabled = actionsEnabled,
            onConfirmFinish = {
                confirmingFinish = false
                onFinish()
            },
            onDismiss = { confirmingFinish = false },
        )
    }
}

/** Resolves the status suffix shown on the top-left trip badge for live location states. */
private fun resolvePositionLabel(
    sampleStateLabel: String?,
    simulationEnabled: Boolean,
    locationState: TripLocationState,
    userPosition: GeoPoint?,
): String? = sampleStateLabel ?: when {
    !simulationEnabled && locationState == TripLocationState.LastKnown -> "Last known position"
    !simulationEnabled && locationState == TripLocationState.LowAccuracy -> "Low GPS accuracy"
    !simulationEnabled && locationState == TripLocationState.MatchedLowAccuracy -> "Estimated position · Low GPS accuracy"
    !simulationEnabled && locationState == TripLocationState.PermissionDenied && userPosition != null -> "Last known position"
    !simulationEnabled && locationState == TripLocationState.Stale && userPosition != null -> "Position paused"
    else -> null
}

/** Resolves the compact single-line instruction displayed in the directions banner header. */
private fun resolveCompactInstruction(
    instruction: ActiveMapInstruction?,
    trip: Trip,
    retainedMapProgress: Float,
    simulationEnabled: Boolean,
    locationState: TripLocationState,
    userPosition: GeoPoint?,
): String = instruction?.detail ?: when {
    !simulationEnabled && userPosition == null -> when (locationState) {
        TripLocationState.PermissionDenied -> "Allow location to see directions"
        TripLocationState.NotTracking -> "Trip route"
        else -> "Finding your location"
    }
    else -> trip.compactGuidanceAt(retainedMapProgress, userPosition)
}

/** Coordinates vertical scroll boundaries inside the active sheet with sheet drag resizing. */
@Composable
private fun rememberSheetNestedScrollConnection(
    panelScroll: ScrollState,
    dragEnabled: Boolean,
    panelExpansion: Float,
    resizePanel: (Float) -> Float,
): NestedScrollConnection {
    val latestResizePanel by rememberUpdatedState(resizePanel)
    val latestDragEnabled by rememberUpdatedState(dragEnabled)
    val latestPanelExpansion by rememberUpdatedState(panelExpansion)
    return remember(panelScroll) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!latestDragEnabled || source != NestedScrollSource.UserInput) return Offset.Zero
                return if (available.y < 0f || !panelScroll.canScrollBackward) {
                    Offset(0f, latestResizePanel(available.y))
                } else Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset = if (latestDragEnabled && source == NestedScrollSource.UserInput) {
                Offset(0f, latestResizePanel(available.y))
            } else Offset.Zero

            override suspend fun onPreFling(available: Velocity): Velocity =
                if (latestDragEnabled && latestPanelExpansion < 1f) Velocity(0f, available.y) else Velocity.Zero
        }
    }
}

/** Renders either the live platform map or the bundled fallback map for an active trip. */
@Composable
private fun ActiveMapSurface(
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
    topInset: androidx.compose.ui.unit.Dp,
    bottomInset: androidx.compose.ui.unit.Dp,
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

/** Displays location-status warnings when tracking is stale, missing, or missing permissions. */
@Composable
private fun ActiveMapLocationWarning(
    locationState: TripLocationState,
    staleMessage: String?,
) {
    when (locationState) {
        TripLocationState.Stale -> TripWarning(
            title = "Location is not updating",
            detail = staleMessage ?: "No fresh location reading for over 30 seconds. Route remains available.",
        )

        TripLocationState.Missing -> TripWarning(
            title = "Location unavailable",
            detail = "Your saved route remains available.",
        )

        TripLocationState.Sample, TripLocationState.Live -> {
            if (staleMessage != null) {
                TripWarning(
                    title = if (locationState == TripLocationState.Sample) "Location not updating" else "Check your route",
                    detail = staleMessage,
                )
            }
        }

        TripLocationState.PermissionDenied -> TripWarning(
            title = "Location permission needed",
            detail = "Tap recenter to allow location access.",
        )

        TripLocationState.NotTracking,
        TripLocationState.Waiting,
        TripLocationState.LastKnown,
        TripLocationState.LowAccuracy,
        TripLocationState.MatchedLowAccuracy,
        -> Unit
    }
}
