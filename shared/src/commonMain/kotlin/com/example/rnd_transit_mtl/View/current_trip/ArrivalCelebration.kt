package com.example.rnd_transit_mtl.View.current_trip

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.example.rnd_transit_mtl.ui.theme.TransitHighlight
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitSelected
import com.example.rnd_transit_mtl.ui.theme.TransitWhite

// Layout settings — dp height and particle count from the Figma arrival celebration spec.
private val CelebrationHeight = 250.dp
private const val CelebrationParticleCount = 28
private const val CelebrationReferenceWidth = 412f

/**
 * Draws Figma's 28 vector celebration particles along the shared arrival timeline without blocking input.
 *
 * @param progress Position from `0f` to `1f` on the two-second arrival timeline.
 * @param modifier Placement over the map viewport.
 */
@Composable
internal fun ArrivalCelebration(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    if (progress >= 0.8f) return
    Canvas(modifier.fillMaxWidth().height(CelebrationHeight).clipToBounds()) {
        val scale = size.width / CelebrationReferenceWidth
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
                drawRoundRect(
                    color = colors[index % colors.size].copy(alpha = opacity),
                    topLeft = Offset(x, y),
                    size = Size(width, height),
                    cornerRadius = CornerRadius(scale, scale),
                )
            }
        }
    }
}
