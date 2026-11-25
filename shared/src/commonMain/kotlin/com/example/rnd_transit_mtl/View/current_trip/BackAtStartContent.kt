package com.example.rnd_transit_mtl.View.current_trip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rnd_transit_mtl.View.main.TripLabelValueField
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.ui.theme.TransitWhite

// Layout settings — dp dimensions for the Figma back-at-start completion sheet.
private val BackAtStartPanelHeight = 192.dp
private val BackAtStartFieldMaxWidth = 620.dp
private val BackAtStartFieldPadding = 16.dp
private val BackAtStartSkipButtonWidth = 116.dp
private val BackAtStartActionSpacing = 12.dp

/**
 * Figma completion sheet displayed once the traveller returns to their stored starting point.
 * Review and skip remain deliberate user actions.
 *
 * @param trip Finished return or round-trip record.
 * @param onReview Opens the review flow for the original adventure.
 * @param onSkip Skips review and returns to planning.
 * @param enabled Whether actions and sheet gestures accept input.
 * @param modifier Parent layout constraints.
 * @param errorMessage Optional coordinator feedback shown above the actions.
 */
@Composable
internal fun BackAtStartContent(
    trip: Trip,
    onReview: () -> Unit,
    onSkip: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    errorMessage: String? = null,
) {
    val reference = (trip.journey?.outboundTripId ?: trip.id).value.takeLast(3)
    TripJourneySheet(
        trip = trip,
        enabled = enabled,
        modifier = modifier,
        panelHeight = BackAtStartPanelHeight,
        collapsedContent = { JourneyHeading("You're back at the start!") },
        overlay = {
            TripLabelValueField(
                label = "Starting point",
                value = "You're back · Trip #$reference finished",
                showLocationIcon = true,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(BackAtStartFieldPadding)
                    .widthIn(max = BackAtStartFieldMaxWidth),
            )
        },
    ) {
        JourneyHeading("You're back at the start!")
        Text(
            "Trip finished and saved. Would you like to review your adventure?",
            color = TransitWhite,
            fontSize = 14.sp,
        )
        if (!errorMessage.isNullOrBlank()) {
            Text(
                errorMessage,
                color = TransitWhite,
                fontSize = 12.sp,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(BackAtStartActionSpacing)) {
            JourneyButton("Trip Review", onReview, enabled, modifier = Modifier.weight(1f))
            JourneyButton(
                "Skip",
                onSkip,
                enabled,
                secondary = true,
                modifier = Modifier.width(BackAtStartSkipButtonWidth),
            )
        }
    }
}
