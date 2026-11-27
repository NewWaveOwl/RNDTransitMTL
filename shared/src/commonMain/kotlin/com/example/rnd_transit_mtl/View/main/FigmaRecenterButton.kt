package com.example.rnd_transit_mtl.View.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import rnd_transit_mtl.shared.generated.resources.Res
import rnd_transit_mtl.shared.generated.resources.rnd_recenter

// Layout settings — dp touch-target size for the Figma recenter control.
private val OriginRecenterSize = 48.dp

/**
 * Figma recenter control for the planner map.
 *
 * @param enabled Whether the origin is available and input is permitted.
 * @param onClick Requests camera recentering without changing the origin.
 * @param modifier Parent placement.
 */
@Composable
internal fun FigmaRecenterButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(Res.drawable.rnd_recenter),
        contentDescription = "Recenter map on starting point",
        modifier = modifier
            .size(OriginRecenterSize)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
    )
}
