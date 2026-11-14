package com.example.rnd_transit_mtl.ui.theme

import androidx.compose.ui.graphics.Color

// Route colours stay stable across the live map, bundled map, and trip information.
private val TripLegColors = listOf(
    Color(0xFF0072B2), Color(0xFFD55E00), Color(0xFF009E73), Color(0xFFAA4499),
    Color(0xFF827000), Color(0xFF5648CF), Color(0xFFB83C55), Color(0xFF007F86)
)

/** Each travel-order leg has its own colour, including consecutive legs of the same mode.
 * @param legIndex Zero-based index in travel order.
 */
fun tripLegColor(legIndex: Int): Color {
    require(legIndex >= 0)
    return TripLegColors.getOrNull(legIndex)
        ?: Color.hsl((legIndex * 137.508f) % 360f, 0.65f, 0.38f)
}
