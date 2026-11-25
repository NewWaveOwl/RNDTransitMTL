package com.example.rnd_transit_mtl.View.current_trip

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitWhite
import kotlin.math.ceil
import kotlin.math.roundToInt

// Layout settings — dp minimum height and corner radius shared across arrival and return sheets.
private val JourneyActionHeight = 52.dp
private val JourneyActionCorner = 24.dp

/**
 * Primary or secondary Figma action button used by arrival, return preview, and retry sheets.
 *
 * @param label Visible button text.
 * @param onClick Called when the button is tapped.
 * @param enabled Whether the button accepts clicks.
 * @param secondary When true, renders the outlined white secondary variant.
 * @param modifier Configuration applied to the button.
 */
@Composable
internal fun JourneyButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    secondary: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = JourneyActionHeight),
        enabled = enabled,
        shape = RoundedCornerShape(JourneyActionCorner),
        border = if (secondary) BorderStroke(1.dp, TransitMain) else null,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (secondary) TransitWhite else TransitMain,
            contentColor = if (secondary) TransitMain else TransitWhite,
        ),
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

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
        fontSize = 20.sp,
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
