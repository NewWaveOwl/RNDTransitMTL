package com.example.rnd_transit_mtl.ui.global_ui_ellements.trip

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rnd_transit_mtl.ui.global_ui_ellements.ratings.StarRatingRow
import com.example.rnd_transit_mtl.ui.theme.TransitComplementary
import com.example.rnd_transit_mtl.ui.theme.TransitWhite

// Layout settings — shared section padding is defined in TripV3Shared.kt.
private val ReviewBandSpacing = 8.dp
private val ReviewActionSpacing = 16.dp

/**
 * Overall-review form; a non-null rating is required for Save. Draft and persistence belong to the caller.
 *
 * @param title Title of the trip being reviewed.
 * @param reference Short display suffix identifying the trip record.
 * @param rating Current 1–5 star rating, or null when unrated.
 * @param comment Current optional review comment text.
 * @param onRatingChange Called with the updated star rating, or null when toggled off.
 * @param onCommentChange Called when the user edits the comment field.
 * @param onSave Called when the user submits the review.
 * @param onSkip Called when the user skips the review.
 * @param onFeedback Called when the user opens per-leg or per-place feedback.
 * @param onGo Called when the user activates the header GO shortcut.
 * @param modifier Configuration applied to the page container.
 * @param enabled Whether interactive controls accept input.
 * @param feedbackEnabled Whether the per-leg/place feedback action is enabled.
 * @param errorMessage Optional validation or persistence error message.
 */
@Composable
fun ReviewV3(
    title: String,
    reference: String,
    rating: Int?,
    comment: String,
    onRatingChange: (Int?) -> Unit,
    onCommentChange: (String) -> Unit,
    onSave: () -> Unit,
    onSkip: () -> Unit,
    onFeedback: () -> Unit,
    onGo: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    feedbackEnabled: Boolean = true,
    errorMessage: String? = null,
) {
    V3Page(modifier) {
        V3Heading(
            title = "Rate your trip",
            subtitle = "$title · Trip #$reference",
            onGo = onGo,
            enabled = enabled,
        )

        Column(
            Modifier
                .fillMaxWidth()
                .background(TransitComplementary)
                .padding(V3SectionPadding),
            verticalArrangement = Arrangement.spacedBy(ReviewBandSpacing),
        ) {
            Text(
                "Overall experience",
                color = TransitWhite,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Text("How was your adventure?", color = TransitWhite)

            StarRatingRow(
                rating = rating,
                onRatingChange = { onRatingChange(if (rating == it) null else it) },
                label = "Overall experience",
                enabled = enabled,
                prominent = true,
            )

            Text(
                rating?.let { "$it out of 5" } ?: "No rating selected",
                color = TransitWhite,
            )
        }

        Column(
            Modifier.padding(V3SectionPadding),
            verticalArrangement = Arrangement.spacedBy(ReviewActionSpacing),
        ) {
            V3Note(comment, onCommentChange, "Optional comment", enabled)

            Text(
                "Choose stars to save a review, or skip. Your trip stays saved.",
                color = TransitComplementary,
                fontSize = 12.sp,
            )

            V3Error(errorMessage)
            V3Button("Save review", onSave, enabled && rating != null)
            V3Button("Skip review", onSkip, enabled, true)
            V3Button(
                "Rate a route leg or place",
                onFeedback,
                enabled && feedbackEnabled,
                true,
            )
        }
    }
}
