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

@Composable
internal fun TeamMember(name: String, photo: DrawableResource, layoutScale: Float) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp * layoutScale)
    ) {
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

