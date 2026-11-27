package com.example.rnd_transit_mtl.View.current_trip

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rnd_transit_mtl.ViewModels.current_trip.ReturnPhase
import com.example.rnd_transit_mtl.ViewModels.current_trip.ReturnTransportCatalogue
import com.example.rnd_transit_mtl.ViewModels.current_trip.ReturnTransportOption
import com.example.rnd_transit_mtl.ViewModels.current_trip.ReturnTripState
import com.example.rnd_transit_mtl.model.ids.TransportId
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.ui.global_ui_ellements.buttons.TripFlowButton
import com.example.rnd_transit_mtl.ui.theme.TransitComplementary
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitSelected
import com.example.rnd_transit_mtl.ui.theme.TransitWhite
import org.jetbrains.compose.resources.painterResource
import rnd_transit_mtl.shared.generated.resources.Res
import rnd_transit_mtl.shared.generated.resources.rnd_mode_bike
import rnd_transit_mtl.shared.generated.resources.rnd_mode_bus
import rnd_transit_mtl.shared.generated.resources.rnd_mode_metro
import rnd_transit_mtl.shared.generated.resources.rnd_mode_walk

// Layout settings — Figma's return search, route-ready, and unavailable sheet heights and control sizes.
private val ReturnSearchingPanelHeight = 366.dp
private val ReturnReadyPanelHeight = 356.dp
private val ReturnUnavailablePanelHeight = 302.dp
private val ReturnModeIconSize = 28.dp
private val ReturnOptionHeight = 52.dp
private val ReturnProgressHeight = 64.dp
private val ReturnOptionCornerRadius = 24.dp

// Animation settings — milliseconds for one full sweep of the indeterminate return search bar.
private const val RETURN_SEARCH_SWEEP_DURATION_MILLIS = 1800

/**
 * Renders the Figma return flow from state without generating or storing routes during composition.
 *
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
    outbound: Trip,
    state: ReturnTripState,
    enabled: Boolean,
    onFind: (ReturnTransportOption) -> Unit,
    onCancel: () -> Unit,
    onStart: () -> Unit,
    onReview: () -> Unit,
    onSkip: () -> Unit,
    onPlanAnother: () -> Unit,
    modifier: Modifier = Modifier,
    syncPending: Boolean = false,
    errorMessage: String? = null,
) {
    val page = modifier.onPreviewKeyEvent {
        if (enabled && state.phase != ReturnPhase.Arrival && it.type == KeyEventType.KeyDown && it.key == Key.Escape) {
            onCancel()
            true
        } else {
            false
        }
    }
    if (state.phase == ReturnPhase.Arrival) {
        TripCompletionContent(
            trip = outbound,
            onReview = onReview,
            onSkipToHistory = onSkip,
            onPlanAnother = onPlanAnother,
            modifier = page,
            enabled = enabled,
            errorMessage = errorMessage,
            syncPending = syncPending,
            celebrate = !state.hasRequested,
            returnControls = {
                Text("Return to starting point · Trip #${outbound.id.value.takeLast(3)}", color = TransitWhite, fontSize = 14.sp)
                ReturnOptionControl(enabled, onFind)
            },
        )
        return
    }
    if (state.phase == ReturnPhase.AtStart) {
        BackAtStartContent(outbound, onReview, onPlanAnother, enabled, page)
        return
    }
    val proposal = state.proposal
    val finding = state.phase == ReturnPhase.Finding
    val ready = state.phase == ReturnPhase.Ready && proposal != null
    val panelHeight = when {
        finding -> ReturnSearchingPanelHeight
        ready -> ReturnReadyPanelHeight
        else -> ReturnUnavailablePanelHeight
    }
    val title = resolveReturnSheetTitle(finding, ready, state.option)

    TripJourneySheet(
        trip = proposal ?: outbound,
        enabled = enabled,
        modifier = page,
        completed = !ready,
        panelHeight = panelHeight,
        collapsedContent = {
            JourneyHeading(title)
            Text(
                "Trip #${outbound.id.value.takeLast(3)} is saved. Expand for return options.",
                color = TransitWhite,
                fontSize = 12.sp,
            )
        },
    ) {
        JourneyHeading(title)
        when {
            finding -> ReturnFindingBody(outbound, state, enabled, onCancel)
            state.phase == ReturnPhase.Ready && proposal != null ->
                ReturnReadyBody(outbound, proposal, state, enabled, errorMessage, onStart, onCancel)
            else -> ReturnUnavailableBody(outbound, state, enabled, onFind, onCancel)
        }
    }
}

/** Resolves the return-sheet heading for the current search phase and option. */
private fun resolveReturnSheetTitle(
    finding: Boolean,
    ready: Boolean,
    option: ReturnTransportOption?,
): String = when {
    finding && option == ReturnTransportOption.Fastest -> "Finding the fastest way back"
    finding -> "Finding your way back"
    ready && option == ReturnTransportOption.Fastest -> "Your fastest way back"
    ready -> "Back with the same transport"
    else -> "No return route found"
}

/** Body shown while comparing candidate return routes. */
@Composable
private fun ReturnFindingBody(
    outbound: Trip,
    state: ReturnTripState,
    enabled: Boolean,
    onCancel: () -> Unit,
) {
    Text(
        if (state.option == ReturnTransportOption.Fastest) "Comparing available routes and transport combinations."
        else "Using the transport selected for your outbound trip.",
        color = TransitWhite,
        fontSize = 14.sp,
    )
    val modes = if (state.option == ReturnTransportOption.Fastest) {
        ReturnTransportCatalogue.map { it.id }
    } else {
        outbound.requestedTransportIds ?: outbound.legs.map { it.transportId }.distinct()
    }
    ReturnModeIcons(modes)
    Text(
        modes.joinToString(" · ") { id -> ReturnTransportCatalogue.firstOrNull { it.id == id }?.label ?: id.value },
        color = TransitWhite,
        fontSize = 12.sp,
    )
    ReturnSearchProgress()
    Text("${state.startLabel ?: outbound.destination.label} → ${outbound.origin.label}", color = TransitWhite, fontSize = 12.sp)
    TripFlowButton("Cancel return", onCancel, enabled, secondary = true)
}

/** Body shown once a return route proposal is ready for explicit confirmation. */
@Composable
private fun ReturnReadyBody(
    outbound: Trip,
    route: Trip,
    state: ReturnTripState,
    enabled: Boolean,
    errorMessage: String?,
    onStart: () -> Unit,
    onCancel: () -> Unit,
) {
    Text(
        if (state.option == ReturnTransportOption.Fastest) "Available transport modes compared"
        else "Only your outbound modes",
        color = TransitWhite,
        fontSize = 14.sp,
    )
    Surface(color = TransitWhite, shape = RoundedCornerShape(14.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(painterResource(Res.drawable.rnd_mode_walk), null, Modifier.size(ReturnModeIconSize), tint = TransitMain)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Return to your starting point", color = TransitComplementary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${journeyMinutes(route.routedDurationSeconds ?: route.legs.sumOf { it.durationSeconds })} · ${journeyDistance(route.distanceKm)} · " +
                        route.legs.map { it.transportId }.distinct().joinToString(" + ") { id ->
                            route.selectedTransports.first { it.id == id }.label
                        },
                    color = TransitComplementary,
                    fontSize = 13.sp,
                )
            }
        }
    }
    Text(
        "Trip #${outbound.id.value.takeLast(3)} is saved: ${journeyDistance(outbound.distanceKm)}. Review it after you return.",
        color = TransitWhite,
        fontSize = 12.sp,
    )
    Text("Destination: ${outbound.origin.label}", color = TransitWhite, fontSize = 12.sp)
    if (!errorMessage.isNullOrBlank()) Text(errorMessage, color = TransitWhite, fontSize = 12.sp)
    TripFlowButton("Start return trip", onStart, enabled)
    TripFlowButton("Change return option", onCancel, enabled, secondary = true)
}

/** Body shown when no return route could be found, offering retry and option change. */
@Composable
private fun ReturnUnavailableBody(
    outbound: Trip,
    state: ReturnTripState,
    enabled: Boolean,
    onFind: (ReturnTransportOption) -> Unit,
    onCancel: () -> Unit,
) {
    Text(
        state.message ?: "Your trip and starting point are saved. Try again.",
        color = TransitWhite,
        fontSize = 14.sp,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    Text(
        "Trip #${outbound.id.value.takeLast(3)} · ${journeyDistance(outbound.distanceKm)} saved",
        color = TransitWhite,
        fontSize = 12.sp,
    )
    TripFlowButton(
        if (state.option == ReturnTransportOption.Fastest) "Retry return route" else "Try fastest way back",
        { onFind(ReturnTransportOption.Fastest) },
        enabled,
    )
    TripFlowButton("Change return option", onCancel, enabled, secondary = true)
}

/** Figma's split button exposing both Fastest and Same-transport return actions directly. */
@Composable
private fun ReturnOptionControl(
    enabled: Boolean,
    onFind: (ReturnTransportOption) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ReturnOptionHeight)
            .clip(RoundedCornerShape(ReturnOptionCornerRadius))
            .background(TransitMain),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .weight(1f)
                .heightIn(min = ReturnOptionHeight)
                .clickable(enabled, role = Role.Button) { onFind(ReturnTransportOption.Fastest) }
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Return Fast",
                color = TransitWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Canvas(Modifier.width(24.dp).height(ReturnOptionHeight)) {
            drawLine(
                TransitWhite,
                Offset(size.width, 0f),
                Offset(0f, size.height),
                strokeWidth = 6.dp.toPx(),
            )
        }
        Box(
            Modifier
                .weight(1f)
                .heightIn(min = ReturnOptionHeight)
                .clickable(enabled, role = Role.Button) { onFind(ReturnTransportOption.SameTransport) }
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Same Way",
                color = TransitWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Exact Figma mode glyphs for the transports the backend is comparing. */
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
    val sweep by transition.animateFloat(
        -0.38f,
        1f,
        infiniteRepeatable(tween(RETURN_SEARCH_SWEEP_DURATION_MILLIS, easing = LinearEasing)),
    )
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(ReturnProgressHeight)
            .clip(RoundedCornerShape(18.dp))
            .background(TransitWhite)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate },
    ) {
        Box(
            Modifier
                .offset(x = maxWidth * sweep)
                .width(maxWidth * 0.38f)
                .fillMaxHeight()
                .background(Brush.horizontalGradient(listOf(TransitMain, TransitSelected)), RoundedCornerShape(18.dp)),
        )
    }
}
