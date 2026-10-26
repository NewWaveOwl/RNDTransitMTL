package com.example.rnd_transit_mtl.ui.previews

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.rnd_transit_mtl.model.TripPoint
import com.example.rnd_transit_mtl.ui.MockTripMap
import com.example.rnd_transit_mtl.ui.TripProgressPanel
import com.example.rnd_transit_mtl.ui.theme.RNDTransitTheme
import com.example.rnd_transit_mtl.ui.theme.TransitMain

/**
 * Fixed preview data only. These previews contain no generator,
 * timer, TripsStore, or navigation dependency.
 *
 * The 4 km distance is an explicit illustrative stored demo value.
 */
@Preview(
    name = "Mock trip — 0 percent",
    showBackground = true,
    widthDp = 320,
    heightDp = 620
)
@Composable
fun MockTripAtStartPreview() {
    TripVisualExample(progress = 0f)
}

@Preview(
    name = "Mock trip — 50 percent",
    showBackground = true,
    widthDp = 402,
    heightDp = 640
)
@Composable
fun MockTripHalfwayPreview() {
    TripVisualExample(progress = 0.5f)
}

@Preview(
    name = "Mock trip — 100 percent",
    showBackground = true,
    widthDp = 402,
    heightDp = 640
)
@Composable
fun MockTripCompletedPreview() {
    TripVisualExample(progress = 1f)
}

@Preview(
    name = "Desktop mock trip — 50 percent",
    showBackground = true,
    widthDp = 1000,
    heightDp = 760
)
@Composable
fun MockTripDesktopPreview() {
    TripVisualExample(progress = 0.5f)
}

@Preview(
    name = "Boundary coordinates — start",
    showBackground = true,
    widthDp = 320,
    heightDp = 620
)
@Composable
fun MockTripBoundaryStartPreview() {
    TripVisualExample(
        progress = 0f,
        start = TripPoint("Top-left boundary", 0f, 0f),
        destination = TripPoint("Bottom-right boundary", 1f, 1f)
    )
}

@Preview(
    name = "Boundary coordinates — destination",
    showBackground = true,
    widthDp = 320,
    heightDp = 620
)
@Composable
fun MockTripBoundaryDestinationPreview() {
    TripVisualExample(
        progress = 1f,
        start = TripPoint("Top-left boundary", 0f, 0f),
        destination = TripPoint("Bottom-right boundary", 1f, 1f)
    )
}

@Composable
private fun TripVisualExample(
    progress: Float,
    start: TripPoint = TripPoint("Start", 0.20f, 0.30f),
    destination: TripPoint = TripPoint(
        "Destination",
        0.80f,
        0.70f
    )
) {
    RNDTransitTheme {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(TransitMain)
        ) {
            val mapHeight = if (maxWidth >= 600.dp) {
                360.dp
            } else {
                240.dp
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 760.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    MockTripMap(
                        start = start,
                        destination = destination,
                        progress = progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(mapHeight)
                    )

                    TripProgressPanel(
                        tripTitle = "Montréal discovery demo",
                        destinationLabel = destination.label,
                        distanceKm = 4.0,
                        progress = progress
                    )
                }
            }
        }
    }
}