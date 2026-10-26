package com.example.rnd_transit_mtl.ui

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
import com.example.rnd_transit_mtl.model.Trip
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitWhite

/**
 * Stateless stored-trip details.
 *
 * A null record shows recovery content.
 * A completed record always uses progress 1 on its original mock map.
 * This component has no timer, generator, or store dependency.
 */
@Composable
fun TripDetailsContent(
    trip: Trip?,
    onReturnToHistory: () -> Unit,
    onReview: (() -> Unit)?,
    onOpenImageReference: () -> Unit,
    modifier: Modifier = Modifier,
    actionsEnabled: Boolean = true,
    errorMessage: String? = null
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TransitWhite),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 820.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            if (trip == null) {
                Text(
                    text = "Trip unavailable",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TransitMain
                )

                Text(
                    text = "This completed trip was removed or is not " +
                        "available in the current session.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TransitMain
                )

                Button(
                    onClick = onReturnToHistory,
                    enabled = actionsEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text("Return to History")
                }
            } else {
                Text(
                    text = trip.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = TransitMain
                )

                Text(
                    text = "${trip.start.label} to ${trip.destination.label}",
                    style = MaterialTheme.typography.titleMedium,
                    color = TransitMain
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = TransitMain,
                            shape = RoundedCornerShape(20.dp)
                        )
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Completed trip",
                        style = MaterialTheme.typography.titleMedium,
                        color = TransitWhite
                    )

                    Text(
                        text = "${formatDemoDistanceKm(trip.distanceKm)} " +
                            "km total demo distance",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TransitWhite
                    )

                    Text(
                        text = "Created: " +
                            formatTripTimestampUtc(trip.createdAtEpochMillis),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TransitWhite
                    )

                    Text(
                        text = "Completed: " +
                            (
                                trip.completedAtEpochMillis
                                    ?.let(::formatTripTimestampUtc)
                                    ?: "Completion information unavailable"
                                ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TransitWhite
                    )

                    SelectionContainer {
                        Text(
                            text = "Trip reference: ${trip.id}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TransitWhite
                        )
                    }
                }

                Text(
                    text = "Completed mock route",
                    style = MaterialTheme.typography.titleLarge,
                    color = TransitMain
                )

                MockTripMap(
                    start = trip.start,
                    destination = trip.destination,
                    progress = 1f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                )

                Text(
                    text = "Start map point: " +
                        "(${trip.start.x}, ${trip.start.y})\n" +
                        "Destination map point: " +
                        "(${trip.destination.x}, ${trip.destination.y})\n" +
                        "These are normalized mock-image positions, " +
                        "not geographic coordinates. This map is static.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TransitMain
                )

                TripInformationPanel(
                    trip = trip,
                    onOpenImageReference = onOpenImageReference
                )

                Text(
                    text = "Saved review",
                    style = MaterialTheme.typography.titleLarge,
                    color = TransitMain
                )

                TripReviewSummary(review = trip.review, linksEnabled = actionsEnabled)

                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Button(
                    onClick = { onReview?.invoke() },
                    enabled = actionsEnabled && onReview != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                ) {
                    Text(
                        if (trip.review == null) {
                            "Review trip"
                        } else {
                            "Edit review"
                        }
                    )
                }

                OutlinedButton(
                    onClick = onReturnToHistory,
                    enabled = actionsEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text("Return to History")
                }
            }
        }
    }
}
