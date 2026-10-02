package com.example.rnd_transit_mtl.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/** Material color model that maps the transit palette to semantic theme roles. */
private val TransitColors = lightColorScheme(
    primary = TransitMain,
    onPrimary = TransitWhite,
    secondary = TransitHighlight,
    onSecondary = TransitWhite,
    tertiary = TransitSelected,
    onTertiary = TransitWhite,
    background = TransitMain,
    onBackground = TransitWhite,
    surface = TransitMain,
    onSurface = TransitWhite,
    surfaceVariant = TransitComplementary,
    onSurfaceVariant = TransitWhite,
    outline = TransitComplementary
)

/**
 * Applies the shared transit colors and LINE Seed JP typography.
 *
 * @param content Application or preview content that inherits the transit theme.
 */
@Composable
fun RNDTransitTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = TransitColors, typography = transitTypography(), content = content)
}
