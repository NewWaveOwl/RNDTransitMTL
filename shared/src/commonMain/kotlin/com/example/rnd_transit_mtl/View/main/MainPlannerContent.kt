package com.example.rnd_transit_mtl.View.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.rnd_transit_mtl.ViewModels.main.MainPlannerViewModel
import com.example.rnd_transit_mtl.data.TripGenerationBounds
import com.example.rnd_transit_mtl.model.TransportType
import com.example.rnd_transit_mtl.ui.GOBox
import com.example.rnd_transit_mtl.ui.GO_TRIP_TRANSITION_MILLIS
import com.example.rnd_transit_mtl.ui.IntensityPanel
import com.example.rnd_transit_mtl.ui.plannerTripBounds
import com.example.rnd_transit_mtl.ui.validMapSourceSize
import com.example.rnd_transit_mtl.ui.theme.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import rnd_transit_mtl.shared.generated.resources.Res
import rnd_transit_mtl.shared.generated.resources.map_sample

/**
 * Displays the map and draggable planner sheet.
 *
 * @param minutes Current available minutes.
 * @param onMinutesChange Requests a time change.
 * @param transportTypes Loaded transport catalogue.
 * @param selectedTransportIds Enabled transport IDs.
 * @param selectedZoneIds Selected transit-zone IDs.
 * @param onToggleTransport Requests a transport toggle.
 * @param onToggleZone Requests a zone toggle.
 * @param intensity Current attraction intensity.
 * @param onIntensityChange Requests an intensity change.
 * @param validationMessage Current validation feedback.
 * @param actionLabel GO, Resume trip, or the completion action.
 * @param actionEnabled Whether the primary action is available.
 * @param inputsEnabled Whether planner inputs can currently change.
 * @param activeTripSummary Description of an unfinished trip.
 * @param pendingReviewSummary Description of a completed trip.
 * @param onPrimaryAction Invokes the existing trip coordinator.
 * @param onOpenDetailedSettings Opens the existing Settings destination.
 * @param modifier Layout supplied by the caller.
 * @param controlsVisible Whether planner controls should remain visible.
 * @param onMapViewportReady Reports the map's generation bounds.
 * @param onControlsHidden Runs after the planner exit animation completes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainPlannerContent(
    minutes: Int,
    onMinutesChange: (Int) -> Unit,
    transportTypes: List<TransportType>,
    selectedTransportIds: List<String>,
    selectedZoneIds: List<String>,
    onToggleTransport: (String) -> Unit,
    onToggleZone: (String) -> Unit,
    intensity: Float,
    onIntensityChange: (Float) -> Unit,
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
    onControlsHidden: () -> Unit = {}
) {
    val controlsState = remember {
        MutableTransitionState(controlsVisible)
    }
    val latestControlsHidden by rememberUpdatedState(onControlsHidden)

    SideEffect {
        controlsState.targetState = controlsVisible
    }

    LaunchedEffect(
        controlsVisible,
        controlsState.isIdle,
        controlsState.currentState
    ) {
        if (
            !controlsVisible &&
            controlsState.isIdle &&
            !controlsState.currentState
        ) {
            latestControlsHidden()
        }
    }

    val mapPainter = painterResource(Res.drawable.map_sample)
    val sourceSize = validMapSourceSize(mapPainter.intrinsicSize)
    var viewport by remember { mutableStateOf(Size.Zero) }
    val titleHeight = with(LocalDensity.current) { 128.dp.toPx() }
    val latestViewportReady by rememberUpdatedState(onMapViewportReady)

    LaunchedEffect(viewport, sourceSize, titleHeight) {
        if (viewport.width > 0f && viewport.height > 0f) {
            latestViewportReady(
                plannerTripBounds(viewport, sourceSize, titleHeight)
            )
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged {
                viewport = Size(
                    it.width.toFloat(),
                    it.height.toFloat()
                )
            }
    ) {
        val availableHeight = maxHeight
        val peekHeight = (availableHeight * 0.58f)
            .coerceAtMost(440.dp)
        val sheetState = rememberStandardBottomSheetState()
        val scaffoldState = rememberBottomSheetScaffoldState(
            bottomSheetState = sheetState
        )
        val scope = rememberCoroutineScope()

        Image(
            painter = mapPainter,
            contentDescription = "Bundled sample transit map",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        AnimatedVisibility(
            visibleState = controlsState,
            modifier = Modifier.fillMaxSize(),
            enter = EnterTransition.None,
            exit = slideOutVertically(
                animationSpec = tween(GO_TRIP_TRANSITION_MILLIS),
                targetOffsetY = { it }
            )
        ) {
            BottomSheetScaffold(
                modifier = Modifier.fillMaxSize(),
                scaffoldState = scaffoldState,
                sheetPeekHeight = peekHeight,
                sheetMaxWidth = 620.dp,
                sheetShape = RectangleShape,
                sheetContainerColor = Color.Transparent,
                sheetContentColor = TransitWhite,
                sheetTonalElevation = 0.dp,
                sheetShadowElevation = 0.dp,
                sheetDragHandle = null,
                sheetContent = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(availableHeight)
                    ) {
                        GOBox(
                            minutes = minutes,
                            onMinutesChange = onMinutesChange,
                            onGo = onPrimaryAction,
                            actionLabel = actionLabel,
                            actionEnabled = actionEnabled,
                            minutesEnabled = inputsEnabled,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = 24.dp,
                                    vertical = 12.dp
                                )
                        )

                        // A tap is an alternative to dragging the sheet.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .background(TransitComplementary)
                                .clickable(
                                    role = Role.Button,
                                    onClickLabel = "Expand or collapse preferences"
                                ) {
                                    scope.launch {
                                        if (
                                            sheetState.currentValue ==
                                            SheetValue.Expanded
                                        ) {
                                            sheetState.partialExpand()
                                        } else {
                                            sheetState.expand()
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                Modifier
                                    .size(width = 42.dp, height = 4.dp)
                                    .background(
                                        TransitWhite,
                                        RoundedCornerShape(2.dp)
                                    )
                            )
                        }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .background(TransitMain)
                        ) {
                            val summary =
                                activeTripSummary ?: pendingReviewSummary

                            if (
                                summary != null ||
                                validationMessage.isNotEmpty()
                            ) {
                                item {
                                    Column(
                                        Modifier
                                            .fillMaxWidth()
                                            .background(TransitWhite)
                                            .padding(16.dp)
                                    ) {
                                        summary?.let {
                                            Text(
                                                it,
                                                color = TransitComplementary
                                            )
                                        }

                                        if (validationMessage.isNotEmpty()) {
                                            Text(
                                                text = validationMessage,
                                                color = MaterialTheme
                                                    .colorScheme.error,
                                                modifier = Modifier.semantics {
                                                    liveRegion =
                                                        LiveRegionMode.Polite
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            item {
                                Column(
                                    Modifier.padding(
                                        horizontal = 24.dp,
                                        vertical = 16.dp
                                    ),
                                    verticalArrangement =
                                        Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        "Types of transport",
                                        style = MaterialTheme.typography
                                            .titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = TransitWhite
                                    )

                                    val displayOrder = listOf(
                                        "walk", "bike", "bus",
                                        "metro", "train", "rem"
                                    )
                                    val orderedTypes = transportTypes.sortedBy {
                                        displayOrder.indexOf(it.id)
                                            .takeIf { index -> index >= 0 }
                                            ?: Int.MAX_VALUE
                                    }

                                    orderedTypes.chunked(3).forEach { row ->
                                        Row(
                                            horizontalArrangement =
                                                Arrangement.spacedBy(8.dp)
                                        ) {
                                            row.forEach { transport ->
                                                PlannerChoice(
                                                    label = if (
                                                        transport.id == "walk"
                                                    ) {
                                                        "Walk"
                                                    } else {
                                                        transport.label
                                                    },
                                                    selected =
                                                        transport.id in
                                                            selectedTransportIds,
                                                    enabled = inputsEnabled,
                                                    onClick = {
                                                        onToggleTransport(
                                                            transport.id
                                                        )
                                                    },
                                                    modifier =
                                                        Modifier.weight(1f)
                                                )
                                            }

                                            repeat(3 - row.size) {
                                                Spacer(Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }

                            val hasTransit = transportTypes.any {
                                it.usesRoutes &&
                                    it.id in selectedTransportIds
                            }

                            if (hasTransit) {
                                item {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(TransitHighlight)
                                            .padding(24.dp),
                                        verticalArrangement =
                                            Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            "Transit zones",
                                            color = TransitComplementary,
                                            style = MaterialTheme.typography
                                                .headlineSmall,
                                            fontWeight = FontWeight.Bold
                                        )

                                        Text(
                                            "Choose the zones to include. Sample routes record these choices; geographic filtering is not yet available.",
                                            color = TransitComplementary
                                        )

                                        MainPlannerViewModel.zoneIds
                                            .chunked(3)
                                            .forEach { row ->
                                                Row(
                                                    horizontalArrangement =
                                                        Arrangement.spacedBy(
                                                            8.dp
                                                        )
                                                ) {
                                                    row.forEach { zone ->
                                                        PlannerChoice(
                                                            label = "ZONE $zone",
                                                            selected =
                                                                zone in
                                                                    selectedZoneIds,
                                                            enabled =
                                                                inputsEnabled,
                                                            onClick = {
                                                                onToggleZone(
                                                                    zone
                                                                )
                                                            },
                                                            modifier =
                                                                Modifier.weight(
                                                                    1f
                                                                )
                                                        )
                                                    }

                                                    repeat(3 - row.size) {
                                                        Spacer(
                                                            Modifier.weight(1f)
                                                        )
                                                    }
                                                }
                                            }
                                    }
                                }
                            }

                            item {
                                IntensityPanel(
                                    intensity = intensity,
                                    onIntensityChange = {
                                        if (inputsEnabled) {
                                            onIntensityChange(it)
                                        }
                                    },
                                    validationMessage = "",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(140.dp)
                                )
                            }

                            item {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(TransitComplementary)
                                        .padding(24.dp)
                                ) {
                                    Text(
                                        "More preferences",
                                        color = TransitWhite,
                                        style = MaterialTheme.typography
                                            .titleLarge
                                    )

                                    Text(
                                        "Detailed transport options belong in Settings.",
                                        color = TransitWhite
                                    )

                                    TextButton(
                                        onClick = onOpenDetailedSettings,
                                        enabled = inputsEnabled
                                    ) {
                                        Text(
                                            "Open settings",
                                            color = TransitWhite
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                containerColor = Color.Transparent
            ) {
                // Keep the map stationary beneath the moving sheet.
                Box(Modifier.fillMaxSize()) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp),
                        color = TransitWhite,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "© OpenStreetMap contributors · Sample map",
                            color = TransitComplementary,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(6.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Displays an accessible transport or zone selection.
 *
 * @param label Visible choice label.
 * @param selected Whether the choice is enabled in the draft.
 * @param enabled Whether interaction is currently allowed.
 * @param onClick Requests a selection change.
 * @param modifier Layout supplied by the containing row.
 */
@Composable
private fun PlannerChoice(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .heightIn(min = 48.dp)
            .toggleable(
                value = selected,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = { onClick() }
            ),
        color = if (selected) TransitSelected else TransitWhite,
        contentColor =
            if (selected) TransitComplementary else TransitMain,
        shape = RoundedCornerShape(14.dp)
    ) {
        Box(
            modifier = Modifier.padding(
                horizontal = 6.dp,
                vertical = 12.dp
            ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
