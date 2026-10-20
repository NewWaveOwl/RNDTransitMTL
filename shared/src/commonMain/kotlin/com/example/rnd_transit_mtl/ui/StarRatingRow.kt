package com.example.rnd_transit_mtl.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.example.rnd_transit_mtl.ui.theme.TransitHighlight
import org.jetbrains.compose.resources.painterResource
import rnd_transit_mtl.shared.generated.resources.Res
import rnd_transit_mtl.shared.generated.resources.ic_google_demo_star_filled
import rnd_transit_mtl.shared.generated.resources.ic_google_demo_star_outline

/**
 * Stateless five-star input.
 *
 * Null means no selection. The zero used for visual comparison is
 * never emitted as a rating.
 *
 * Each tap target is 48 dp. Extremely narrow containers can scroll
 * horizontally rather than shrinking the targets.
 */
@Composable
fun StarRatingRow(
    rating: Int?,
    onRatingChange: (Int) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    prominent: Boolean = false
) {
    require(rating == null || rating in 1..5) {
        "Rating must be null or between 1 and 5."
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .selectableGroup()
            .semantics {
                contentDescription = "$label rating"
                stateDescription = if (rating == null) {
                    "Not selected"
                } else {
                    "$rating out of 5"
                }
            },
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        repeat(5) { index ->
            val stars = index + 1
            val filled = stars <= (rating ?: 0)

            IconButton(
                onClick = { onRatingChange(stars) },
                enabled = enabled,
                modifier = Modifier
                    .size(48.dp)
                    .semantics {
                        role = Role.RadioButton
                        selected = rating == stars
                        contentDescription =
                            "Rate $label $stars out of 5"
                    }
            ) {
                Icon(
                    painter = painterResource(
                        if (filled) {
                            Res.drawable.ic_google_demo_star_filled
                        } else {
                            Res.drawable.ic_google_demo_star_outline
                        }
                    ),
                    contentDescription = null,
                    tint = TransitHighlight,
                    modifier = Modifier.size(
                        if (prominent) 40.dp else 32.dp
                    )
                )
            }
        }
    }
}