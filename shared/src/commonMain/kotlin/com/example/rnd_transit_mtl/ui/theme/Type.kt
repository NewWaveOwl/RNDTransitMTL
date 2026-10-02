package com.example.rnd_transit_mtl.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import rnd_transit_mtl.shared.generated.resources.Res
import rnd_transit_mtl.shared.generated.resources.line_seed_jp_regular
import rnd_transit_mtl.shared.generated.resources.line_seed_jp_bold

/**
 * Builds the bundled LINE Seed JP font family with normal, medium, and bold mappings.
 *
 * @return Font family backed by the shared Regular and Bold font resources.
 */
@Composable
private fun lineSeedJp() = FontFamily(
    Font(Res.font.line_seed_jp_regular, FontWeight.Normal),
    Font(Res.font.line_seed_jp_bold, FontWeight.Medium),
    Font(Res.font.line_seed_jp_bold, FontWeight.Bold)
)

/**
 * Applies LINE Seed JP to all standard and emphasized Material text styles.
 *
 * @return Material typography using the shared LINE Seed JP font family.
 */
@Composable
internal fun transitTypography(): Typography = Typography(fontFamily = lineSeedJp())
