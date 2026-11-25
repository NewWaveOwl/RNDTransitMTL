package com.example.rnd_transit_mtl.View.main.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.ui.theme.TransitWhite
import kotlin.math.abs
import org.jetbrains.compose.resources.painterResource
import rnd_transit_mtl.shared.generated.resources.Res
import rnd_transit_mtl.shared.generated.resources.ic_arrow_drop_down
import rnd_transit_mtl.shared.generated.resources.ic_arrow_drop_up

// Layout settings — dp values scaled by layoutScale for the minutes wheel inside GOBox.
private val MinutesSelectorWidth = 76.dp
private val MinutesNumberHorizontalInset = 8.dp
private val MinutesArrowSlotHeight = 22.dp
private val MinutesArrowIconSize = 20.dp

// Gesture settings — vertical drag distance per one-minute step; this is not animation duration.
private val MinutesDragStepDistance = 18.dp

/**
 * Interactive minutes selector supporting arrow taps and vertical dragging in one-minute steps,
 * bounded between [Trip.MIN_PLANNED_MINUTES] and [Trip.MAX_PLANNED_MINUTES].
 *
 * Updated-state references let a continuous gesture keep using the latest
 * value/callback without restarting its pointer effect after each step.
 *
 * @param minutes Current planned duration in minutes.
 * @param onMinutesChange Called with the updated bounded duration in minutes.
 * @param enabled Whether taps and vertical drags can adjust the value.
 * @param layoutScale Responsive layout multiplier supplied by [GOBox].
 */
@Composable
internal fun ScrollableMinutes(
    minutes: Int,
    onMinutesChange: (Int) -> Unit,
    enabled: Boolean,
    layoutScale: Float,
) {
    val boundedMinutes = Trip.coercePlannedMinutes(minutes)
    val latestMinutes by rememberUpdatedState(boundedMinutes)
    val latestOnMinutesChange by rememberUpdatedState(onMinutesChange)
    val density = LocalDensity.current
    val dragThreshold = with(density) { MinutesDragStepDistance.toPx() }
    val numberWidth = MinutesSelectorWidth * layoutScale
    val numberText = boundedMinutes.toString()
    val numberStyle = MaterialTheme.typography.bodyLarge.copy(
        fontSize = 28.sp * layoutScale,
        lineHeight = 30.sp * layoutScale,
        textAlign = TextAlign.Center,
        fontFeatureSettings = "tnum",
    )
    val textMeasurer = rememberTextMeasurer()
    val measuredWidth = textMeasurer.measure(
        text = numberText,
        style = numberStyle,
        softWrap = false,
        maxLines = 1,
    ).size.width
    val availableWidth = with(density) {
        (numberWidth - MinutesNumberHorizontalInset * layoutScale).toPx()
    }
    val fontScale = (availableWidth / measuredWidth.coerceAtLeast(1)).coerceAtMost(1f)

    // Fixed centre and equal arrow slots fit inside both the 76 dp and 96 dp GO layouts.
    Box(modifier = Modifier.width(numberWidth).fillMaxHeight()) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .width(numberWidth)
                .height(MinutesArrowSlotHeight * layoutScale)
                .semantics { contentDescription = "Increase planned time by one minute" }
                .clickable(
                    enabled = enabled && boundedMinutes < Trip.MAX_PLANNED_MINUTES,
                    role = Role.Button,
                    onClickLabel = "Increase planned time by one minute",
                ) {
                    latestOnMinutesChange(Trip.adjustPlannedMinutes(latestMinutes, direction = 1))
                },
            contentAlignment = Alignment.Center,
        ) {
            if (boundedMinutes < Trip.MAX_PLANNED_MINUTES) {
                Icon(
                    painterResource(Res.drawable.ic_arrow_drop_up),
                    contentDescription = null,
                    tint = TransitWhite,
                    modifier = Modifier.size(MinutesArrowIconSize * layoutScale),
                )
            }
        }

        Text(
            text = numberText,
            color = TransitWhite,
            style = numberStyle.copy(fontSize = numberStyle.fontSize * fontScale),
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .pointerInput(enabled, dragThreshold) {
                    if (enabled) {
                        var dragDistance = 0f
                        var workingMinutes = latestMinutes
                        detectVerticalDragGestures(
                            onDragStart = {
                                dragDistance = 0f
                                workingMinutes = latestMinutes
                            },
                            onVerticalDrag = { change, amount ->
                                change.consume()
                                dragDistance += amount
                                while (abs(dragDistance) >= dragThreshold) {
                                    val direction = if (dragDistance < 0f) 1 else -1
                                    workingMinutes = Trip.adjustPlannedMinutes(workingMinutes, direction)
                                    latestOnMinutesChange(workingMinutes)
                                    dragDistance += direction * dragThreshold
                                }
                            },
                        )
                    }
                }
                .semantics {
                    contentDescription =
                        "Planned time: $boundedMinutes minutes. Drag up to increase or down to decrease."
                },
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .width(numberWidth)
                .height(MinutesArrowSlotHeight * layoutScale)
                .semantics { contentDescription = "Decrease planned time by one minute" }
                .clickable(
                    enabled = enabled && boundedMinutes > Trip.MIN_PLANNED_MINUTES,
                    role = Role.Button,
                    onClickLabel = "Decrease planned time by one minute",
                ) {
                    latestOnMinutesChange(Trip.adjustPlannedMinutes(latestMinutes, direction = -1))
                },
            contentAlignment = Alignment.Center,
        ) {
            if (boundedMinutes > Trip.MIN_PLANNED_MINUTES) {
                Icon(
                    painterResource(Res.drawable.ic_arrow_drop_down),
                    contentDescription = null,
                    tint = TransitWhite,
                    modifier = Modifier.size(MinutesArrowIconSize * layoutScale),
                )
            }
        }
    }
}
