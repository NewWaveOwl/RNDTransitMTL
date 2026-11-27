package com.example.rnd_transit_mtl.View.current_trip

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.model.trip.TripJourneyKind
import com.example.rnd_transit_mtl.ui.global_ui_ellements.transitions.tripPanelEntrance
import com.example.rnd_transit_mtl.ui.theme.TransitComplementary
import com.example.rnd_transit_mtl.ui.theme.TransitWhite
import kotlin.math.roundToInt

// Layout settings — dp padding, spacing, and drag-handle sizing for the active trip sheet.
private val ActiveSheetHorizontalPadding = 16.dp
internal val ActiveSheetTopPadding = 4.dp
private val ActiveSheetBottomPadding = 12.dp
private val ActiveSheetHeaderSpacing = 4.dp
private val ActiveSheetActionSpacing = 12.dp
internal val ActiveSheetHandleHeight = 20.dp
private val ActiveSheetHandleBarWidth = 48.dp
private val ActiveSheetHandleBarHeight = 4.dp
internal val ActiveSheetMinimumHeight = ActiveSheetTopPadding + ActiveSheetHandleHeight

/**
 * Continuously draggable bottom sheet displaying active trip progress, status notes, and controls.
 *
 * @param trip Stored active trip record.
 * @param reference Short trip reference suffix.
 * @param fraction Bounded completion progress from 0 to 1.
 * @param completed Whether progress has reached 100%.
 * @param mainOverview Whether rendered in Main's resume-overview mode.
 * @param simulationEnabled Whether debug simulation mode is active.
 * @param locationState Current location tracking status.
 * @param operationMessage Optional status feedback from trip actions.
 * @param actionsEnabled Whether action buttons accept clicks.
 * @param mapInteractive Whether dragging and scrolling are currently enabled.
 * @param liveMap Whether the platform live map is active (governs panel entrance animation).
 * @param panelVisibleHeight Current height of the sheet clip window in dp.
 * @param panelMaxHeight Maximum expanded height of the sheet in dp.
 * @param panelExpansion Fractional sheet expansion in `0..1` for accessibility descriptions.
 * @param panelScroll Scroll state shared with nested-scroll drag coordination.
 * @param panelNestedScroll Nested scroll connection converting edge scrolls into sheet dragging.
 * @param onResizePanel Drag delta handler returning consumed vertical pixels.
 * @param onPanelHeightChanged Called with the measured pixel height of the visible sheet.
 * @param onResume Called when the user resumes the active trip from Main overview.
 * @param onPrimaryFinishAction Called when the user taps Review trip, I'm back, or Finish trip.
 * @param onRequestCancel Called when the user taps Cancel to open confirmation.
 * @param onShowInformation Called when the user opens the trip information dialog.
 * @param modifier Configuration applied to the sheet root box.
 */
@Composable
internal fun ActiveMapBottomSheet(
    trip: Trip,
    reference: String,
    fraction: Float,
    completed: Boolean,
    mainOverview: Boolean,
    simulationEnabled: Boolean,
    locationState: TripLocationState,
    operationMessage: String?,
    actionsEnabled: Boolean,
    mapInteractive: Boolean,
    liveMap: Boolean,
    panelVisibleHeight: Dp,
    panelMaxHeight: Dp,
    panelExpansion: Float,
    panelScroll: ScrollState,
    panelNestedScroll: NestedScrollConnection,
    onResizePanel: (Float) -> Float,
    onPanelHeightChanged: (Int) -> Unit,
    onResume: () -> Unit,
    onPrimaryFinishAction: () -> Unit,
    onRequestCancel: () -> Unit,
    onShowInformation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(panelVisibleHeight)
            .clipToBounds()
            .onSizeChanged { onPanelHeightChanged(it.height) }
            .tripPanelEntrance(liveMap)
            .background(TransitComplementary)
            .nestedScroll(panelNestedScroll)
            .draggable(
                state = rememberDraggableState { onResizePanel(it) },
                orientation = Orientation.Vertical,
                enabled = mapInteractive,
            )
            .semantics {
                contentDescription = "Trip progress panel"
                stateDescription = "${(panelExpansion * 100).roundToInt()} percent open"
            },
    ) {
        // Keep the full sheet below the viewport as it is pulled down, leaving only its handle.
        Column(
            Modifier
                .fillMaxWidth()
                .wrapContentHeight(Alignment.Top, unbounded = true)
                .height(panelMaxHeight)
                .padding(
                    start = ActiveSheetHorizontalPadding,
                    end = ActiveSheetHorizontalPadding,
                    top = ActiveSheetTopPadding,
                    bottom = ActiveSheetBottomPadding,
                ),
            verticalArrangement = Arrangement.spacedBy(ActiveSheetHeaderSpacing),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(ActiveSheetHandleHeight)
                    .semantics { contentDescription = "Trip panel handle" },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .width(ActiveSheetHandleBarWidth)
                        .height(ActiveSheetHandleBarHeight)
                        .background(TransitWhite, RoundedCornerShape(2.dp)),
                )
            }

            Column(
                Modifier.weight(1f).verticalScroll(panelScroll, enabled = mapInteractive),
                verticalArrangement = Arrangement.spacedBy(ActiveSheetActionSpacing),
            ) {
                if (mainOverview) {
                    Text(
                        "Your adventure is still running",
                        color = TransitWhite,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                val isReturn = trip.journey?.kind == TripJourneyKind.Return
                if (isReturn) {
                    Text(
                        "Returning to your start",
                        color = TransitWhite,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    "${if (isReturn) "Return trip" else "Current trip"} · #$reference",
                    color = TransitWhite,
                    fontSize = 14.sp,
                )
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
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }

                if (mainOverview) {
                    TripActionButton(
                        label = "Resume current trip",
                        enabled = actionsEnabled,
                        onClick = onResume,
                    )
                    Text(
                        "Finish or cancel this trip before generating another.",
                        color = TransitWhite,
                        fontSize = 12.sp,
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(ActiveSheetActionSpacing)) {
                        if (completed || simulationEnabled || isReturn) {
                            TripActionButton(
                                label = when {
                                    completed -> "Review trip"
                                    isReturn -> "I'm back at the start"
                                    else -> "Finish trip"
                                },
                                enabled = actionsEnabled,
                                onClick = onPrimaryFinishAction,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        TripActionButton(
                            label = "Cancel",
                            secondary = true,
                            enabled = actionsEnabled && !completed,
                            onClick = onRequestCancel,
                            modifier = Modifier.weight(1f),
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
