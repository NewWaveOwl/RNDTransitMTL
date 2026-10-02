package com.example.rnd_transit_mtl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import com.example.rnd_transit_mtl.ui.theme.TransitWhite
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitHighlight
import org.jetbrains.compose.resources.painterResource
import rnd_transit_mtl.shared.generated.resources.Res
import rnd_transit_mtl.shared.generated.resources.ic_close

/** Displays every saved trip and provides an individual removal action. */
@Composable
internal fun TripResults(
    trips: List<String>,
    onRemoveTrip: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        itemsIndexed(trips) { index, trip ->
            TripResultCard(
                number = index + 1,
                summary = trip,
                onRemove = { onRemoveTrip(index) }
            )
        }
        item {
            Text(
                text = "Swipe right on the map to return. Press × to remove a trip.",
                color = TransitWhite,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

/** Draws one saved trip using the shared highlight and main colours. */
@Composable
private fun TripResultCard(
    number: Int,
    summary: String,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(30.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .background(
                brush = Brush.horizontalGradient(
                    0.00f to TransitHighlight,
                    0.76f to TransitHighlight,
                    0.90f to TransitMain,
                    1.00f to TransitMain
                ),
                shape = cardShape
            )
    ) {
        Text(
            text = "$number. $summary",
            color = TransitWhite,
            fontSize = 23.sp,
            lineHeight = 27.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = "Trip $number. $summary" }
                .padding(horizontal = 22.dp, vertical = 16.dp)
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .width(78.dp)
                .heightIn(min = 96.dp)
                .clickable(onClick = onRemove)
                .semantics { contentDescription = "Remove trip $number" }
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_close),
                contentDescription = null,
                tint = TransitWhite,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}
