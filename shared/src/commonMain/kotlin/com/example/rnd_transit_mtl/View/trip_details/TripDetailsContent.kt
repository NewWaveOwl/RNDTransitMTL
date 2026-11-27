package com.example.rnd_transit_mtl.View.trip_details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.rnd_transit_mtl.View.history.formatTripTimestampUtc
import com.example.rnd_transit_mtl.data.assets.toSampleMapPoint
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.model.trip.explorationWaypoints
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.MockTripMap
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.formatDemoDistanceKm
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.formatGeoPoint
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.toSampleMapLegs
import com.example.rnd_transit_mtl.ui.global_ui_ellements.trip.TripInformationPanel
import com.example.rnd_transit_mtl.ui.global_ui_ellements.trip.TripReviewSummary
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitWhite

// Layout settings — dp width limit, padding, map height, and action heights for stored trip details.
private val TripDetailsMaxContentWidth = 820.dp
private val TripDetailsContentPadding = 16.dp
private val TripDetailsSectionSpacing = 20.dp
private val TripDetailsSummaryCornerRadius = 20.dp
private val TripDetailsSummarySpacing = 10.dp
private val TripDetailsMapHeight = 300.dp
private val TripDetailsPrimaryButtonMinHeight = 52.dp
private val TripDetailsSecondaryButtonMinHeight = 48.dp

/**
 * Stateless stored-trip details view.
 * A null record shows recovery content; a completed record always uses progress `1f` on its map.
 *
 * @param trip Saved trip to display, or null when missing.
 * @param onReturnToHistory Navigates back to History.
 * @param onReview Optional callback opening the review editor.
 * @param onOpenImageReference Opens the trip's external reference image link.
 * @param modifier Parent layout constraints.
 * @param actionsEnabled Whether buttons and review links accept clicks.
 * @param errorMessage Optional error feedback displayed above the actions.
 */
@Composable
fun TripDetailsContent(
    trip: Trip?,
    onReturnToHistory: () -> Unit,
    onReview: (() -> Unit)?,
    onOpenImageReference: () -> Unit,
    modifier: Modifier = Modifier,
    actionsEnabled: Boolean = true,
    errorMessage: String? = null,
) {
    Box(
        modifier = modifier.fillMaxSize().background(TransitWhite),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = TripDetailsMaxContentWidth)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(TripDetailsContentPadding),
            verticalArrangement = Arrangement.spacedBy(TripDetailsSectionSpacing),
        ) {
            if (trip == null) {
                TripDetailsUnavailableBody(
                    actionsEnabled = actionsEnabled,
                    onReturnToHistory = onReturnToHistory,
                )
            } else {
                Text(text = trip.title, style = MaterialTheme.typography.headlineSmall, color = TransitMain)
                Text(
                    text = "${trip.origin.label} to ${trip.destination.label}",
                    style = MaterialTheme.typography.titleMedium,
                    color = TransitMain,
                )

                TripDetailsMetadataCard(trip)

                Text(text = "Completed mock route", style = MaterialTheme.typography.titleLarge, color = TransitMain)
                MockTripMap(
                    start = trip.origin.toSampleMapPoint(),
                    destination = trip.destination.toSampleMapPoint(),
                    waypoints = trip.explorationWaypoints.map { it.toSampleMapPoint() },
                    legs = trip.toSampleMapLegs(),
                    progress = 1f,
                    modifier = Modifier.fillMaxWidth().height(TripDetailsMapHeight),
                )
                Text(
                    text = "Start: ${formatGeoPoint(trip.origin.point)}\n" +
                        "Destination: ${formatGeoPoint(trip.destination.point)}\n" +
                        (if (trip.legs.all { it.isRouted }) "Drawn along the planned route. "
                        else "Dashed parts are straight lines, not a street route. ") +
                        "This map is static.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TransitMain,
                )

                TripInformationPanel(trip = trip, onOpenImageReference = onOpenImageReference)

                Text(text = "Saved review", style = MaterialTheme.typography.titleLarge, color = TransitMain)
                TripReviewSummary(review = trip.review, linksEnabled = actionsEnabled)

                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Button(
                    onClick = { onReview?.invoke() },
                    enabled = actionsEnabled && onReview != null,
                    modifier = Modifier.fillMaxWidth().heightIn(min = TripDetailsPrimaryButtonMinHeight),
                ) {
                    Text(if (trip.review == null) "Review trip" else "Edit review")
                }

                OutlinedButton(
                    onClick = onReturnToHistory,
                    enabled = actionsEnabled,
                    modifier = Modifier.fillMaxWidth().heightIn(min = TripDetailsSecondaryButtonMinHeight),
                ) {
                    Text("Return to History")
                }
            }
        }
    }
}

/** Recovery block shown when the requested trip record is no longer available. */
@Composable
private fun TripDetailsUnavailableBody(
    actionsEnabled: Boolean,
    onReturnToHistory: () -> Unit,
) {
    Text(text = "Trip unavailable", style = MaterialTheme.typography.headlineSmall, color = TransitMain)
    Text(
        text = "This completed trip was removed or is not available in the current session.",
        style = MaterialTheme.typography.bodyLarge,
        color = TransitMain,
    )
    Button(
        onClick = onReturnToHistory,
        enabled = actionsEnabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = TripDetailsSecondaryButtonMinHeight),
    ) {
        Text("Return to History")
    }
}

/** Highlighted blue metadata card showing distance, timestamps, and selectable trip reference ID. */
@Composable
private fun TripDetailsMetadataCard(trip: Trip) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = TransitMain, shape = RoundedCornerShape(TripDetailsSummaryCornerRadius))
            .padding(TripDetailsContentPadding),
        verticalArrangement = Arrangement.spacedBy(TripDetailsSummarySpacing),
    ) {
        Text(text = "Completed trip", style = MaterialTheme.typography.titleMedium, color = TransitWhite)
        Text(
            text = "${formatDemoDistanceKm(trip.distanceKm)} km total demo distance",
            style = MaterialTheme.typography.bodyLarge,
            color = TransitWhite,
        )
        Text(
            text = "Created: ${formatTripTimestampUtc(trip.createdAt)}",
            style = MaterialTheme.typography.bodyMedium,
            color = TransitWhite,
        )
        Text(
            text = "Completed: ${trip.completedAt?.let(::formatTripTimestampUtc) ?: "Completion information unavailable"}",
            style = MaterialTheme.typography.bodyMedium,
            color = TransitWhite,
        )
        SelectionContainer {
            Text(
                text = "Trip reference: ${trip.id}",
                style = MaterialTheme.typography.bodySmall,
                color = TransitWhite,
            )
        }
    }
}
