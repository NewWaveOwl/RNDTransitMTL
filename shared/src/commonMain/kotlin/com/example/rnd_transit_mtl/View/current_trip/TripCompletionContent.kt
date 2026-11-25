package com.example.rnd_transit_mtl.View.current_trip

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.model.trip.TripCompletionStatistics
import com.example.rnd_transit_mtl.ui.theme.*
import org.jetbrains.compose.resources.painterResource
import rnd_transit_mtl.shared.generated.resources.*
import kotlin.math.ceil
import kotlin.math.roundToInt

// Layout settings — exact completion metrics and controls from Figma 371:2239 / 380:2800.
private val CompletionCheckSize = 48.dp
private val CompletionIconSize = 24.dp
private val CompletionMetricGap = 16.dp
private val CompletionActionHeight = 52.dp
private val CompletionActionCorner = 24.dp
private val CompletionMetricRowHeight = 62.dp
private val CompletionBreakdownWidth = 260.dp
private val CelebrationHeight = 250.dp
private const val CelebrationParticleCount = 28

/**
 * Arrival celebration and saved-route statistics, without changing the completed trip.
 * @param trip Completed record, also used for the map and metric breakdown.
 * @param onReview Opens the existing review flow.
 * @param onSkipToHistory Skips review through the coordinator's checked action.
 * @param onPlanAnother Returns to the planner through the coordinator.
 * @param modifier Parent constraints.
 * @param enabled Whether actions and sheet gestures accept input.
 * @param errorMessage Coordinator feedback, leaving statistics and the map visible.
 * @param syncPending Whether the saved local record is still awaiting cloud sync.
 * @param celebrate Whether to play the Figma arrival cohort on this entry.
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
    returnControls: (@Composable () -> Unit)? = null
) {
    val statistics = remember(trip) { TripCompletionStatistics.from(trip) }
    var celebrationPlayed by rememberSaveable(trip.id.value) { mutableStateOf(false) }
    val motion = remember(trip.id.value) { Animatable(if (celebrationPlayed || !celebrate) 1f else 0f) }
    LaunchedEffect(trip.id, celebrate) {
        if (!celebrationPlayed && celebrate) {
            celebrationPlayed = true
            motion.animateTo(1f, tween(ARRIVAL_CELEBRATION_MILLIS, easing = LinearEasing))
        } else motion.snapTo(1f)
    }
    TripJourneySheet(trip, enabled, modifier, entranceProgress = motion.value,
        collapsedContent = {
            Text("Adventure complete!", color = TransitWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("${journeyDistance(statistics.distanceKm)} · ${journeyMinutes(statistics.durationSeconds)} · Trip #${trip.id.value.takeLast(3)} saved",
                color = TransitWhite, fontSize = 12.sp)
            Text("Drag up for trip stats and return options", color = TransitWhite, fontSize = 10.sp)
        }, overlay = { ArrivalCelebration(motion.value, Modifier.align(Alignment.TopCenter)) }) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(CompletionCheckSize).background(TransitSelected, RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center) {
                    Image(painterResource(Res.drawable.rnd_trip_complete), null, Modifier.size(CompletionIconSize))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Adventure complete!", color = TransitWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite })
                    Text("Trip #${trip.id.value.takeLast(3)} · Destination reached", color = TransitWhite, fontSize = 12.sp)
                }
            }
            CompletionMetrics(trip, statistics, enabled)
        }
        if (syncPending) Text("Saved on this device; cloud sync pending.", color = TransitWhite, fontSize = 12.sp)
        if (!errorMessage.isNullOrBlank()) Text(errorMessage, color = TransitWhite, fontSize = 12.sp,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
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

/** Saved metric grid; distance opens a table of only the modes actually used.
 * @param trip Record providing transport labels.
 * @param statistics Derived values, with estimate provenance kept in the labels.
 * @param enabled Whether the breakdown popup can open.
 */
@Composable
private fun CompletionMetrics(trip: Trip, statistics: TripCompletionStatistics, enabled: Boolean) {
    var expanded by rememberSaveable(trip.id.value) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(CompletionMetricGap)) {
            Box(Modifier.weight(1f)) {
                CompletionMetric(journeyDistance(statistics.distanceKm), "Distance", "Tap for breakdown",
                    Modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Button,
                        onClickLabel = "Distance by transport") { expanded = true })
                Image(painterResource(Res.drawable.rnd_distance_chevron), null,
                    Modifier.align(Alignment.TopEnd).padding(top = 3.dp).size(16.dp))
                DropdownMenu(expanded, { expanded = false }, containerColor = TransitWhite,
                    shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.width(CompletionBreakdownWidth).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Distance by transport", color = TransitComplementary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        statistics.distanceByTransportKm.forEach { (id, distance) ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(trip.selectedTransports.firstOrNull {
                                    it.id.value == id || id == "walk" && it.id.value == "walking"
                                }?.label ?: id,
                                    color = TransitComplementary, fontSize = 14.sp)
                                Text(journeyDistance(distance), color = TransitComplementary, fontSize = 14.sp)
                            }
                        }
                        HorizontalDivider(color = TransitComplementary.copy(alpha = 0.2f))
                        Text("Total ${journeyDistance(statistics.distanceKm)}", color = TransitComplementary,
                            fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Only modes used in this trip are shown.", color = TransitComplementary, fontSize = 10.sp)
                    }
                }
            }
            CompletionMetric(journeyMinutes(statistics.durationSeconds), "Time", "Route estimate", Modifier.weight(1f))
            CompletionMetric("≈ ${statistics.walkingSteps}", "Walking steps",
                "Estimated · ${journeyDistance(statistics.walkingDistanceKm)}", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(CompletionMetricGap)) {
            CompletionMetric(journeyMinutes(statistics.walkingSeconds), "Walking time", "Estimated on foot", Modifier.weight(1f))
            CompletionMetric(statistics.averageSpeedKmh?.let { "${(it * 10).roundToInt() / 10.0} km/h" } ?: "—",
                "Average speed", "Route estimate", Modifier.weight(1f))
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

/** Primary/secondary Figma action used by arrival, return preview and retry states. */
@Composable
internal fun JourneyButton(label: String, onClick: () -> Unit, enabled: Boolean,
    secondary: Boolean = false, modifier: Modifier = Modifier) {
    Button(onClick, modifier.fillMaxWidth().heightIn(min = CompletionActionHeight), enabled = enabled,
        shape = RoundedCornerShape(CompletionActionCorner),
        border = if (secondary) BorderStroke(1.dp, TransitMain) else null,
        colors = ButtonDefaults.buttonColors(containerColor = if (secondary) TransitWhite else TransitMain,
            contentColor = if (secondary) TransitMain else TransitWhite)) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

/** Kilometres rounded to the same single decimal as the saved-trip screens. */
internal fun journeyDistance(kilometres: Double): String = "${(kilometres * 10).roundToInt() / 10.0} km"

/** Whole minutes rounded upward; zero remains zero for trips without a walking leg. */
internal fun journeyMinutes(seconds: Long): String = "${ceil(seconds / 60.0).toLong()} min"

/** Figma's 28 vector particles and authoritative motion tracks, drawn without an input layer.
 * @param progress Position on the shared two-second arrival timeline.
 * @param modifier Placement over the map.
 */
@Composable
private fun ArrivalCelebration(progress: Float, modifier: Modifier = Modifier) {
    if (progress >= 0.8f) return
    Canvas(modifier.fillMaxWidth().height(CelebrationHeight).clipToBounds()) {
        val scale = size.width / 412f
        val colors = listOf(TransitSelected, TransitHighlight, TransitWhite, TransitMain)
        val opacity = 1f - ArrivalEaseOut.transform(((progress - 0.35f) / 0.45f).coerceIn(0f, 1f))
        repeat(CelebrationParticleCount) { index ->
            val fall = ArrivalEaseOut.transform((progress / (0.70f + (index % 4) * 0.03f)).coerceIn(0f, 1f))
            val drift = ArrivalEaseOut.transform((progress / 0.75f).coerceIn(0f, 1f))
            val x = (18 + (index * 53) % 376 + listOf(-10, 14, -18, 22)[index % 4] * drift) * scale
            val y = (14 + (index * 37) % 210 + (90 + 12 * (index % 5)) * fall) * scale
            val width = (if (index % 3 == 0) 5f else 8f) * scale
            val height = (if (index % 3 == 0) 12f else 5f) * scale
            val angle = -((index * 29) % 170) + (if (index % 2 == 0) 100 else -100) * drift
            rotate(angle, Offset(x + width / 2, y + height / 2)) {
                drawRoundRect(colors[index % colors.size].copy(alpha = opacity), Offset(x, y),
                    Size(width, height), CornerRadius(scale, scale))
            }
        }
    }
}
