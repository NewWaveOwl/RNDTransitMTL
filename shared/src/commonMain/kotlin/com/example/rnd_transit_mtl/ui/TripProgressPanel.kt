package com.example.rnd_transit_mtl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.example.rnd_transit_mtl.ui.theme.TransitHighlight
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitSelected
import com.example.rnd_transit_mtl.ui.theme.TransitWhite

/**
 * Stateless status card and yellow progress footer.
 *
 * The compact card stays within the footer so small-window scrolling
 * cannot clip its status text.
 *
 * Remaining distance and track fill use the same supplied progress.
 * No timer or independent animation is created here.
 */
@Composable
fun TripProgressPanel(
    tripTitle: String,
    destinationLabel: String,
    distanceKm: Double,
    progress: Float,
    modifier: Modifier = Modifier,
    statusText: String? = null,
    onCompletedClick: (() -> Unit)? = null,
    actionsEnabled: Boolean = true
) {
    require(tripTitle.isNotBlank()) {
        "Trip title must not be blank."
    }
    require(destinationLabel.isNotBlank()) {
        "Destination label must not be blank."
    }
    require(distanceKm.isFinite() && distanceKm > 0.0) {
        "Total demo distance must be finite and positive."
    }

    val boundedProgress = boundedTripProgress(progress)
    val percentage = tripProgressPercentage(boundedProgress)
    val canOpenReview = boundedProgress == 1f &&
        actionsEnabled && onCompletedClick != null
    val remainingDistanceKm =
        distanceKm * (1f - boundedProgress).toDouble()

    val formattedRemaining =
        formatDemoDistanceKm(remainingDistanceKm)

    val remainingText = if (
        boundedProgress < 1f &&
        remainingDistanceKm > 0.0 &&
        formattedRemaining == "0.00"
    ) {
        "<0.01 km"
    } else {
        "$formattedRemaining km"
    }

    val status = statusText ?: when (boundedProgress) {
        0f -> "Ready at the start"
        1f -> "Demo destination reached"
        else -> "Following the mock route"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(TransitHighlight)
            .padding(top = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .background(
                    color = TransitMain,
                    shape = RoundedCornerShape(22.dp)
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = status,
                style = MaterialTheme.typography.bodyLarge,
                color = TransitWhite
            )

            Text(
                text = "$remainingText demo distance remaining",
                style = MaterialTheme.typography.titleMedium,
                color = TransitWhite
            )


        }

        Box(
            modifier = Modifier
                .padding(
                    start = 24.dp,
                    end = 24.dp,
                    top = 12.dp,
                    bottom = 20.dp
                )
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(TransitMain)
                .then(
                    if (canOpenReview) {
                        Modifier.clickable(
                            onClickLabel = "Rate your trip",
                            onClick = { onCompletedClick?.invoke() }
                        )
                    } else {
                        Modifier
                    }
                )
                .semantics {
                    contentDescription =
                        "Demo progress for $tripTitle"
                    stateDescription = "$percentage percent"
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = boundedProgress,
                        range = 0f..1f
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(boundedProgress)
                    .fillMaxHeight()
                    .background(TransitSelected)
            )

            /*
             * A small teal backing keeps the percentage readable
             * across both colors of the progress track.
             */
            Text(
                text = if (boundedProgress == 1f && onCompletedClick != null) {
                    "100% · Rate your trip"
                } else {
                    "$percentage%"
                },
                modifier = Modifier
                    .background(
                        color = TransitMain,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                style = MaterialTheme.typography.titleMedium,
                color = TransitWhite
            )
        }
    }
}
