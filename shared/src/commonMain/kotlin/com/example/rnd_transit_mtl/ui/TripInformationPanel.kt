package com.example.rnd_transit_mtl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.rnd_transit_mtl.model.Trip
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitWhite
import kotlin.math.roundToInt

/**
 * Displays the stored planner snapshot and its separate reference image.
 *
 * Image loading does not control or replace the bundled mock map.
 * Opening the original link remains delegated to the screen coordinator.
 */
@Composable
fun TripInformationPanel(
    trip: Trip,
    onOpenImageReference: () -> Unit,
    modifier: Modifier = Modifier
) {
    val transportsText = trip.selectedTransports.joinToString {
        it.label
    }

    val routesText = if (trip.selectedRoutes.isEmpty()) {
        "No routes selected."
    } else {
        trip.selectedRoutes.joinToString(separator = "\n") { route ->
            val transportLabel = trip.selectedTransports
                .firstOrNull { it.id == route.transportTypeId }
                ?.label

            if (transportLabel == null) {
                route.label
            } else {
                "$transportLabel: ${route.label}"
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = TransitWhite,
                shape = RoundedCornerShape(20.dp)
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Trip information",
            style = MaterialTheme.typography.titleLarge,
            color = TransitMain
        )

        Text(
            text = trip.description,
            style = MaterialTheme.typography.bodyLarge,
            color = TransitMain
        )

        Text(
            text = "Planned time: ${trip.plannedMinutes} minutes",
            style = MaterialTheme.typography.bodyLarge,
            color = TransitMain
        )

        Text(
            text = "Transport: $transportsText",
            style = MaterialTheme.typography.bodyLarge,
            color = TransitMain
        )

        Text(
            text = "Selected routes\n$routesText",
            style = MaterialTheme.typography.bodyLarge,
            color = TransitMain
        )

        Text(
            text = "Attraction intensity: " +
                    "${trip.attractionIntensity.roundToInt()} / 100",
            style = MaterialTheme.typography.bodyLarge,
            color = TransitMain
        )

        // Older saved trips can retain a separate image reference.
        if (trip.imageUrl.isNotBlank()) {
            Text(
                text = "Reference image",
                style = MaterialTheme.typography.titleMedium,
                color = TransitMain
            )

            TripReferenceImage(
                imageUrl = trip.imageUrl,
                tripTitle = trip.title
            )

            SelectionContainer {
                Text(
                    text = trip.imageUrl,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TransitMain
                )
            }

            OutlinedButton(
                onClick = onOpenImageReference,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Open image reference",
                    color = TransitMain
                )
            }
        }

        Text(
            text = "The bundled map shows a straight-line demo route. " +
                    "Its distance is simulated. The animation lasts " +
                    "10 seconds of active time, independently of your " +
                    "planned minutes.",
            style = MaterialTheme.typography.bodySmall,
            color = TransitMain
        )
    }
}
