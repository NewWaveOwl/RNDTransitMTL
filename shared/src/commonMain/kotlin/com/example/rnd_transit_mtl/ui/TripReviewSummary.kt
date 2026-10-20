package com.example.rnd_transit_mtl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.rnd_transit_mtl.model.TripReview
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitWhite

/**
 * Stateless saved-feedback presentation for expanded cards and details.
 */
@Composable
fun TripReviewSummary(
    review: TripReview?,
    modifier: Modifier = Modifier,
    includeOverall: Boolean = true,
    linksEnabled: Boolean = true
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = TransitMain,
                shape = RoundedCornerShape(18.dp)
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (review == null) {
            Text(
                text = "Not reviewed",
                style = MaterialTheme.typography.titleMedium,
                color = TransitWhite
            )

            Text(
                text = "No feedback has been saved for this trip.",
                style = MaterialTheme.typography.bodyMedium,
                color = TransitWhite
            )
        } else {
            if (includeOverall) {
                RatingStarsDisplay(
                    label = "Overall experience",
                    rating = review.overall,
                    prominent = true
                )
            }

            RatingStarsDisplay(
                label = "Quality",
                rating = review.quality
            )

            RatingStarsDisplay(
                label = "How interesting",
                rating = review.interesting
            )

            RatingStarsDisplay(
                label = "Fun",
                rating = review.`fun`
            )

            Text(
                text = "Comment",
                style = MaterialTheme.typography.labelLarge,
                color = TransitWhite
            )

            Text(
                text = if (review.comment.isBlank()) {
                    "No written comment."
                } else {
                    "“${review.comment}”"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = TransitWhite
            )

            if (review.imageUrl.isNotBlank()) {
                Text(
                    text = "Review photo",
                    style = MaterialTheme.typography.labelLarge,
                    color = TransitWhite
                )
                ReviewImage(imageUrl = review.imageUrl, enabled = linksEnabled)
                ReviewImageLink(
                    imageUrl = review.imageUrl,
                    contentColor = TransitWhite,
                    enabled = linksEnabled
                )
            }
        }
    }
}
