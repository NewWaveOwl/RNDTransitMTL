package com.example.rnd_transit_mtl.View.compass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.rnd_transit_mtl.ui.theme.TransitComplementary
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitSelected
import com.example.rnd_transit_mtl.ui.theme.TransitWhite

// Layout settings — dp ring geometry and cardinal label inset for the compass dial.
private val CompassDialOuterInset = 4.dp
private val CompassDialRingStrokeWidth = 6.dp
private val CompassDialCardinalPadding = 14.dp

/**
 * Vector compass dial whose needle rotates independently of the stationary cardinal labels.
 *
 * @param directionDegrees Target bearing in degrees clockwise from north.
 * @param modifier Size and placement constraints for the square dial container.
 */
@Composable
fun CompassDial(
    directionDegrees: Float,
    modifier: Modifier = Modifier,
) {
    val angle = if (directionDegrees.isFinite()) ((directionDegrees % 360f) + 360f) % 360f else 0f
    Box(
        modifier.semantics {
            contentDescription = "Compass direction ${angle.toInt()} degrees clockwise from north"
        },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f - CompassDialOuterInset.toPx()
            drawCircle(TransitWhite, radius)
            drawCircle(TransitMain, radius, style = Stroke(CompassDialRingStrokeWidth.toPx()))
            rotate(angle) {
                val tip = Offset(center.x, center.y - radius * 0.7f)
                val tail = Offset(center.x + radius * 0.42f, center.y + radius * 0.32f)
                drawPath(
                    Path().apply {
                        moveTo(tip.x, tip.y)
                        lineTo(center.x - radius * 0.42f, center.y + radius * 0.32f)
                        lineTo(center.x, center.y + radius * 0.15f)
                        close()
                    },
                    TransitSelected,
                )
                drawPath(
                    Path().apply {
                        moveTo(tip.x, tip.y)
                        lineTo(tail.x, tail.y)
                        lineTo(center.x, center.y + radius * 0.15f)
                        close()
                    },
                    TransitMain,
                )
            }
        }
        listOf(
            "N" to Alignment.TopCenter,
            "E" to Alignment.CenterEnd,
            "S" to Alignment.BottomCenter,
            "W" to Alignment.CenterStart,
        ).forEach { (label, alignment) ->
            Text(
                label,
                color = TransitComplementary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(alignment).padding(CompassDialCardinalPadding),
            )
        }
    }
}
