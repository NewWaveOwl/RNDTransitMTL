package com.example.rnd_transit_mtl.View.compass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.ui.theme.TransitComplementary
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitSelected
import com.example.rnd_transit_mtl.ui.theme.TransitWhite

// Layout settings — dp padding, spacing, and maximum dial width for the compass presentation.
private val CompassBodyHorizontalPadding = 28.dp
private val CompassBodyVerticalPadding = 24.dp
private val CompassDialMaximumWidth = 420.dp
private val CompassControlsPadding = 20.dp
private val CompassActionRowSpacing = 12.dp

internal val LocalCompassControlsVisible = staticCompositionLocalOf { false }

/**
 * Direction-only compass presentation keeping destination names private.
 *
 * @param activeTrip Current active trip when available, or null when browsing without a trip.
 * @param directionDegrees Target bearing clockwise from north (`0` = N, `90` = E).
 * @param controlsVisible Whether the bottom action drawer and heading copy are revealed.
 * @param onOpenMap Called when the user switches back to the map screen.
 * @param onFinishTrip Optional callback to finish the active trip.
 * @param onOpenPresets Optional callback to open trip presets when no trip is active.
 * @param modifier Configuration applied to the root column.
 */
@Composable
fun CompassScreen(
    activeTrip: Trip? = null,
    directionDegrees: Float = 45f,
    controlsVisible: Boolean = LocalCompassControlsVisible.current,
    onOpenMap: () -> Unit = {},
    onFinishTrip: (() -> Unit)? = null,
    onOpenPresets: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(TransitComplementary).safeDrawingPadding()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = CompassBodyHorizontalPadding, vertical = CompassBodyVerticalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.alpha(if (controlsVisible) 1f else 0f),
            ) {
                Text(
                    if (activeTrip != null) "Find your next direction" else "Follow your curiosity",
                    color = TransitWhite,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (activeTrip != null) "Trip · Mystery destination" else "Compass · no active trip",
                    color = TransitWhite,
                    fontSize = 14.sp,
                )
            }
            Box(
                Modifier.weight(1f).fillMaxWidth().padding(vertical = CompassBodyVerticalPadding),
                contentAlignment = Alignment.Center,
            ) {
                CompassDial(
                    directionDegrees = directionDegrees,
                    modifier = Modifier
                        .widthIn(max = CompassDialMaximumWidth)
                        .aspectRatio(1f)
                        .alpha(if (activeTrip == null && controlsVisible) 0.6f else 1f),
                )
            }
            Text(
                "Hold the screen to reveal controls",
                color = TransitWhite.copy(alpha = 0.65f),
                fontSize = 12.sp,
            )
        }
        if (controlsVisible) {
            Column(Modifier.fillMaxWidth().background(TransitMain).padding(CompassControlsPadding)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (activeTrip != null) "Current trip" else "Plan your next trip",
                            color = TransitWhite,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            if (activeTrip != null) "Mystery destination" else "Set your trip preferences on the map.",
                            color = TransitWhite,
                            fontSize = 12.sp,
                        )
                    }
                    Text(
                        "GO",
                        color = TransitWhite,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(
                                Brush.horizontalGradient(listOf(TransitMain, TransitSelected)),
                                RoundedCornerShape(14.dp),
                            )
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(CompassActionRowSpacing)) {
                    CompassAction(
                        label = if (activeTrip != null) "Back to map" else "Open map",
                        action = onOpenMap,
                        modifier = Modifier.weight(1f),
                    )
                    CompassAction(
                        label = if (activeTrip != null) "Finish trip" else "Trip presets",
                        action = if (activeTrip != null) onFinishTrip else onOpenPresets,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** White pill action button shown inside the revealed compass footer. */
@Composable
private fun CompassAction(
    label: String,
    action: (() -> Unit)?,
    modifier: Modifier,
) {
    Button(
        onClick = { action?.invoke() },
        enabled = action != null,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = TransitWhite,
            contentColor = TransitMain,
            disabledContainerColor = TransitWhite,
            disabledContentColor = TransitMain.copy(alpha = 0.5f),
        ),
    ) {
        Text(label, fontSize = 12.sp)
    }
}
