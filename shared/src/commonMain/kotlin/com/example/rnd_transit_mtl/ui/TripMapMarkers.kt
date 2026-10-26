package com.example.rnd_transit_mtl.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import kotlin.math.PI
import kotlin.math.atan2

/** The supplied endpoint SVGs have their outer triangle tip at this anchor. */
internal fun DrawScope.drawAnchoredTripEndpoint(
    painter: Painter,
    position: Offset,
    markerSize: Float
) {
    translate(
        left = position.x - markerSize * (129.202f / 259f),
        top = position.y - markerSize * (223.784f / 259f)
    ) {
        with(painter) { draw(size = Size(markerSize, markerSize)) }
    }
}

/** The pointer's circle center stays on the route while its sharp tip turns. */
internal fun DrawScope.drawDirectedTripPointer(
    painter: Painter,
    position: Offset,
    markerSize: Float,
    rotationDegrees: Float
) {
    rotate(rotationDegrees, pivot = position) {
        translate(position.x - markerSize / 2f, position.y - markerSize / 2f) {
            with(painter) { draw(size = Size(markerSize, markerSize)) }
        }
    }
}

/** The SVG points up at zero degrees; screen Y increases downward. */
internal fun tripPointerRotation(start: Offset, destination: Offset): Float {
    val direction = destination - start
    if (direction == Offset.Zero) return 0f
    val degrees = (atan2(direction.y.toDouble(), direction.x.toDouble()) * 180.0 / PI).toFloat()
    return (degrees + 90f + 360f) % 360f
}
