package com.example.rnd_transit_mtl.ui.global_ui_ellements.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.rnd_transit_mtl.ui.theme.TransitComplementary
import com.example.rnd_transit_mtl.ui.theme.TransitWhite
import org.maplibre.compose.map.StyleLoadState

// Layout settings — dp insets and indicator sizes for map attribution and style-loading status badges.
private val MapAttributionInset = 8.dp
private val MapAttributionBottomGap = 4.dp
private val MapStatusTopOffset = 64.dp
private val MapStatusSpinnerSize = 24.dp

/**
 * Renders OpenFreeMap attribution and style loading/offline/error overlays over [AndroidTransitMap].
 *
 * @param hasTrip Whether a trip route is currently displayed (adjusts overlay alignment).
 * @param offline Whether the map is currently showing the bundled offline fallback.
 * @param styleLoadState Current MapLibre style load state.
 * @param data Active map presentation configuration.
 * @param onRetryStyle Reloads the failed style, including the bundled style while offline.
 * @param onRetryOnline Tries to switch a working bundled map back to the online style.
 */
@Composable
internal fun BoxScope.AndroidMapStatusOverlays(
    hasTrip: Boolean,
    offline: Boolean,
    styleLoadState: StyleLoadState,
    data: TransitMapData,
    onRetryStyle: () -> Unit,
    onRetryOnline: () -> Unit,
) {
    if (!offline && !data.attributionHandledByScreen &&
        (!data.browsing || data.pickerOpen || !data.interactive)
    ) {
        val uriHandler = LocalUriHandler.current
        Surface(
            modifier = Modifier
                .align(if (!hasTrip) Alignment.TopStart else Alignment.BottomStart)
                .padding(
                    start = MapAttributionInset,
                    top = MapAttributionInset,
                    bottom = data.bottomInset + MapAttributionBottomGap,
                )
                .clickable { uriHandler.openUri("https://openfreemap.org/quick_start/") },
            color = TransitWhite,
            shape = RoundedCornerShape(4.dp),
        ) {
            Text(
                "OpenFreeMap",
                color = TransitComplementary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }

    val statusAlignment = if (!hasTrip) Alignment.TopCenter else Alignment.Center
    val statusOffset: Dp = if (!hasTrip) MapStatusTopOffset else 0.dp
    when (styleLoadState) {
        StyleLoadState.Ready -> if (offline) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = MapAttributionInset, bottom = data.bottomInset + MapAttributionBottomGap),
                color = TransitWhite,
                shape = MaterialTheme.shapes.small,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TextButton(onClick = onRetryOnline) {
                        Text("Offline map · Retry", color = TransitComplementary)
                    }
                    Text(
                        "© OpenStreetMap contributors",
                        color = TransitComplementary,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp).padding(bottom = 4.dp),
                    )
                }
            }
        }

        is StyleLoadState.Failed -> Surface(
            modifier = Modifier.align(statusAlignment).padding(top = statusOffset),
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("Couldn’t load the map")
                TextButton(onClick = onRetryStyle) { Text("Retry") }
            }
        }

        else -> CircularProgressIndicator(
            modifier = Modifier
                .align(statusAlignment)
                .padding(top = statusOffset)
                .size(MapStatusSpinnerSize)
                .semantics { contentDescription = "Loading map" },
        )
    }
}
