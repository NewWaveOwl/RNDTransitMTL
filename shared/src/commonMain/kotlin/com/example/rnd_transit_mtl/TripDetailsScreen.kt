package com.example.rnd_transit_mtl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import com.example.rnd_transit_mtl.state.LocalTripsStore
import com.example.rnd_transit_mtl.ui.TripDetailsContent

/**
 * Resolves the latest completed record by stable ID.
 *
 * No trip is generated, appended, or reconstructed from a route snapshot.
 * No simulation effect is created.
 *
 * Review navigation should use TripReviewMode.EDIT with this details
 * entry as its origin. If this record is subsequently deleted, the
 * review navigation owner should recover to History.
 */
@Composable
fun TripDetailsScreen(
    tripId: String,
    onReturnToHistory: () -> Unit,
    onReview: ((String) -> Unit)? = null,
    isDestinationActive: Boolean = true,
    modifier: Modifier = Modifier
) {
    val tripsStore = LocalTripsStore.current
    val uriHandler = LocalUriHandler.current

    val snapshot by tripsStore.state
    val trip = snapshot.completedTrips.firstOrNull {
        it.id == tripId
    }

    val latestDestinationActive by rememberUpdatedState(
        isDestinationActive
    )

    var errorMessage by remember(tripId) {
        mutableStateOf<String?>(null)
    }

    val reviewCallback: (() -> Unit)? = onReview?.let { callback ->
        {
            if (latestDestinationActive) {
                if (tripsStore.findCompleted(tripId) == null) {
                    errorMessage = "This trip is no longer available."
                } else {
                    errorMessage = null
                    callback(tripId)
                }
            }
        }
    }

    TripDetailsContent(
        trip = trip,
        onReturnToHistory = {
            if (latestDestinationActive) {
                onReturnToHistory()
            }
        },
        onReview = reviewCallback,
        onOpenImageReference = {
            if (latestDestinationActive) {
                val latestTrip = tripsStore.findCompleted(tripId)

                if (latestTrip == null) {
                    errorMessage = "This trip is no longer available."
                } else {
                    try {
                        uriHandler.openUri(latestTrip.imageUrl)
                        errorMessage = null
                    } catch (_: Exception) {
                        errorMessage =
                            "The image reference could not be opened. " +
                                "You can copy its URL from Trip information."
                    }
                }
            }
        },
        actionsEnabled = isDestinationActive,
        errorMessage = errorMessage,
        modifier = modifier
    )
}
