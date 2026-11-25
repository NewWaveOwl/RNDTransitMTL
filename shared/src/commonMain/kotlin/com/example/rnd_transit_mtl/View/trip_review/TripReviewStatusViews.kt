package com.example.rnd_transit_mtl.View.trip_review

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.rnd_transit_mtl.model.review.TripReviewMode
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitWhite

// Layout settings — dp padding and spacing for trip-review status and fallback screens.
private val ReviewStatusPadding = 24.dp
private val ReviewStatusSpacing = 16.dp

/**
 * Confirmation/retry view shown while review exit navigation is pending or failed.
 *
 * @param exitMessage Outcome message explaining what happened to the review.
 * @param errorMessage Optional navigation retry message.
 * @param mode Review entry mode determining the continue button label.
 * @param enabled Whether the continue button accepts clicks.
 * @param onContinue Retries exit navigation.
 * @param modifier Parent layout constraints.
 */
@Composable
internal fun TripReviewExitView(
    exitMessage: String,
    errorMessage: String?,
    mode: TripReviewMode,
    enabled: Boolean,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TransitWhite)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(ReviewStatusPadding),
        verticalArrangement = Arrangement.spacedBy(ReviewStatusSpacing),
    ) {
        Text(
            text = exitMessage,
            style = MaterialTheme.typography.headlineSmall,
            color = TransitMain,
        )
        if (errorMessage != null) {
            Text(text = errorMessage, color = MaterialTheme.colorScheme.error)
        }
        Button(
            onClick = onContinue,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (mode == TripReviewMode.INITIAL) "Continue to History" else "Return to previous view",
            )
        }
    }
}

/**
 * Fallback view shown when the completed trip being reviewed is no longer available in storage.
 *
 * @param enabled Whether the return button accepts clicks.
 * @param onReturn Initiates exit back to the caller.
 * @param modifier Parent layout constraints.
 */
@Composable
internal fun TripReviewUnavailableView(
    enabled: Boolean,
    onReturn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TransitWhite)
            .padding(ReviewStatusPadding),
        verticalArrangement = Arrangement.spacedBy(ReviewStatusSpacing),
    ) {
        Text(
            text = "Trip unavailable",
            style = MaterialTheme.typography.headlineSmall,
            color = TransitMain,
        )
        Text(
            text = "This completed trip was removed or is not available in the current session.",
            style = MaterialTheme.typography.bodyLarge,
            color = TransitMain,
        )
        Button(
            onClick = onReturn,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Return")
        }
    }
}

/**
 * Transitional view shown while the review draft is being initialized.
 *
 * @param errorMessage Optional initialization error text.
 * @param enabled Whether the close button accepts clicks.
 * @param onClose Discards and closes the review flow.
 * @param modifier Parent layout constraints.
 */
@Composable
internal fun TripReviewOpeningView(
    errorMessage: String?,
    enabled: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TransitWhite)
            .padding(ReviewStatusPadding),
        verticalArrangement = Arrangement.spacedBy(ReviewStatusSpacing),
    ) {
        Text(
            text = "Opening review",
            style = MaterialTheme.typography.titleLarge,
            color = TransitMain,
        )
        if (errorMessage != null) {
            Text(text = errorMessage, color = MaterialTheme.colorScheme.error)
        }
        Button(onClick = onClose, enabled = enabled) {
            Text("Close")
        }
    }
}
