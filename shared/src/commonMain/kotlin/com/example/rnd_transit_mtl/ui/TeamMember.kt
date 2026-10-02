package com.example.rnd_transit_mtl.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rnd_transit_mtl.ui.theme.TransitWhite
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * Displays a team photograph beside the shared About page placeholder text.
 *
 * The teammate name is used for accessibility; the visible text is "We make stuff".
 *
 * @param name Teammate name used in the photograph accessibility description.
 * @param photo Shared drawable resource containing the teammate photograph.
 * @param layoutScale Scale factor for dimensions and text relative to the 402-unit reference width.
 */
@Composable
internal fun TeamMember(name: String, photo: DrawableResource, layoutScale: Float) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp * layoutScale)
    ) {
        /** Crop the photograph to fill the same square size for every team member. */
        Image(
            painter = painterResource(photo),
            contentDescription = "$name, team member",
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(116.dp * layoutScale)
        )
        Text(
            text = "We make\nstuff",
            color = TransitWhite,
            fontSize = 44.sp * layoutScale,
            lineHeight = 52.sp * layoutScale,
            modifier = Modifier.weight(1f)
        )
    }
}