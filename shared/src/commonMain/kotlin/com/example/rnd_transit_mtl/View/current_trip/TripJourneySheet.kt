package com.example.rnd_transit_mtl.View.current_trip

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.data.assets.toSampleMapPoint
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.*
import com.example.rnd_transit_mtl.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// Layout settings — Figma's 412-wide screens, excluding the existing header and footer.
private val JourneySheetMaximumWidth = 620.dp
private val JourneyArrivalPanelHeight = 448.dp
private val JourneyCollapsedPanelHeight = 148.dp
private val JourneyMinimumMapHeight = 48.dp
private val JourneyHandleHeight = 32.dp
private val JourneyHandleWidth = 42.dp
private val JourneyContentPadding = 16.dp

// Animation settings — Figma's arrival cohort: 100 px entrance over the first 450 ms of 2 seconds.
private const val JOURNEY_SHEET_SNAP_MILLIS = 300
internal const val ARRIVAL_CELEBRATION_MILLIS = 2000
internal val ArrivalEaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)
private enum class JourneySheetPosition { Expanded, Collapsed }

/**
 * Keeps the real trip map behind the responsive teal sheet used by the round-trip designs.
 * @param trip Stored route to draw, never a screenshot or reversed outbound geometry.
 * @param enabled Whether gestures and handle controls accept input.
 * @param modifier Parent constraints between the existing header and footer.
 * @param completed Whether the whole route is travelled.
 * @param entranceProgress Shared arrival animation clock, from zero to one.
 * @param panelHeight Expanded height from the corresponding Figma screen.
 * @param collapsedContent Short summary displayed at the lower resting position.
 * @param overlay Optional decoration above the map, outside the sheet's input surface.
 * @param content Scrollable panel content.
 */
@Composable
internal fun TripJourneySheet(
    trip: Trip,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    completed: Boolean = true,
    entranceProgress: Float = 1f,
    panelHeight: androidx.compose.ui.unit.Dp = JourneyArrivalPanelHeight,
    collapsedContent: @Composable ColumnScope.() -> Unit,
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    BoxWithConstraints(modifier.fillMaxSize().clipToBounds()) {
        val width = maxWidth.coerceAtMost(JourneySheetMaximumWidth)
        val scale = (width.value / 412f).coerceIn(0.7f, 1.5f)
        val heightPx = with(density) { maxHeight.toPx() }
        val expanded = with(density) {
            (maxHeight - panelHeight * scale).coerceAtLeast(JourneyMinimumMapHeight.coerceAtMost(maxHeight * 0.25f)).toPx()
        }
        val collapsed = with(density) { (maxHeight - JourneyCollapsedPanelHeight * scale).toPx() }.coerceAtLeast(expanded)
        val anchors = remember(expanded, collapsed) {
            DraggableAnchors {
                JourneySheetPosition.Expanded at expanded
                JourneySheetPosition.Collapsed at collapsed
            }
        }
        val sheet = rememberSaveable(trip.id.value, saver = AnchoredDraggableState.Saver()) {
            AnchoredDraggableState(JourneySheetPosition.Expanded, anchors)
        }
        SideEffect { sheet.updateAnchors(anchors, sheet.targetValue) }
        val top = sheet.offset.takeIf { it.isFinite() } ?: expanded
        val showSummary = sheet.targetValue == JourneySheetPosition.Collapsed
        val entranceY = with(density) { (100.dp * scale).toPx() } *
            (1f - ArrivalEaseOut.transform((entranceProgress / 0.225f).coerceIn(0f, 1f)))
        val mapData = TransitMapData(trip = trip, progress = if (completed) 1f else null,
            simulatePosition = false, interactive = enabled, sharedTripFlow = true,
            bottomInset = with(density) { (heightPx - top).coerceAtLeast(0f).toDp() })
        TransitMap(mapData, Modifier.fillMaxSize()) {
            MockTripMap(start = trip.origin.toSampleMapPoint(), destination = trip.destination.toSampleMapPoint(),
                progress = if (completed) 1f else 0f, legs = trip.toSampleMapLegs(),
                modifier = Modifier.fillMaxWidth().height(with(density) { top.toDp() }),
                interactive = enabled, matchPlannerBackground = false,
                showControls = false, showPosition = false)
        }
        overlay()
        Column(Modifier.align(Alignment.TopCenter)
            .offset { IntOffset(0, (top + entranceY).roundToInt()) }
            .width(width)
            .height(with(density) { (heightPx - top).coerceAtLeast(0f).toDp() })
            .background(TransitComplementary)
            .anchoredDraggable(sheet, Orientation.Vertical, enabled = enabled)) {
            fun move(position: JourneySheetPosition) {
                if (enabled) scope.launch { sheet.animateTo(position, tween(JOURNEY_SHEET_SNAP_MILLIS)) }
            }
            Box(Modifier.fillMaxWidth().height(JourneyHandleHeight)
                .onKeyEvent {
                    if (!enabled || it.type != KeyEventType.KeyDown) false
                    else when (it.key) {
                        Key.DirectionUp, Key.MoveEnd -> { move(JourneySheetPosition.Expanded); true }
                        Key.DirectionDown, Key.MoveHome -> { move(JourneySheetPosition.Collapsed); true }
                        else -> false
                    }
                }
                .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Expand or collapse trip summary") {
                    move(if (showSummary) JourneySheetPosition.Expanded else JourneySheetPosition.Collapsed)
                }.semantics {
                    contentDescription = "Trip summary"
                    stateDescription = sheet.targetValue.name
                    customActions = listOf(
                        CustomAccessibilityAction("Expand trip summary") { if (enabled) move(JourneySheetPosition.Expanded); enabled },
                        CustomAccessibilityAction("Collapse trip summary") { if (enabled) move(JourneySheetPosition.Collapsed); enabled })
                }, contentAlignment = Alignment.TopCenter) {
                Box(Modifier.padding(top = 8.dp).size(JourneyHandleWidth, 4.dp)
                    .background(TransitWhite, RoundedCornerShape(2.dp)))
            }
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                .padding(start = JourneyContentPadding, end = JourneyContentPadding, bottom = JourneyContentPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (showSummary) collapsedContent() else content()
            }
        }
    }
}
