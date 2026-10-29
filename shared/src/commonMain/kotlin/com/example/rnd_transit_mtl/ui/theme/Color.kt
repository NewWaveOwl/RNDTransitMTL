package com.example.rnd_transit_mtl.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** Main colour for the header, panels, controls, and unfilled progress track. */
val TransitMain = Color(0xFF0076B3)

/** Gold highlight colour for route panels and intensity accents. */
val TransitHighlight = Color(0xFFFFBA00)

/** White used for text, icons, and control dividers. */
val TransitWhite = Color(0xFFF8F8F8)

/** Green used for selected choices, progress, and the GO gradient. */
val TransitSelected = Color(0xFF98CA45)

/** Lighter green endpoint for an open choice's gradient, derived from the existing palette. */
val TransitSelectionGradientEnd = lerp(TransitSelected, TransitWhite, 0.35f)

/** Pale-centre selection chip stops from Figma 242:2210, for an open but unchosen mode. */
val TransitOpenChoiceGradientStops = arrayOf(
    0f to TransitWhite,
    0.40384614f to Color(0xFFF0F4E9),
    0.85096157f to Color(0xFFC0DD8F),
    1f to Color(0xFFAAD367)
)

/** Teal colour for text, outlines, and secondary accents. */
val TransitComplementary = Color(0xFF28454C)

/** Orange used for the mock route's start, moving marker, and completed segment. */
val TransitOrange = Color(0xFFFF8B00)
