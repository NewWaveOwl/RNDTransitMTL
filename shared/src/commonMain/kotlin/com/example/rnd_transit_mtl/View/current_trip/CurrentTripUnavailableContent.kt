package com.example.rnd_transit_mtl.View.current_trip

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitWhite

// Layout settings — dp padding and vertical spacing for the missing-trip recovery view.
private val UnavailableTripPadding = 24.dp
private val UnavailableTripSpacing = 16.dp

/**
 * Recovery presentation displayed when a navigated trip ID is no longer present in [TripsStore].
 *
 * @param enabled Whether the return-to-planner action accepts clicks.
 * @param onReturnToPlanner Called when the user returns to the trip planner.
 * @param modifier Configuration applied to the root column.
 */
@Composable
internal fun CurrentTripUnavailableContent(
    enabled: Boolean,
    onReturnToPlanner: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TransitWhite)
            .padding(UnavailableTripPadding),
        verticalArrangement = Arrangement.spacedBy(UnavailableTripSpacing),
    ) {
        Text(
            text = "Trip unavailable",
            style = MaterialTheme.typography.headlineSmall,
            color = TransitMain,
        )

        Text(
            text = "This trip was cancelled, removed, or was not " +
                "restored in the current session. Return to the " +
                "planner to continue.",
            style = MaterialTheme.typography.bodyLarge,
            color = TransitMain,
        )

        Button(
            onClick = onReturnToPlanner,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Return to planner")
        }
    }
}
