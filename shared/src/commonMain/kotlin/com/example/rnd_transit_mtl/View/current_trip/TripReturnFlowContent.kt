package com.example.rnd_transit_mtl.View.current_trip

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rnd_transit_mtl.View.main.TripLabelValueField
import com.example.rnd_transit_mtl.ViewModels.current_trip.*
import com.example.rnd_transit_mtl.model.ids.TransportId
import com.example.rnd_transit_mtl.model.trip.Trip
import org.jetbrains.compose.resources.painterResource
import rnd_transit_mtl.shared.generated.resources.*
import com.example.rnd_transit_mtl.ui.theme.*

// Layout settings — Figma's return search, route-ready, unavailable and back-at-start panels.
private val ReturnSearchingPanelHeight = 366.dp
private val ReturnReadyPanelHeight = 356.dp
private val ReturnUnavailablePanelHeight = 302.dp
private val ReturnFinishedPanelHeight = 192.dp
private val ReturnModeIconSize = 28.dp
private val ReturnOptionHeight = 52.dp
private val ReturnProgressHeight = 64.dp
private val ReturnOptionCornerRadius = 24.dp
private const val RETURN_SEARCH_SWEEP_MILLIS = 1800

/**
 * Renders the Figma return flow from state without generating or storing routes during composition.
 * @param outbound Saved completed trip; remains available during every return state.
 * @param state Current cancellable request/proposal.
 * @param enabled Whether this destination accepts input.
 * @param onFind Requests fresh routing with the chosen comparison policy.
 * @param onCancel Discards only the temporary request or proposal.
 * @param onStart Accepts the independently routed proposal.
 * @param onReview Reviews the saved outbound record.
 * @param onSkip Skips review through the existing checked action.
 * @param onPlanAnother Returns to planning.
 * @param modifier Parent layout constraints.
 * @param syncPending Whether the outbound record is still awaiting cloud acknowledgement.
 * @param errorMessage Additional coordinator feedback.
 */
@Composable
internal fun TripReturnFlowContent(
    outbound: Trip, state: ReturnTripState, enabled: Boolean,
    onFind: (ReturnTransportOption) -> Unit, onCancel: () -> Unit, onStart: () -> Unit,
    onReview: () -> Unit, onSkip: () -> Unit, onPlanAnother: () -> Unit,
    modifier: Modifier = Modifier, syncPending: Boolean = false, errorMessage: String? = null
) {
    val page = modifier.onPreviewKeyEvent {
        if (enabled && state.phase != ReturnPhase.Arrival && it.type == KeyEventType.KeyDown && it.key == Key.Escape) {
            onCancel(); true
        } else false
    }
    if (state.phase == ReturnPhase.Arrival) {
        TripCompletionContent(outbound, onReview, onSkip, onPlanAnother, page, enabled, errorMessage,
            syncPending, celebrate = !state.hasRequested, returnControls = {
                Text("Return to starting point · Trip #${outbound.id.value.takeLast(3)}", color = TransitWhite, fontSize = 14.sp)
                ReturnOptionControl(outbound, state.option, enabled, onFind)
            })
        return
    }
    if (state.phase == ReturnPhase.AtStart) {
        BackAtStartContent(outbound, onReview, onPlanAnother, enabled, page)
        return
    }
    val proposal = state.proposal
    val finding = state.phase == ReturnPhase.Finding
    val ready = state.phase == ReturnPhase.Ready && proposal != null
    val panelHeight = when { finding -> ReturnSearchingPanelHeight; ready -> ReturnReadyPanelHeight; else -> ReturnUnavailablePanelHeight }
    val title = when {
        finding && state.option == ReturnTransportOption.Fastest -> "Finding the fastest way back"
        finding -> "Finding your way back"
        ready && state.option == ReturnTransportOption.Fastest -> "Your fastest way back"
        ready -> "Back with the same transport"
        else -> "No return route found"
    }
    TripJourneySheet(proposal ?: outbound, enabled, page, completed = !ready, panelHeight = panelHeight,
        collapsedContent = {
            JourneyHeading(title)
            Text("Trip #${outbound.id.value.takeLast(3)} is saved. Expand for return options.", color = TransitWhite, fontSize = 12.sp)
        }) {
        JourneyHeading(title)
        when {
            finding -> {
                Text(if (state.option == ReturnTransportOption.Fastest) "Comparing available routes and transport combinations."
                    else "Using the transport selected for your outbound trip.", color = TransitWhite, fontSize = 14.sp)
                val modes = if (state.option == ReturnTransportOption.Fastest) ReturnTransportCatalogue.map { it.id }
                    else outbound.requestedTransportIds ?: outbound.legs.map { it.transportId }.distinct()
                ReturnModeIcons(modes)
                Text(modes.joinToString(" · ") { id -> ReturnTransportCatalogue.firstOrNull { it.id == id }?.label ?: id.value },
                    color = TransitWhite, fontSize = 12.sp)
                ReturnSearchProgress()
                Text("${state.startLabel ?: outbound.destination.label} → ${outbound.origin.label}", color = TransitWhite, fontSize = 12.sp)
                JourneyButton("Cancel return", onCancel, enabled, secondary = true)
            }
            ready -> {
                val route = requireNotNull(proposal)
                Text(if (state.option == ReturnTransportOption.Fastest) "Available transport modes compared"
                    else "Only your outbound modes", color = TransitWhite, fontSize = 14.sp)
                Surface(color = TransitWhite, shape = RoundedCornerShape(14.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(painterResource(Res.drawable.rnd_mode_walk), null, Modifier.size(ReturnModeIconSize), tint = TransitMain)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Return to your starting point", color = TransitComplementary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("${journeyMinutes(route.routedDurationSeconds ?: route.legs.sumOf { it.durationSeconds })} · ${journeyDistance(route.distanceKm)} · " +
                                route.legs.map { it.transportId }.distinct().joinToString(" + ") { id ->
                                    route.selectedTransports.first { it.id == id }.label }, color = TransitComplementary, fontSize = 13.sp)
                        }
                    }
                }
                Text("Trip #${outbound.id.value.takeLast(3)} is saved: ${journeyDistance(outbound.distanceKm)}. Review it after you return.",
                    color = TransitWhite, fontSize = 12.sp)
                Text("Destination: ${outbound.origin.label}", color = TransitWhite, fontSize = 12.sp)
                if (!errorMessage.isNullOrBlank()) Text(errorMessage, color = TransitWhite, fontSize = 12.sp)
                JourneyButton("Start return trip", onStart, enabled)
                JourneyButton("Change return option", onCancel, enabled, secondary = true)
            }
            else -> {
                Text(state.message ?: "Your trip and starting point are saved. Try again.", color = TransitWhite, fontSize = 14.sp,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                Text("Trip #${outbound.id.value.takeLast(3)} · ${journeyDistance(outbound.distanceKm)} saved",
                    color = TransitWhite, fontSize = 12.sp)
                JourneyButton(if (state.option == ReturnTransportOption.Fastest) "Retry return route" else "Try fastest way back",
                    { onFind(ReturnTransportOption.Fastest) }, enabled)
                JourneyButton("Change return option", onCancel, enabled, secondary = true)
            }
        }
    }
}

/** Figma's split primary action and accessible two-choice menu. */
@Composable
private fun ReturnOptionControl(outbound: Trip, option: ReturnTransportOption, enabled: Boolean,
    onFind: (ReturnTransportOption) -> Unit) {
    var expanded by rememberSaveable(outbound.id.value) { mutableStateOf(false) }
    Box {
        Row(Modifier.fillMaxWidth().heightIn(min = ReturnOptionHeight)
            .clip(RoundedCornerShape(ReturnOptionCornerRadius)).background(TransitMain)) {
            Box(Modifier.weight(1f).heightIn(min = ReturnOptionHeight)
                .clickable(enabled, role = Role.Button) { onFind(option) }, contentAlignment = Alignment.Center) {
                Text(if (option == ReturnTransportOption.Fastest) "Fastest way back" else "Same transport",
                    color = TransitWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Box(Modifier.size(ReturnOptionHeight).clickable(enabled, role = Role.Button,
                onClickLabel = "More return options") { expanded = true }.semantics { contentDescription = "More return options" },
                contentAlignment = Alignment.Center) {
                Image(painterResource(Res.drawable.rnd_expand_more), null, Modifier.size(24.dp))
            }
        }
        DropdownMenu(expanded, { expanded = false }, containerColor = TransitWhite, shape = RoundedCornerShape(12.dp)) {
            DropdownMenuItem(text = { Column {
                Text("Fastest way back", color = TransitComplementary, fontWeight = FontWeight.Bold)
                Text("Compare all available transport modes", color = TransitComplementary, fontSize = 12.sp)
            } }, onClick = { expanded = false; onFind(ReturnTransportOption.Fastest) }, enabled = enabled)
            DropdownMenuItem(text = { Column {
                Text("Same transport", color = TransitComplementary, fontWeight = FontWeight.Bold)
                Text("Keep your outbound selection", color = TransitComplementary, fontSize = 12.sp)
            } }, onClick = { expanded = false; onFind(ReturnTransportOption.SameTransport) }, enabled = enabled)
        }
    }
}

/** Figma completion at the stored start; review and skip are deliberate user actions. */
@Composable
internal fun BackAtStartContent(trip: Trip, onReview: () -> Unit, onSkip: () -> Unit,
    enabled: Boolean, modifier: Modifier = Modifier, errorMessage: String? = null) {
    TripJourneySheet(trip, enabled, modifier, panelHeight = ReturnFinishedPanelHeight,
        collapsedContent = { JourneyHeading("You're back at the start!") }, overlay = {
            TripLabelValueField("Starting point", "You're back · Trip #${(trip.journey?.outboundTripId ?: trip.id).value.takeLast(3)} finished",
                showLocationIcon = true, modifier = Modifier.align(Alignment.TopCenter).padding(16.dp).widthIn(max = 620.dp))
        }) {
        JourneyHeading("You're back at the start!")
        Text("Trip finished and saved. Would you like to review your adventure?", color = TransitWhite, fontSize = 14.sp)
        if (!errorMessage.isNullOrBlank()) Text(errorMessage, color = TransitWhite, fontSize = 12.sp,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            JourneyButton("Trip Review", onReview, enabled, modifier = Modifier.weight(1f))
            JourneyButton("Skip", onSkip, enabled, secondary = true, modifier = Modifier.width(116.dp))
        }
    }
}

/** Shared section heading announces state changes without moving keyboard focus. */
@Composable
private fun JourneyHeading(value: String) {
    Text(value, color = TransitWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite })
}

/** Exact Figma mode glyphs for the transports the backend is actually comparing. */
@Composable
private fun ReturnModeIcons(modes: List<TransportId>) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        modes.forEach { mode ->
            val resource = when (mode.value) {
                "walk" -> Res.drawable.rnd_mode_walk
                "bike" -> Res.drawable.rnd_mode_bike
                "bus" -> Res.drawable.rnd_mode_bus
                "metro" -> Res.drawable.rnd_mode_metro
                else -> null
            }
            resource?.let { Image(painterResource(it), null, Modifier.size(ReturnModeIconSize)) }
        }
    }
}

/** Indeterminate gradient sweep; no fabricated percentage is announced during routing. */
@Composable
private fun ReturnSearchProgress() {
    val transition = rememberInfiniteTransition()
    val sweep by transition.animateFloat(-0.38f, 1f,
        infiniteRepeatable(tween(RETURN_SEARCH_SWEEP_MILLIS, easing = LinearEasing)))
    BoxWithConstraints(Modifier.fillMaxWidth().height(ReturnProgressHeight).clip(RoundedCornerShape(18.dp))
        .background(TransitWhite).semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }) {
        Box(Modifier.offset(x = maxWidth * sweep).width(maxWidth * 0.38f).fillMaxHeight()
            .background(Brush.horizontalGradient(listOf(TransitMain, TransitSelected)), RoundedCornerShape(18.dp)))
    }
}
