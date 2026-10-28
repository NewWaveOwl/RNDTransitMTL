package com.example.rnd_transit_mtl.View.main.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitSelected
import com.example.rnd_transit_mtl.ui.theme.TransitWhite
import org.jetbrains.compose.resources.painterResource
import rnd_transit_mtl.shared.generated.resources.Res
import rnd_transit_mtl.shared.generated.resources.ic_arrow_drop_down
import rnd_transit_mtl.shared.generated.resources.ic_arrow_drop_up
import kotlin.math.abs

// Layout settings — dp values are scaled by layoutScale except existing unscaled action/divider widths.
/** Compact GO height, also used by the planner to position GO above the sheet. */
internal val GoBoxCompactHeight = 76.dp
private val GoBoxStandardHeight = 96.dp
private val GoBoxContentHorizontalPadding = 8.dp
private val GoBoxActionWidth = 60.dp
private val GoBoxResumeActionWidth = 84.dp
private val GoBoxDividerWidth = 1.dp
private val GoBoxCompactDividerHeight = 48.dp
private val GoBoxStandardDividerHeight = 58.dp
private val GoBoxCornerRadius = 28.dp
private val MinutesSelectorWidth = 76.dp
private val MinutesNumberHorizontalInset = 8.dp
private val MinutesArrowSlotHeight = 22.dp
private val MinutesArrowIconSize = 20.dp

// Gesture settings — vertical distance per five-minute change; this is not animation duration.
private val MinutesDragStepDistance = 18.dp

/**
 * Stateless planned-minutes selector and primary planner action.
 *
 * The parent decides whether the action starts, resumes, or opens a completed trip.
 * Selected minutes do not determine the ten-second simulation duration.
 *
 * @param compact Uses the Figma planner's 76 dp height and two separators instead of the legacy layout.
 */
@Composable
internal fun GOBox(
    minutes: Int,
    onMinutesChange: (Int) -> Unit,
    onGo: () -> Unit,
    modifier: Modifier = Modifier,
    layoutScale: Float = 1f,
    actionLabel: String = "GO",
    actionEnabled: Boolean = true,
    minutesEnabled: Boolean = true,
    compact: Boolean = false
) {
    Row(
        modifier = modifier
            .height((if (compact) GoBoxCompactHeight else GoBoxStandardHeight) * layoutScale)
            .background(
                Brush.horizontalGradient(
                    0f to TransitMain,
                    0.84f to TransitMain,
                    1f to TransitSelected
                ),
                RoundedCornerShape(GoBoxCornerRadius * layoutScale)
            )
            .padding(horizontal = GoBoxContentHorizontalPadding * layoutScale),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("I have", color = TransitWhite, fontSize = 20.sp * layoutScale,
            maxLines = 1, softWrap = false)
        ControlDivider(layoutScale, compact)
        ScrollableMinutes(minutes, onMinutesChange, minutesEnabled, layoutScale)
        if (!compact) ControlDivider(layoutScale, compact)
        Text("minutes", color = TransitWhite, fontSize = 20.sp * layoutScale,
            maxLines = 1, softWrap = false)
        ControlDivider(layoutScale, compact)
        Box(
            modifier = Modifier
                .width(if (actionLabel == "Resume trip") GoBoxResumeActionWidth else GoBoxActionWidth)
                .fillMaxHeight()
                .clickable(enabled = actionEnabled, onClick = onGo)
                .semantics { contentDescription = actionLabel },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = actionLabel,
                color = TransitWhite.copy(alpha = if (actionEnabled) 1f else 0.5f),
                fontSize = (if (actionLabel == "Resume trip") 16.sp else 22.sp) * layoutScale,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun ControlDivider(layoutScale: Float, compact: Boolean) {
    Spacer(
        Modifier.width(GoBoxDividerWidth).height((if (compact) GoBoxCompactDividerHeight else GoBoxStandardDividerHeight) * layoutScale)
            .background(TransitWhite.copy(alpha = 0.8f))
    )
}

/**
 * Arrow taps and vertical dragging change minutes in five-minute steps,
 * bounded between 0 and 360 minutes. Empty arrow slots keep the number centred.
 *
 * Updated-state references let a continuous gesture keep using the latest
 * value/callback without restarting its pointer effect after each step.
 */
@Composable
private fun ScrollableMinutes(
    minutes: Int,
    onMinutesChange: (Int) -> Unit,
    enabled: Boolean,
    layoutScale: Float
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
        fontFeatureSettings = "tnum"
    )
    val textMeasurer = rememberTextMeasurer()
    val measuredWidth = textMeasurer.measure(
        text = numberText,
        style = numberStyle,
        softWrap = false,
        maxLines = 1
    ).size.width
    val availableWidth = with(density) {
        (numberWidth - MinutesNumberHorizontalInset * layoutScale).toPx()
    }
    val fontScale = (availableWidth / measuredWidth.coerceAtLeast(1))
        .coerceAtMost(1f)

    // Fixed centre and equal arrow slots fit inside both the 76 dp and 96 dp GO layouts.
    Box(modifier = Modifier.width(numberWidth).fillMaxHeight()) {
        Box(
            modifier = Modifier.align(Alignment.TopCenter).width(numberWidth).height(MinutesArrowSlotHeight * layoutScale)
                .semantics { contentDescription = "Increase planned time by five minutes" }
                .clickable(enabled = enabled && boundedMinutes < Trip.MAX_PLANNED_MINUTES,
                    role = Role.Button, onClickLabel = "Increase planned time by five minutes") {
                    latestOnMinutesChange(Trip.adjustPlannedMinutes(latestMinutes, direction = 1))
                },
            contentAlignment = Alignment.Center
        ) {
            if (boundedMinutes < Trip.MAX_PLANNED_MINUTES) {
                Icon(painterResource(Res.drawable.ic_arrow_drop_up), contentDescription = null,
                    tint = TransitWhite, modifier = Modifier.size(MinutesArrowIconSize * layoutScale))
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
                                    val direction =
                                        if (dragDistance < 0f) 1 else -1

                                    workingMinutes = Trip.adjustPlannedMinutes(
                                        workingMinutes,
                                        direction
                                    )

                                    latestOnMinutesChange(workingMinutes)
                                    dragDistance += direction * dragThreshold
                                }
                            }
                        )
                    }
                }
                .semantics {
                    contentDescription =
                        "Planned time: $boundedMinutes minutes. " +
                                "Drag up to increase or down to decrease."
                }
        )

        Box(
            modifier = Modifier.align(Alignment.BottomCenter).width(numberWidth).height(MinutesArrowSlotHeight * layoutScale)
                .semantics { contentDescription = "Decrease planned time by five minutes" }
                .clickable(enabled = enabled && boundedMinutes > Trip.MIN_PLANNED_MINUTES,
                    role = Role.Button, onClickLabel = "Decrease planned time by five minutes") {
                    latestOnMinutesChange(Trip.adjustPlannedMinutes(latestMinutes, direction = -1))
                },
            contentAlignment = Alignment.Center
        ) {
            if (boundedMinutes > Trip.MIN_PLANNED_MINUTES) {
                Icon(painterResource(Res.drawable.ic_arrow_drop_down), contentDescription = null,
                    tint = TransitWhite, modifier = Modifier.size(MinutesArrowIconSize * layoutScale))
            }
        }
    }
}
