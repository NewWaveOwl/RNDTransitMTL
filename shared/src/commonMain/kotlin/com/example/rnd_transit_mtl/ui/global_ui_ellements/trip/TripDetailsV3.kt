package com.example.rnd_transit_mtl.ui.global_ui_ellements.trip

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rnd_transit_mtl.data.assets.toSampleMapPoint
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.model.trip.TripStatus
import com.example.rnd_transit_mtl.model.trip.explorationWaypoints
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.LocalTransitMap
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.MockTripMap
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.TransitMap
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.TransitMapData
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.toSampleMapLegs
import com.example.rnd_transit_mtl.ui.theme.TransitComplementary
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitSelected
import com.example.rnd_transit_mtl.ui.theme.TransitWhite

// Layout settings — dp limits for the saved-trip details map badge and bottom sheet.
private val V3DetailsMaximumPanelHeight = 330.dp
private val V3DetailsMapTopInset = 56.dp
private val V3DetailsBadgeCornerRadius = 16.dp
private val V3DetailsBadgeInnerPadding = 8.dp
private val V3DetailsPanelSpacing = 12.dp

/**
 * Saved-trip presentation. Repeat creates a fresh proposal; Edit copy restores preferences without overwriting history.
 * A kept cancelled trip shows as Cancelled without a review section.
 *
 * @param trip Saved trip record to visualize and summarize.
 * @param completedLabel Formatted time the trip ended, by completion or cancellation.
 * @param onRepeat Called when the user requests a fresh trip using these settings.
 * @param onEditCopy Called when the user restores these settings into the planner.
 * @param modifier Configuration applied to the root layout.
 * @param enabled Whether map and action controls accept interaction.
 * @param errorMessage Optional error message shown above the action buttons.
 * @param onReview Opens the saved trip's review editor; null omits the action when navigation is unavailable.
 */
@Composable
fun TripDetailsV3(
    trip: Trip,
    completedLabel: String,
    onRepeat: () -> Unit,
    onEditCopy: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    errorMessage: String? = null,
    onReview: (() -> Unit)? = null,
) {
    val liveMap = LocalTransitMap.current != null
    Column(modifier.fillMaxSize().background(TransitWhite)) {
        TripDetailsMapSection(
            trip = trip,
            liveMap = liveMap,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        TripDetailsSummaryPanel(
            trip = trip,
            completedLabel = completedLabel,
            enabled = enabled,
            errorMessage = errorMessage,
            onReview = onReview,
            onRepeat = onRepeat,
            onEditCopy = onEditCopy,
        )
    }
}

/**
 * Renders the saved trip route map, badge overlay, and sample-map attribution when offline.
 *
 * @param trip Saved trip whose origin, destination, waypoints, and legs are drawn.
 * @param liveMap Whether a platform live map provider is active.
 * @param enabled Whether map gestures are enabled.
 * @param modifier Configuration applied to the map container.
 */
@Composable
private fun TripDetailsMapSection(
    trip: Trip,
    liveMap: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        TransitMap(
            data = TransitMapData(
                trip = trip,
                progress = 1f,
                interactive = enabled,
                topInset = V3DetailsMapTopInset,
            ),
            modifier = Modifier.fillMaxSize(),
        ) {
            MockTripMap(
                start = trip.origin.toSampleMapPoint(),
                destination = trip.destination.toSampleMapPoint(),
                waypoints = trip.explorationWaypoints.map { it.toSampleMapPoint() },
                legs = trip.toSampleMapLegs(),
                progress = 1f,
                interactive = enabled,
                matchPlannerBackground = true,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(V3SectionPadding),
            color = TransitWhite,
            shape = RoundedCornerShape(V3DetailsBadgeCornerRadius),
        ) {
            Text(
                "Saved trip · #${trip.id.value.takeLast(3)}",
                color = TransitMain,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(V3DetailsBadgeInnerPadding),
            )
        }

        if (!liveMap) {
            Text(
                "© OpenStreetMap contributors · Sample map",
                color = TransitComplementary,
                fontSize = 10.sp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .background(TransitWhite)
                    .padding(4.dp),
            )
        }
    }
}

/**
 * Displays the saved trip's metadata, review summary, and action buttons below the map.
 *
 * @param trip Saved trip record being inspected.
 * @param completedLabel Formatted completion or cancellation timestamp label.
 * @param enabled Whether buttons and review links accept interaction.
 * @param errorMessage Optional error message rendered above the actions.
 * @param onReview Optional callback to open or edit the trip's review.
 * @param onRepeat Called to repeat this trip as a fresh proposal.
 * @param onEditCopy Called to copy these settings into the planner.
 */
@Composable
private fun TripDetailsSummaryPanel(
    trip: Trip,
    completedLabel: String,
    enabled: Boolean,
    errorMessage: String?,
    onReview: (() -> Unit)?,
    onRepeat: () -> Unit,
    onEditCopy: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = V3DetailsMaximumPanelHeight)
            .background(TransitComplementary)
            .verticalScroll(rememberScrollState())
            .padding(V3SectionPadding),
        verticalArrangement = Arrangement.spacedBy(V3DetailsPanelSpacing),
    ) {
        Text(
            "${trip.title} · #${trip.id.value.takeLast(3)}",
            color = TransitWhite,
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold,
        )
        val cancelled = trip.status == TripStatus.Cancelled
        Text(
            "${if (cancelled) "Cancelled" else "Completed"} · $completedLabel",
            color = TransitSelected,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "${trip.plannedMinutes} min · ${v3DistanceText(trip)} · ${v3TransportText(trip)}",
            color = TransitWhite,
            fontSize = 18.sp,
        )

        val review = trip.review
        if (!cancelled) {
            TripReviewSummary(review = review, linksEnabled = enabled)
        }

        V3Error(errorMessage)
        if (onReview != null) {
            V3Button(
                label = if (review == null) "Review trip" else "Edit review",
                onClick = onReview,
                enabled = enabled,
            )
        }
        V3Button("Repeat as a new trip", onRepeat, enabled)
        V3Button("Edit a copy of these settings", onEditCopy, enabled, true)
        Text(
            "Repeat and edit a copy create new trips.",
            color = TransitWhite,
            fontSize = 12.sp,
        )
    }
}
