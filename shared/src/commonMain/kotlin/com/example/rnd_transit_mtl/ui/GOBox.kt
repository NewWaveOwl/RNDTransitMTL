package com.example.rnd_transit_mtl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import com.example.rnd_transit_mtl.ui.theme.TransitWhite
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitSelected
import org.jetbrains.compose.resources.painterResource
import rnd_transit_mtl.shared.generated.resources.Res
import rnd_transit_mtl.shared.generated.resources.ic_arrow_drop_up
import rnd_transit_mtl.shared.generated.resources.ic_arrow_drop_down
import kotlin.math.abs

/**
 * Displays the floating duration selector and trip-generation action.
 *
 * Duration changes and the GO action are delegated to the parent through callbacks.
 *
 * @param layoutScale Scale factor for dimensions and text relative to the 402-unit reference width.
 * @param minutes Selected trip duration in minutes.
 * @param onMinutesChange Receives the new duration when the user taps an arrow or drags the minute selector.
 * @param onGo Requests a trip using the current selections.
 * @param modifier Layout and appearance modifiers supplied by the parent.
 */
@Composable
internal fun GOBox(
    layoutScale: Float = 1f,
    minutes: Int,
    onMinutesChange: (Int) -> Unit,
    onGo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth(0.92f)
            .height(90.dp * layoutScale)
            .background(
                brush = Brush.horizontalGradient(
                    0.00f to TransitMain,
                    0.84f to TransitMain,
                    1.00f to TransitSelected
                ),
                shape = RoundedCornerShape(28.dp * layoutScale)
            )
            .padding(horizontal = 12.dp * layoutScale),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("I have", color = TransitWhite, fontSize = 26.sp * layoutScale, maxLines = 1, softWrap = false)
        ControlDivider(layoutScale)
        ScrollableMinutes(minutes = minutes, onMinutesChange = onMinutesChange, layoutScale = layoutScale)
        ControlDivider(layoutScale)
        Text("minutes", color = TransitWhite, fontSize = 26.sp * layoutScale, maxLines = 1, softWrap = false)
        ControlDivider(layoutScale)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxHeight()
                .width(60.dp * layoutScale)
                .clickable(onClick = onGo)
        ) {
            Text("GO", color = TransitWhite, fontSize = 28.sp * layoutScale, maxLines = 1, softWrap = false)
        }
    }
}

/**
 * Changes trip duration in five-minute steps through arrow taps or vertical dragging.
 *
 * Dragging up increases the duration; dragging down decreases it. Drag updates
 * are clamped to 5–240 minutes, and the arrow buttons enforce their respective limits.
 *
 * @param minutes Selected trip duration in minutes, expected to be between 5 and 240.
 * @param onMinutesChange Receives the updated duration after a tap or drag step.
 * @param layoutScale Scale factor for dimensions and text relative to the 402-unit reference width.
 */
@Composable
private fun ScrollableMinutes(minutes: Int, onMinutesChange: (Int) -> Unit, layoutScale: Float) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(64.dp * layoutScale)
            .pointerInput(minutes) {
                /** Track movement since the last step and the duration used for the next drag update. */
                var dragDistance = 0f
                var workingMinutes = minutes
                detectVerticalDragGestures(
                    onDragStart = { dragDistance = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        /** Consume the movement so another gesture handler does not also act on it. */
                        change.consume()
                        dragDistance += dragAmount
                        /**
                         * Apply one five-minute step after at least 18 pixels of movement.
                         * Clamp the duration to 5–240 minutes, then reset the distance for the next step.
                         */
                        if (abs(dragDistance) >= 18f) {
                            workingMinutes = (workingMinutes + if (dragDistance < 0f) 5 else -5)
                                .coerceIn(5, 240)
                            onMinutesChange(workingMinutes)
                            dragDistance = 0f
                        }
                    }
                )
            }
            .semantics { contentDescription = "Scrollable trip time: $minutes minutes" }
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_arrow_drop_up),
            contentDescription = "Increase trip time",
            tint = TransitWhite,
            modifier = Modifier.size(26.dp * layoutScale).clickable {
                onMinutesChange((minutes + 5).coerceAtMost(240))
            }
        )
        Text(
            text = minutes.toString(),
            color = TransitWhite,
            fontSize = 28.sp * layoutScale,
            lineHeight = 30.sp * layoutScale,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Icon(
            painter = painterResource(Res.drawable.ic_arrow_drop_down),
            contentDescription = "Decrease trip time",
            tint = TransitWhite,
            modifier = Modifier.size(26.dp * layoutScale).clickable {
                onMinutesChange((minutes - 5).coerceAtLeast(5))
            }
        )
    }
}

/**
 * Draws a separator between sections of GOBox.
 *
 * @param layoutScale Scale factor for dimensions and text relative to the 402-unit reference width.
 */
@Composable
private fun ControlDivider(layoutScale: Float) {
    Spacer(
        Modifier
            .width(2.dp * layoutScale)
            .height(68.dp * layoutScale)
            .background(TransitWhite)
    )
}