package com.example.rnd_transit_mtl.View.main.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitSelected
import com.example.rnd_transit_mtl.ui.theme.TransitWhite

// Layout settings — dp values scaled by layoutScale except existing unscaled action/divider widths.
// Minutes wheel tuning lives in ScrollableMinutes.kt.
/** Compact GO height, also used by the planner to position GO above the sheet. */
internal val GoBoxCompactHeight = 76.dp
private val GoBoxStandardHeight = 96.dp
private val GoBoxContentHorizontalPadding = 8.dp
private val GoBoxActionWidth = 60.dp
private val GoBoxResumeActionWidth = 84.dp
private val GoBoxDividerWidth = 1.dp
private val GoBoxCompactDividerHeight = 48.dp
private val GoBoxStandardDividerHeight = 58.dp
private val GoBoxCornerRadius = 28.dp

/**
 * Stateless planned-minutes selector and primary planner action.
 *
 * The parent decides whether the action starts, resumes, or opens a completed trip.
 * Selected minutes do not determine the ten-second simulation duration.
 *
 * @param minutes Current planned trip duration in minutes.
 * @param onMinutesChange Called with the updated planned duration in minutes.
 * @param onGo Called when the user taps the GO / Resume action area.
 * @param modifier Parent layout constraints.
 * @param layoutScale Responsive layout multiplier.
 * @param actionLabel Label displayed in the right-hand action slot.
 * @param actionEnabled Whether the primary action accepts clicks.
 * @param minutesEnabled Whether the minutes selector accepts taps and drags.
 * @param compact Uses the Figma planner's 76 dp height and two separators instead of the legacy layout.
 */
@Composable
internal fun GOBox(
    minutes: Int,
    onMinutesChange: (Int) -> Unit,
    onGo: () -> Unit,
    modifier: Modifier = Modifier,
    layoutScale: Float = 1f,
    actionLabel: String = "GO",
    actionEnabled: Boolean = true,
    minutesEnabled: Boolean = true,
    compact: Boolean = false,
) {
    Row(
        modifier = modifier
            .height((if (compact) GoBoxCompactHeight else GoBoxStandardHeight) * layoutScale)
            .background(
                Brush.horizontalGradient(
                    0f to TransitMain,
                    0.84f to TransitMain,
                    1f to TransitSelected,
                ),
                RoundedCornerShape(GoBoxCornerRadius * layoutScale),
            )
            .padding(horizontal = GoBoxContentHorizontalPadding * layoutScale),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "I have",
            color = TransitWhite,
            fontSize = 20.sp * layoutScale,
            maxLines = 1,
            softWrap = false,
        )
        ControlDivider(layoutScale, compact)
        ScrollableMinutes(minutes, onMinutesChange, minutesEnabled, layoutScale)
        if (!compact) ControlDivider(layoutScale, compact)
        Text(
            "minutes",
            color = TransitWhite,
            fontSize = 20.sp * layoutScale,
            maxLines = 1,
            softWrap = false,
        )
        ControlDivider(layoutScale, compact)
        Box(
            modifier = Modifier
                .width(if (actionLabel == "Resume trip") GoBoxResumeActionWidth else GoBoxActionWidth)
                .fillMaxHeight()
                .clickable(enabled = actionEnabled, onClick = onGo)
                .semantics { contentDescription = actionLabel },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = actionLabel,
                fontWeight = FontWeight.Bold,
                color = TransitWhite.copy(alpha = if (actionEnabled) 1f else 0.5f),
                fontSize = (if (actionLabel == "Resume trip") 16.sp else 22.sp) * layoutScale,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

/** Vertical white divider separating GOBox control segments. */
@Composable
private fun ControlDivider(layoutScale: Float, compact: Boolean) {
    Spacer(
        Modifier
            .width(GoBoxDividerWidth)
            .height((if (compact) GoBoxCompactDividerHeight else GoBoxStandardDividerHeight) * layoutScale)
            .background(TransitWhite.copy(alpha = 0.8f)),
    )
}
