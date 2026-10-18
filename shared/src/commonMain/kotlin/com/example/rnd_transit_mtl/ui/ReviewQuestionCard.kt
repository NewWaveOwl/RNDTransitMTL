package com.example.rnd_transit_mtl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.example.rnd_transit_mtl.model.Trip
import androidx.compose.ui.unit.dp
import com.example.rnd_transit_mtl.state.ReviewStep
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitSelected
import com.example.rnd_transit_mtl.ui.theme.TransitWhite

/**
 * One stateless prompted question.
 *
 * Overall includes the optional comment. Other categories can be
 * cleared back to null after selection.
 */
@Composable
fun ReviewQuestionCard(
    step: ReviewStep,
    rating: Int?,
    comment: String,
    imageUrl: String,
    enabled: Boolean,
    onRatingChange: (Int) -> Unit,
    onClearRating: () -> Unit,
    onCommentChange: (String) -> Unit,
    onImageUrlChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val title = when (step) {
        ReviewStep.OVERALL -> "Overall experience"
        ReviewStep.QUALITY -> "Quality"
        ReviewStep.INTERESTING -> "How interesting"
        ReviewStep.FUN -> "Fun"
    }

    val question = when (step) {
        ReviewStep.OVERALL ->
            "How was your overall experience?"
        ReviewStep.QUALITY ->
            "How was the quality of your trip?"
        ReviewStep.INTERESTING ->
            "How interesting was your trip?"
        ReviewStep.FUN ->
            "How much fun did you have?"
    }

    val ratingDescription = when (rating) {
        null -> "Choose a rating"
        1 -> "Poor"
        2 -> "Fair"
        3 -> "Okay"
        4 -> "Good"
        5 -> "Excellent"
        else -> error("Unsupported rating.")
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = TransitMain,
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(horizontal = 12.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = TransitWhite,
                modifier = Modifier.semantics {
                    heading()
                    liveRegion = LiveRegionMode.Polite
                }
            )

            Text(
                text = question,
                style = MaterialTheme.typography.bodyLarge,
                color = TransitWhite
            )

            Text(
                text = if (step == ReviewStep.OVERALL) {
                    "Required"
                } else {
                    "Optional — continue without selecting a rating."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = TransitSelected
            )

            StarRatingRow(
                rating = rating,
                onRatingChange = onRatingChange,
                label = title,
                enabled = enabled,
                prominent = step == ReviewStep.OVERALL
            )

            Text(
                text = if (rating == null) {
                    ratingDescription
                } else {
                    "$ratingDescription · $rating out of 5"
                },
                style = MaterialTheme.typography.titleMedium,
                color = TransitWhite
            )
        }

        if (step != ReviewStep.OVERALL) {
            TextButton(
                onClick = onClearRating,
                enabled = enabled && rating != null,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Clear optional rating")
            }
        }

        if (step == ReviewStep.OVERALL) {
            val fieldColors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TransitMain,
                unfocusedTextColor = TransitMain,
                errorTextColor = TransitMain,
                cursorColor = TransitMain,
                focusedLabelColor = TransitMain,
                unfocusedLabelColor = TransitMain,
                focusedSupportingTextColor = TransitMain,
                unfocusedSupportingTextColor = TransitMain,
                focusedBorderColor = TransitMain,
                unfocusedBorderColor = TransitMain,
                focusedContainerColor = TransitWhite,
                unfocusedContainerColor = TransitWhite,
                errorContainerColor = TransitWhite
            )
            val commentPrompt = when {
                rating == null -> "Tell us about your trip"
                rating >= 4 -> "What did you enjoy about your trip?"
                rating <= 2 -> "What could improve your trip?"
                else -> "Tell us about your trip"
            }

            Text(
                text = commentPrompt,
                style = MaterialTheme.typography.titleMedium,
                color = TransitMain
            )

            OutlinedTextField(
                value = comment,
                onValueChange = onCommentChange,
                enabled = enabled,
                label = { Text("Optional comment") },
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                maxLines = 6,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = TransitMain
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences
                )
            )

            val trimmedUrl = imageUrl.trim()
            val invalidUrl = trimmedUrl.isNotEmpty() &&
                !Trip.isSupportedImageUrl(trimmedUrl)
            OutlinedTextField(
                value = imageUrl,
                onValueChange = onImageUrlChange,
                enabled = enabled,
                label = { Text("Image link (optional)") },
                colors = fieldColors,
                singleLine = true,
                isError = invalidUrl,
                supportingText = {
                    Text(if (invalidUrl) "Paste only an HTTPS URL, or leave this empty."
                        else "Optional photo link. Paste the URL only; the photo appears in your saved review.")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
