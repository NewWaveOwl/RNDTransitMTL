package com.example.rnd_transit_mtl.View.current_trip

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.model.trip.TripCompletionStatistics
import com.example.rnd_transit_mtl.ui.theme.TransitComplementary
import com.example.rnd_transit_mtl.ui.theme.TransitSelected
import com.example.rnd_transit_mtl.ui.theme.TransitWhite
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.painterResource
import rnd_transit_mtl.shared.generated.resources.Res
import rnd_transit_mtl.shared.generated.resources.rnd_distance_chevron
import rnd_transit_mtl.shared.generated.resources.rnd_trip_complete

// Layout settings — exact completion metrics and controls from Figma 371:2239 / 380:2800.
// Shared action button height is defined in JourneyButton.kt; arrival animation timing in TripJourneySheet.kt.
private val CompletionCheckSize = 48.dp
private val CompletionIconSize = 24.dp
private val CompletionMetricGap = 16.dp
private val CompletionMetricRowHeight = 62.dp
private val CompletionBreakdownWidth = 260.dp

/**
 * Arrival celebration and saved-route statistics, without changing the completed trip.
 *
 * @param trip Completed record, also used for the map and metric breakdown.
 * @param onReview Opens the existing review flow.
 * @param onSkipToHistory Skips review through the coordinator's checked action.
 * @param onPlanAnother Returns to the planner through the coordinator.
 * @param modifier Parent constraints.
 * @param enabled Whether actions and sheet gestures accept input.
 * @param errorMessage Coordinator feedback, leaving statistics and the map visible.
 * @param syncPending Whether the saved local record is still awaiting cloud sync.
 * @param celebrate Whether to play the Figma arrival confetti on this entry.
 * @param returnControls Return choice controls supplied by the return-flow coordinator.
 */
@Composable
internal fun TripCompletionContent(
    trip: Trip,
    onReview: () -> Unit,
    onSkipToHistory: () -> Unit,
    onPlanAnother: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    errorMessage: String? = null,
    syncPending: Boolean = false,
    celebrate: Boolean = true,
    returnControls: (@Composable () -> Unit)? = null,
) {
    val statistics = remember(trip) { TripCompletionStatistics.from(trip) }
    var celebrationPlayed by rememberSaveable(trip.id.value) { mutableStateOf(false) }
    val motion = remember(trip.id.value) { Animatable(if (celebrationPlayed || !celebrate) 1f else 0f) }
    LaunchedEffect(trip.id, celebrate) {
        if (!celebrationPlayed && celebrate) {
            celebrationPlayed = true
            motion.animateTo(1f, tween(ARRIVAL_CELEBRATION_MILLIS, easing = LinearEasing))
        } else {
            motion.snapTo(1f)
        }
    }
    TripJourneySheet(
        trip = trip,
        enabled = enabled,
        modifier = modifier,
        entranceProgress = motion.value,
        collapsedContent = {
            Text("Adventure complete!", color = TransitWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                "${journeyDistance(statistics.distanceKm)} · ${journeyMinutes(statistics.durationSeconds)} · Trip #${trip.id.value.takeLast(3)} saved",
                color = TransitWhite,
                fontSize = 12.sp,
            )
            Text("Drag up for trip stats and return options", color = TransitWhite, fontSize = 10.sp)
        },
        overlay = { ArrivalCelebration(motion.value, Modifier.align(Alignment.TopCenter)) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(CompletionCheckSize).background(TransitSelected, RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(painterResource(Res.drawable.rnd_trip_complete), null, Modifier.size(CompletionIconSize))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Adventure complete!",
                        color = TransitWhite,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite },
                    )
                    Text("Trip #${trip.id.value.takeLast(3)} · Destination reached", color = TransitWhite, fontSize = 12.sp)
                }
            }
            CompletionMetrics(trip, statistics, enabled)
        }
        if (syncPending) Text("Saved on this device; cloud sync pending.", color = TransitWhite, fontSize = 12.sp)
        if (!errorMessage.isNullOrBlank()) {
            Text(
                errorMessage,
                color = TransitWhite,
                fontSize = 12.sp,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (returnControls != null) {
            returnControls()
            JourneyButton("Trip Review", onReview, enabled, secondary = true)
            Text("Review now to finish the trip without returning.", color = TransitWhite, fontSize = 12.sp)
        } else {
            JourneyButton("Trip Review", onReview, enabled)
            JourneyButton("Skip review — view history", onSkipToHistory, enabled, secondary = true)
            JourneyButton("Plan another adventure", onPlanAnother, enabled, secondary = true)
        }
    }
}

/** Saved metric grid; distance opens a table of only the modes actually used. */
@Composable
private fun CompletionMetrics(trip: Trip, statistics: TripCompletionStatistics, enabled: Boolean) {
    var expanded by rememberSaveable(trip.id.value) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(CompletionMetricGap)) {
            Box(Modifier.weight(1f)) {
                CompletionMetric(
                    value = journeyDistance(statistics.distanceKm),
                    label = "Distance",
                    detail = "Tap for breakdown",
                    modifier = Modifier.fillMaxWidth().clickable(
                        enabled = enabled,
                        role = Role.Button,
                        onClickLabel = "Distance by transport",
                    ) { expanded = true },
                )
                Image(
                    painterResource(Res.drawable.rnd_distance_chevron),
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 3.dp).size(16.dp),
                )
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    containerColor = TransitWhite,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(
                        Modifier.width(CompletionBreakdownWidth).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Distance by transport", color = TransitComplementary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        statistics.distanceByTransportKm.forEach { (id, distance) ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    trip.selectedTransports.firstOrNull {
                                        it.id.value == id || id == "walk" && it.id.value == "walking"
                                    }?.label ?: id,
                                    color = TransitComplementary,
                                    fontSize = 14.sp,
                                )
                                Text(journeyDistance(distance), color = TransitComplementary, fontSize = 14.sp)
                            }
                        }
                        HorizontalDivider(color = TransitComplementary.copy(alpha = 0.2f))
                        Text(
                            "Total ${journeyDistance(statistics.distanceKm)}",
                            color = TransitComplementary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                        )
                        Text("Only modes used in this trip are shown.", color = TransitComplementary, fontSize = 10.sp)
                    }
                }
            }
            CompletionMetric(journeyMinutes(statistics.durationSeconds), "Time", "Route estimate", Modifier.weight(1f))
            CompletionMetric(
                "≈ ${statistics.walkingSteps}",
                "Walking steps",
                "Estimated · ${journeyDistance(statistics.walkingDistanceKm)}",
                Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(CompletionMetricGap)) {
            CompletionMetric(journeyMinutes(statistics.walkingSeconds), "Walking time", "Estimated on foot", Modifier.weight(1f))
            CompletionMetric(
                statistics.averageSpeedKmh?.let { "${(it * 10).roundToInt() / 10.0} km/h" } ?: "—",
                "Average speed",
                "Route estimate",
                Modifier.weight(1f),
            )
            CompletionMetric("—", "Fastest speed", "Not recorded", Modifier.weight(1f))
        }
    }
}

/** Figma metric typography in a responsive grid cell. */
@Composable
private fun CompletionMetric(value: String, label: String, detail: String, modifier: Modifier = Modifier) {
    Column(modifier.heightIn(min = CompletionMetricRowHeight), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, color = TransitWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = TransitWhite, fontSize = 12.sp)
        Text(detail, color = TransitWhite.copy(alpha = 0.8f), fontSize = 10.sp)
    }
}
