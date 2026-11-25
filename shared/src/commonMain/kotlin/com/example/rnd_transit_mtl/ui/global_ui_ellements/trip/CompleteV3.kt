package com.example.rnd_transit_mtl.ui.global_ui_ellements.trip

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.rnd_transit_mtl.View.current_trip.TripCompletionContent
import com.example.rnd_transit_mtl.model.trip.Trip

// Layout settings — delegated to View/current_trip/TripCompletionContent.kt.

/**
 * Completed-record presentation delegating to [TripCompletionContent].
 *
 * @param trip Completed trip record to display.
 * @param onReview Called when the user chooses to review the completed trip.
 * @param onSkipToHistory Called when the user skips the review and opens History.
 * @param onPlanAnother Called when the user chooses to plan another adventure.
 * @param modifier Configuration applied to the root container.
 * @param enabled Whether action buttons accept clicks.
 * @param errorMessage Optional persistence or action error message.
 * @param syncPending Whether the completed trip is still awaiting cloud acknowledgement.
 */
@Composable
fun CompleteV3(
    trip: Trip,
    onReview: () -> Unit,
    onSkipToHistory: () -> Unit,
    onPlanAnother: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    errorMessage: String? = null,
    syncPending: Boolean = false,
) {
    TripCompletionContent(
        trip = trip,
        onReview = onReview,
        onSkipToHistory = onSkipToHistory,
        onPlanAnother = onPlanAnother,
        modifier = modifier,
        enabled = enabled,
        errorMessage = errorMessage,
        syncPending = syncPending,
    )
}
