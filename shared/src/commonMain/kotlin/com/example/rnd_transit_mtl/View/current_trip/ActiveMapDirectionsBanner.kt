package com.example.rnd_transit_mtl.View.current_trip

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitWhite
import com.example.rnd_transit_mtl.ui.theme.tripLegColor
import org.jetbrains.compose.resources.painterResource
import rnd_transit_mtl.shared.generated.resources.Res
import rnd_transit_mtl.shared.generated.resources.rnd_turn_left

// Layout settings — dp padding, spacing, and icon sizes for the expandable directions banner.
private val DirectionsBannerTopPadding = 16.dp
private val DirectionsBannerHorizontalPadding = 24.dp
private val DirectionsBannerVerticalPadding = 16.dp
private val DirectionsBannerLegIndicatorSize = 12.dp
private val DirectionsBannerInstructionIconSize = 32.dp

// Animation settings — milliseconds; details grow down from the compact hint and retract upwards.
private const val ACTIVE_TRIP_DIRECTIONS_ANIMATION_DURATION_MILLIS = 280
private const val ACTIVE_TRIP_DIRECTIONS_FADE_DURATION_MILLIS = 160

/**
 * Expandable blue street-guidance banner displayed at the top of an active trip map.
 *
 * @param legIndex Zero-based index of the active trip leg, used for the colour dot.
 * @param compactInstruction Single-line street hint visible in both collapsed and expanded states.
 * @param currentInstruction Full section heading, current action, and optional next manoeuvre.
 * @param expanded Whether the detailed guidance body is currently expanded.
 * @param actionsEnabled Whether tapping the banner toggles expansion.
 * @param onToggleExpanded Called when the user taps the banner to expand or collapse details.
 * @param modifier Configuration applied to the banner row.
 */
@Composable
internal fun ActiveMapDirectionsBanner(
    legIndex: Int,
    compactInstruction: String,
    currentInstruction: ActiveMapInstruction,
    expanded: Boolean,
    actionsEnabled: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .padding(top = DirectionsBannerTopPadding)
            .fillMaxWidth()
            .background(TransitMain)
            .clickable(
                enabled = actionsEnabled,
                role = Role.Button,
                onClickLabel = if (expanded) "Collapse directions" else "Expand directions",
                onClick = onToggleExpanded,
            )
            .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
            .padding(
                horizontal = DirectionsBannerHorizontalPadding,
                vertical = DirectionsBannerVerticalPadding,
            ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .padding(top = 6.dp)
                .size(DirectionsBannerLegIndicatorSize)
                .background(tripLegColor(legIndex), RoundedCornerShape(50))
                .border(1.dp, TransitWhite, RoundedCornerShape(50)),
        )
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                compactInstruction,
                color = TransitWhite,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
            )
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    tween(ACTIVE_TRIP_DIRECTIONS_ANIMATION_DURATION_MILLIS, easing = FastOutSlowInEasing),
                    expandFrom = Alignment.Top,
                ) + fadeIn(tween(ACTIVE_TRIP_DIRECTIONS_FADE_DURATION_MILLIS)),
                exit = shrinkVertically(
                    tween(ACTIVE_TRIP_DIRECTIONS_ANIMATION_DURATION_MILLIS, easing = FastOutSlowInEasing),
                    shrinkTowards = Alignment.Top,
                ) + fadeOut(tween(ACTIVE_TRIP_DIRECTIONS_FADE_DURATION_MILLIS)),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (currentInstruction.showLeftTurnIcon) {
                            Image(
                                painterResource(Res.drawable.rnd_turn_left),
                                contentDescription = null,
                                modifier = Modifier.size(DirectionsBannerInstructionIconSize),
                            )
                        }
                        Text(
                            currentInstruction.title,
                            color = TransitWhite,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text("Now: ${currentInstruction.detail}", color = TransitWhite, fontSize = 13.sp)
                    currentInstruction.nextAction?.let { next ->
                        Text(
                            "Next: $next",
                            color = TransitWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}
