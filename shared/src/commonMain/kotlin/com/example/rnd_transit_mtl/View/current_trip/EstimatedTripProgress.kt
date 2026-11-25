package com.example.rnd_transit_mtl.View.current_trip

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitWhite
import kotlin.math.ceil
import kotlin.math.roundToInt

// Layout settings — dp dimensions for the estimated progress bar and percentage label.
private val EstimatedProgressBarHeight = 64.dp
private val EstimatedProgressCornerRadius = 24.dp
private val EstimatedProgressPercentageWidth = 64.dp
private val EstimatedProgressPercentageInset = 8.dp
private val EstimatedProgressBarTopPadding = 6.dp

/**
 * Remaining time, distance, and visual progress bar derived from the trip's progress fraction.
 *
 * @param trip Trip duration, distance, and selected modes.
 * @param fraction Bounded progress from 0 to 1.
 * @param modifier Configuration applied to the progress section container.
 */
@Composable
internal fun EstimatedTripProgress(
    trip: Trip,
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    val remaining = 1f - fraction
    val minutes = ceil(trip.plannedMinutes * remaining).toInt()
    val distance = (trip.distanceKm * remaining * 10).roundToInt() / 10.0
    val percentage = (fraction * 100).roundToInt()

    Column(modifier = modifier) {
        Text(
            if (fraction >= 1f) "Destination reached" else "$minutes min remaining",
            color = TransitWhite,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
        )

        Text(
            "$distance km · " + trip.selectedTransports.joinToString(" + ") { it.label },
            color = TransitWhite,
            fontSize = 14.sp,
        )

        Text(
            "Estimated progress",
            color = TransitWhite,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )

        BoxWithConstraints(
            Modifier
                .padding(top = EstimatedProgressBarTopPadding)
                .fillMaxWidth()
                .height(EstimatedProgressBarHeight)
                .clip(RoundedCornerShape(EstimatedProgressCornerRadius))
                .background(TransitWhite)
                .semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                },
        ) {
            if (fraction > 0f) {
                Box(
                    Modifier
                        .width(maxWidth * fraction)
                        .fillMaxHeight()
                        .background(
                            TransitMain,
                            RoundedCornerShape(EstimatedProgressCornerRadius),
                        ),
                )
            }

            val fillWidth = maxWidth * fraction
            val labelWidth = EstimatedProgressPercentageWidth.coerceAtMost(maxWidth)
            val insideFill = fillWidth >= labelWidth + EstimatedProgressPercentageInset * 2
            val labelOffset = (
                if (insideFill) fillWidth - labelWidth - EstimatedProgressPercentageInset
                else fillWidth + EstimatedProgressPercentageInset
            ).coerceIn(0.dp, (maxWidth - labelWidth).coerceAtLeast(0.dp))

            // Plain percentage inside the blue fill; at low values use blue text on the white track.
            Text(
                "$percentage%",
                color = if (insideFill) TransitWhite else TransitMain,
                fontSize = 24.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = labelOffset)
                    .width(labelWidth),
            )
        }
    }
}
