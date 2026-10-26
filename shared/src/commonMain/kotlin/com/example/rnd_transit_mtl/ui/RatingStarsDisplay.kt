package com.example.rnd_transit_mtl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.example.rnd_transit_mtl.ui.theme.TransitHighlight
import com.example.rnd_transit_mtl.ui.theme.TransitWhite
import org.jetbrains.compose.resources.painterResource
import rnd_transit_mtl.shared.generated.resources.Res
import rnd_transit_mtl.shared.generated.resources.ic_google_demo_star_filled
import rnd_transit_mtl.shared.generated.resources.ic_google_demo_star_outline

/**
 * Read-only rating display.
 *
 * Unlike StarRatingRow, this has no buttons or input callbacks.
 * A null optional category is described as Not rated, never zero stars.
 */
@Composable
fun RatingStarsDisplay(
    label: String,
    rating: Int?,
    modifier: Modifier = Modifier,
    prominent: Boolean = false,
    textColor: Color = TransitWhite,
    starColor: Color = TransitHighlight
) {
    require(rating == null || rating in 1..5) {
        "Rating must be null or between 1 and 5."
    }

    Column(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = if (rating == null) {
                "$label: not rated"
            } else {
                "$label: $rating out of 5"
            }
        },
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = textColor
        )

        if (rating == null) {
            Text(
                text = "Not rated",
                style = MaterialTheme.typography.bodyMedium,
                color = textColor
            )
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(5) { index ->
                    Icon(
                        painter = painterResource(
                            if (index + 1 <= rating) {
                                Res.drawable.ic_google_demo_star_filled
                            } else {
                                Res.drawable.ic_google_demo_star_outline
                            }
                        ),
                        contentDescription = null,
                        tint = starColor,
                        modifier = Modifier.size(
                            if (prominent) 28.dp else 20.dp
                        )
                    )
                }

                Text(
                    text = "$rating / 5",
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor
                )
            }
        }
    }
}
