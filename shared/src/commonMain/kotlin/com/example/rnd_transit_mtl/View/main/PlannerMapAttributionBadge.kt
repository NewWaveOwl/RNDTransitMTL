package com.example.rnd_transit_mtl.View.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.LocalTransitMap
import com.example.rnd_transit_mtl.ui.theme.TransitComplementary
import com.example.rnd_transit_mtl.ui.theme.TransitWhite
import kotlin.math.roundToInt

// Layout settings — dp offsets and padding for the map attribution badge above the GO bar.
private val AttributionClearanceAboveGo = 22.dp
private val AttributionStartInset = 8.dp
private val AttributionTextHorizontalPadding = 4.dp
private val AttributionCornerRadius = 4.dp

/**
 * Attribution pill positioned above the GO bar when sufficient map viewport is visible.
 *
 * @param goTopPx Current vertical top offset of the GO box in pixels.
 * @param density Display density converting dp badge clearance to pixels.
 */
@Composable
internal fun PlannerMapAttributionBadge(
    goTopPx: Float,
    density: Density,
) {
    val liveMap = LocalTransitMap.current != null
    val uriHandler = LocalUriHandler.current
    Surface(
        modifier = Modifier
            .offset { IntOffset(0, (goTopPx - with(density) { AttributionClearanceAboveGo.toPx() }).roundToInt()) }
            .padding(start = AttributionStartInset)
            .then(
                if (liveMap) Modifier.clickable { uriHandler.openUri("https://openfreemap.org/quick_start/") }
                else Modifier,
            ),
        color = TransitWhite,
        shape = RoundedCornerShape(AttributionCornerRadius),
    ) {
        Text(
            if (liveMap) "OpenFreeMap" else "© OpenStreetMap contributors · Sample map",
            color = TransitComplementary,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = AttributionTextHorizontalPadding),
        )
    }
}
