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

/** Renders the floating time picker and GO control. */
@Composable
internal fun TripControls(
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

/** Displays and changes minutes using vertical dragging or arrow taps. */
@Composable
private fun ScrollableMinutes(minutes: Int, onMinutesChange: (Int) -> Unit, layoutScale: Float) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(64.dp * layoutScale)
            .pointerInput(minutes) {
                var dragDistance = 0f
                var workingMinutes = minutes
                detectVerticalDragGestures(
                    onDragStart = { dragDistance = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        dragDistance += dragAmount
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

/** Draws a short separator between parts of the trip control. */
@Composable
private fun ControlDivider(layoutScale: Float) {
    Spacer(
        Modifier
            .width(2.dp * layoutScale)
            .height(68.dp * layoutScale)
            .background(TransitWhite)
    )
}
