package com.example.rnd_transit_mtl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitOrange
import com.example.rnd_transit_mtl.ui.theme.TransitWhite
import org.jetbrains.compose.resources.painterResource
import rnd_transit_mtl.shared.generated.resources.Res
import rnd_transit_mtl.shared.generated.resources.ic_google_demo_directions_walk

/**
 * A recognizable orange walking person on a contrasting circular badge.
 *
 * The marker's anchor is its center. MockTripMap places that center
 * exactly on the interpolated route position at every progress value.
 *
 * A null description is appropriate when the parent map supplies
 * the complete accessible description.
 */
@Composable
fun OrangePersonMarker(
    modifier: Modifier = Modifier,
    description: String? = "Orange person representing the trip traveller"
) {
    val accessibilityModifier = if (description == null) {
        Modifier
    } else {
        Modifier.semantics {
            contentDescription = description
        }
    }

    Box(
        modifier = modifier
            .size(48.dp)
            .then(accessibilityModifier)
            .background(TransitWhite, CircleShape)
            .border(2.dp, TransitMain, CircleShape)
            .padding(6.dp)
    ) {
        Icon(
            painter = painterResource(
                Res.drawable.ic_google_demo_directions_walk
            ),
            contentDescription = null,
            tint = TransitOrange,
            modifier = Modifier.fillMaxSize()
        )
    }
}