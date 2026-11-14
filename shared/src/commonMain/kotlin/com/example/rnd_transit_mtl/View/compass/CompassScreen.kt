package com.example.rnd_transit_mtl.View.compass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.ui.theme.*

internal val LocalCompassControlsVisible = staticCompositionLocalOf { false }

/**
 * Presentation only: supply directionDegrees clockwise from north (0=N, 90=E).
 * No sensor or routing assumptions are made here. Destination names stay private.
 * Action callbacks are optional so trip completion/presets can be connected later.
 */
@Composable
fun CompassScreen(
    activeTrip: Trip? = null,
    directionDegrees: Float = 45f,
    controlsVisible: Boolean = LocalCompassControlsVisible.current,
    onOpenMap: () -> Unit = {},
    onFinishTrip: (() -> Unit)? = null,
    onOpenPresets: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize().background(TransitComplementary).safeDrawingPadding()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.alpha(if (controlsVisible) 1f else 0f)) {
                Text(if (activeTrip != null) "Find your next direction" else "Follow your curiosity",
                    color = TransitWhite, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(if (activeTrip != null) "Trip · Mystery destination" else "Compass · no active trip",
                    color = TransitWhite, fontSize = 14.sp)
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                CompassDial(directionDegrees, Modifier.widthIn(max = 420.dp).aspectRatio(1f)
                    .alpha(if (activeTrip == null && controlsVisible) 0.6f else 1f))
            }
            Text("Hold the screen to reveal controls", color = TransitWhite.copy(alpha = 0.65f), fontSize = 12.sp)
        }
        if (controlsVisible) {
            Column(Modifier.fillMaxWidth().background(TransitMain).padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (activeTrip != null) "Current trip" else "Plan your next trip",
                            color = TransitWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(if (activeTrip != null) "Mystery destination" else "Set your trip preferences on the map.",
                            color = TransitWhite, fontSize = 12.sp)
                    }
                    Text("GO", color = TransitWhite, fontWeight = FontWeight.Bold,
                        modifier = Modifier.background(Brush.horizontalGradient(listOf(TransitMain, TransitSelected)),
                            RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 6.dp))
                }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CompassAction(if (activeTrip != null) "Back to map" else "Open map", onOpenMap, Modifier.weight(1f))
                    CompassAction(if (activeTrip != null) "Finish trip" else "Trip presets",
                        if (activeTrip != null) onFinishTrip else onOpenPresets, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CompassAction(label: String, action: (() -> Unit)?, modifier: Modifier) {
    Button(onClick = { action?.invoke() }, enabled = action != null, modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = TransitWhite, contentColor = TransitMain,
            disabledContainerColor = TransitWhite, disabledContentColor = TransitMain.copy(alpha = 0.5f))) {
        Text(label, fontSize = 12.sp)
    }
}

/** Vector needle rotates independently of the stationary cardinal labels. */
@Composable
fun CompassDial(directionDegrees: Float, modifier: Modifier = Modifier) {
    val angle = if (directionDegrees.isFinite()) ((directionDegrees % 360f) + 360f) % 360f else 0f
    Box(modifier.semantics { contentDescription = "Compass direction ${angle.toInt()} degrees clockwise from north" }) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f - 4.dp.toPx()
            drawCircle(TransitWhite, radius)
            drawCircle(TransitMain, radius, style = Stroke(6.dp.toPx()))
            rotate(angle) {
                val tip = Offset(center.x, center.y - radius * 0.7f)
                val tail = Offset(center.x + radius * 0.42f, center.y + radius * 0.32f)
                drawPath(Path().apply {
                    moveTo(tip.x, tip.y)
                    lineTo(center.x - radius * 0.42f, center.y + radius * 0.32f)
                    lineTo(center.x, center.y + radius * 0.15f)
                    close()
                }, TransitSelected)
                drawPath(Path().apply {
                    moveTo(tip.x, tip.y)
                    lineTo(tail.x, tail.y)
                    lineTo(center.x, center.y + radius * 0.15f)
                    close()
                }, TransitMain)
            }
        }
        listOf("N" to Alignment.TopCenter, "E" to Alignment.CenterEnd,
            "S" to Alignment.BottomCenter, "W" to Alignment.CenterStart).forEach { (label, alignment) ->
            Text(label, color = TransitComplementary, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(alignment).padding(14.dp))
        }
    }
}

