package com.example.rnd_transit_mtl.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * Displays an existing team photograph and the supplied member name.
 *
 * No role, biography, contact information, or contribution is inferred.
 * The shared About screen owns the surrounding layout.
 */
@Composable
internal fun TeamMember(
    name: String,
    photo: DrawableResource,
    layoutScale: Float
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp * layoutScale)
    ) {
        Image(
            painter = painterResource(photo),
            contentDescription = "$name, RND Transit team member",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(116.dp * layoutScale)
                .clip(RoundedCornerShape(16.dp))
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = name,
                color = TransitMain,
                style = MaterialTheme.typography.headlineSmall
            )

            Text(
                text = "RND Transit team",
                color = TransitMain,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
