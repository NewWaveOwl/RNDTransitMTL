package com.example.rnd_transit_mtl.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import rnd_transit_mtl.shared.generated.resources.Res
import rnd_transit_mtl.shared.generated.resources.line_seed_jp_regular
import rnd_transit_mtl.shared.generated.resources.line_seed_jp_bold

/** LINE Seed JP font model with normal, medium, and bold weight mappings. */
@Composable
private fun lineSeedJp() = FontFamily(
    Font(Res.font.line_seed_jp_regular, FontWeight.Normal),
    Font(Res.font.line_seed_jp_bold, FontWeight.Medium),
    Font(Res.font.line_seed_jp_bold, FontWeight.Bold)
)

/** Baseline Material typography whose sizing and spacing values are retained. */
private val DefaultTypography = Typography()

/** App typography model that applies LINE Seed JP to every Material text style. */
@Composable
internal fun transitTypography(): Typography {
    val fontFamily = lineSeedJp()
    return Typography(
        displayLarge = DefaultTypography.displayLarge.copy(fontFamily = fontFamily),
        displayMedium = DefaultTypography.displayMedium.copy(fontFamily = fontFamily),
        displaySmall = DefaultTypography.displaySmall.copy(fontFamily = fontFamily),
        headlineLarge = DefaultTypography.headlineLarge.copy(fontFamily = fontFamily),
        headlineMedium = DefaultTypography.headlineMedium.copy(fontFamily = fontFamily),
        headlineSmall = DefaultTypography.headlineSmall.copy(fontFamily = fontFamily),
        titleLarge = DefaultTypography.titleLarge.copy(fontFamily = fontFamily),
        titleMedium = DefaultTypography.titleMedium.copy(fontFamily = fontFamily),
        titleSmall = DefaultTypography.titleSmall.copy(fontFamily = fontFamily),
        bodyLarge = DefaultTypography.bodyLarge.copy(fontFamily = fontFamily),
        bodyMedium = DefaultTypography.bodyMedium.copy(fontFamily = fontFamily),
        bodySmall = DefaultTypography.bodySmall.copy(fontFamily = fontFamily),
        labelLarge = DefaultTypography.labelLarge.copy(fontFamily = fontFamily),
        labelMedium = DefaultTypography.labelMedium.copy(fontFamily = fontFamily),
        labelSmall = DefaultTypography.labelSmall.copy(fontFamily = fontFamily)
    )
}
