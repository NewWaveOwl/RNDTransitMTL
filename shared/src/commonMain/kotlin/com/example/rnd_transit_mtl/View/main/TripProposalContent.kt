package com.example.rnd_transit_mtl.View.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rnd_transit_mtl.data.assets.toSampleMapPoint
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.model.trip.explorationWaypoints
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.LocalTransitMap
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.MockTripMap
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.TransitMap
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.TransitMapData
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.toSampleMapLegs
import com.example.rnd_transit_mtl.ui.global_ui_ellements.transitions.tripPanelEntrance
import com.example.rnd_transit_mtl.ui.theme.TransitComplementary
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitWhite
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import rnd_transit_mtl.shared.generated.resources.Res
import rnd_transit_mtl.shared.generated.resources.rnd_recenter

// Layout settings — dp limits for the trip proposal sheet; shared max width is in TripLabelValueField.kt.
private val ProposalPanelMaximumHeight = 340.dp
private val ProposalPanelBottomGap = 28.dp
private val ProposalRecenterSize = 48.dp
private val ProposalHeaderPadding = 16.dp
private val ProposalSheetPadding = 16.dp
private val ProposalSheetSpacing = 10.dp
private val ProposalActionRowSpacing = 12.dp

// Animation settings — milliseconds between live departure-countdown refreshes.
private const val PROPOSAL_DEPARTURE_TICK_INTERVAL_MILLIS = 1_000L

/**
 * Shows a generated proposal without activating it.
 *
 * @param trip Generated trip awaiting confirmation.
 * @param onStart Requests activation of this proposal.
 * @param onGenerateAnother Requests a fresh proposal.
 * @param onEditSettings Returns to the saved planner choices.
 * @param modifier Parent layout constraints.
 * @param scheduled Whether to display the scheduled/routed duration rather than planned minutes.
 * @param onRename Optional callback updating the proposal name before it becomes an active trip.
 * @param generationError Failed replacement request; the displayed proposal remains available.
 */
@Composable
internal fun TripProposalContent(
    trip: Trip,
    onStart: () -> Unit,
    onGenerateAnother: () -> Unit,
    onEditSettings: () -> Unit,
    modifier: Modifier = Modifier,
    scheduled: Boolean = false,
    onRename: ((String) -> Unit)? = null,
    generationError: String? = null,
) {
    var nameDraft by rememberSaveable(trip.id.value) { mutableStateOf(trip.title) }
    val nowMillis by produceState(
        initialValue = Clock.System.now().toEpochMilliseconds(),
        key1 = trip.id,
    ) {
        if (trip.legs.none { it.transitTiming != null }) return@produceState
        while (true) {
            delay(PROPOSAL_DEPARTURE_TICK_INTERVAL_MILLIS)
            value = Clock.System.now().toEpochMilliseconds()
        }
    }
    val liveMap = LocalTransitMap.current != null
    val density = LocalDensity.current
    var headerHeight by remember { mutableIntStateOf(0) }
    var panelHeight by remember { mutableIntStateOf(0) }
    var recenterRequest by remember(trip.id) { mutableIntStateOf(0) }

    Box(modifier.fillMaxSize()) {
        TransitMap(
            data = TransitMapData(
                trip = trip,
                progress = 0f,
                recenterRequest = recenterRequest,
                topInset = with(density) { headerHeight.toDp() },
                bottomInset = with(density) { panelHeight.toDp() } + ProposalPanelBottomGap,
                sharedTripFlow = true,
                cameraInsetsReady = headerHeight > 0 && panelHeight > 0,
            ),
            modifier = Modifier.fillMaxSize(),
        ) {
            MockTripMap(
                start = trip.origin.toSampleMapPoint(),
                destination = trip.destination.toSampleMapPoint(),
                waypoints = trip.explorationWaypoints.map { it.toSampleMapPoint() },
                legs = trip.toSampleMapLegs(),
                progress = 0f,
                recenterRequest = recenterRequest,
                showControls = false,
                matchPlannerBackground = true,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .onSizeChanged { headerHeight = it.height }
                .tripPanelEntrance(liveMap, fromBottom = false)
                .widthIn(max = ProposalPanelMaxWidth)
                .fillMaxWidth()
                .padding(ProposalHeaderPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (generationError != null) {
                Surface(shape = RoundedCornerShape(16.dp), color = TransitWhite) {
                    Text(generationError, color = TransitMain, modifier = Modifier.padding(ProposalSheetPadding))
                }
            }
            TripLabelValueField(
                label = "Starting point",
                value = trip.origin.label,
                showLocationIcon = true,
            )
            Surface(shape = RoundedCornerShape(16.dp), color = TransitWhite) {
                Text(
                    text = "${if (trip.routedDurationSeconds != null) "Generated trip" else "Sample trip"} · #${trip.id.value.takeLast(3)}",
                    color = TransitMain,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }

        Image(
            painterResource(Res.drawable.rnd_recenter),
            contentDescription = "Recenter on route preview",
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = ProposalHeaderPadding,
                    bottom = with(density) { panelHeight.toDp() } + ProposalPanelBottomGap + 12.dp,
                )
                .size(ProposalRecenterSize)
                .clickable { recenterRequest++ },
        )

        ProposalSummarySheet(
            trip = trip,
            nameDraft = nameDraft,
            scheduled = scheduled,
            nowMillis = nowMillis,
            liveMap = liveMap,
            onNameChange = { name ->
                nameDraft = name
                if (name.isNotBlank()) onRename?.invoke(name.trim())
            },
            onNameFocusLost = {
                if (nameDraft.isBlank()) nameDraft = trip.title
            },
            canRename = onRename != null,
            onStart = onStart,
            onGenerateAnother = onGenerateAnother,
            onEditSettings = onEditSettings,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = ProposalPanelBottomGap)
                .onSizeChanged { panelHeight = it.height }
                .tripPanelEntrance(liveMap)
                .widthIn(max = ProposalPanelMaxWidth)
                .fillMaxWidth(),
        )
    }
}

/**
 * Bottom summary card showing the editable proposal title, duration, distance, transit departures, and actions.
 */
@Composable
private fun ProposalSummarySheet(
    trip: Trip,
    nameDraft: String,
    scheduled: Boolean,
    nowMillis: Long,
    liveMap: Boolean,
    onNameChange: (String) -> Unit,
    onNameFocusLost: () -> Unit,
    canRename: Boolean,
    onStart: () -> Unit,
    onGenerateAnother: () -> Unit,
    onEditSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        if (!liveMap) SampleMapAttribution(Modifier.padding(start = 8.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = ProposalPanelMaximumHeight)
                .background(TransitComplementary)
                .verticalScroll(rememberScrollState())
                .padding(ProposalSheetPadding),
            verticalArrangement = Arrangement.spacedBy(ProposalSheetSpacing),
        ) {
            BasicTextField(
                value = nameDraft,
                onValueChange = onNameChange,
                readOnly = !canRename,
                singleLine = true,
                textStyle = TextStyle(
                    color = TransitWhite,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                ),
                cursorBrush = SolidColor(TransitWhite),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Trip name" }
                    .onFocusChanged { if (!it.isFocused) onNameFocusLost() },
            )
            val distanceTenths = (trip.distanceKm * 10).roundToInt()
            val distance = "${distanceTenths / 10},${distanceTenths % 10}"
            val minutes = resolveProposalDurationMinutes(trip, scheduled)
            Text(
                text = "$minutes min ($distance km)",
                color = TransitWhite,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = trip.selectedTransports.joinToString(" + ") { it.label },
                color = TransitWhite,
                fontSize = 15.sp,
            )

            for (leg in trip.legs.filter { it.transportId.value in listOf("bus", "metro", "train", "rem") }) {
                val routeLabel = trip.selectedRoutes.firstOrNull { it.id == leg.routeId }?.label
                Text(
                    text = transitDepartureSummary(leg, routeLabel, nowMillis),
                    color = TransitWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            ProposalButton("Start Trip", true, onStart)

            Row(horizontalArrangement = Arrangement.spacedBy(ProposalActionRowSpacing)) {
                ProposalButton(
                    "Generate another",
                    false,
                    onGenerateAnother,
                    Modifier.weight(1f),
                )
                ProposalButton(
                    "Edit settings",
                    false,
                    onEditSettings,
                    Modifier.weight(1f),
                )
            }
        }
    }
}

/** Computes the displayed proposal duration in minutes using routed or scheduled timing when present. */
private fun resolveProposalDurationMinutes(trip: Trip, scheduled: Boolean): Int {
    val timedLegs = trip.legs.filter { it.transitTiming != null }
    val elapsedSeconds = trip.routedDurationSeconds ?: if (timedLegs.size > 1) {
        val first = timedLegs.first().transitTiming
        val last = timedLegs.last().transitTiming
        if (first != null && last != null) {
            val departure = first.estimatedDeparture.takeIf { first.realtimeApplied } ?: first.scheduledDeparture
            val arrival = last.estimatedArrival.takeIf { last.realtimeApplied } ?: last.scheduledArrival
            runCatching { (Instant.parse(arrival) - Instant.parse(departure)).inWholeSeconds }
                .getOrNull()?.takeIf { it > 0 } ?: trip.legs.sumOf { it.durationSeconds }
        } else {
            trip.legs.sumOf { it.durationSeconds }
        }
    } else {
        trip.legs.sumOf { it.durationSeconds }
    }
    return if (scheduled || trip.routedDurationSeconds != null) {
        (elapsedSeconds / 60.0).roundToInt()
    } else {
        trip.plannedMinutes
    }
}
