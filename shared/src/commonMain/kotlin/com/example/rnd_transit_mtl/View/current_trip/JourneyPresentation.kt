package com.example.rnd_transit_mtl.View.current_trip

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.rnd_transit_mtl.ui.theme.TransitWhite
import kotlin.math.ceil
import kotlin.math.roundToInt

// Layout settings — heading type size for arrival and return sheets.
private val JourneyHeadingFontSize = 20.sp

/**
 * Shared section heading announcing return/arrival state changes without moving keyboard focus.
 *
 * @param value Heading text displayed in white bold typography.
 */
@Composable
internal fun JourneyHeading(value: String) {
    Text(
        value,
        color = TransitWhite,
        fontSize = JourneyHeadingFontSize,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.semantics {
            heading()
            liveRegion = LiveRegionMode.Polite
        },
    )
}

/**
 * Formats kilometres rounded to one decimal place matching saved-trip screens.
 *
 * @param kilometres Distance in kilometres.
 * @return Formatted distance string such as `"2.5 km"`.
 */
internal fun journeyDistance(kilometres: Double): String = "${(kilometres * 10).roundToInt() / 10.0} km"

/**
 * Formats seconds as whole minutes rounded upward; zero remains `"0 min"`.
 *
 * @param seconds Elapsed or estimated duration in seconds.
 * @return Formatted minute string such as `"18 min"`.
 */
internal fun journeyMinutes(seconds: Long): String = "${ceil(seconds / 60.0).toLong()} min"
