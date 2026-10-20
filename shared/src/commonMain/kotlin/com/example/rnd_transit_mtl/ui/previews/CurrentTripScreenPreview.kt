package com.example.rnd_transit_mtl.ui.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.rnd_transit_mtl.model.Trip
import com.example.rnd_transit_mtl.model.TripPoint
import com.example.rnd_transit_mtl.model.TripRouteSnapshot
import com.example.rnd_transit_mtl.model.TripTransportSnapshot
import com.example.rnd_transit_mtl.ui.CurrentTripContent
import com.example.rnd_transit_mtl.ui.theme.RNDTransitTheme

private val currentTripPreviewRecord = Trip(
    id = "current-trip-static-preview",
    title = "Current trip 1",
    description = "A relaxed afternoon exploring the mock transit route.",
    imageUrl = "",
    start = TripPoint(
        label = "Start",
        x = 0.45f,
        y = 0.25f
    ),
    destination = TripPoint(
        label = "Destination",
        x = 0.55f,
        y = 0.48f
    ),
    plannedMinutes = 30,
    selectedTransports = listOf(
        TripTransportSnapshot(
            id = "preview-walk",
            label = "Walking",
            usesRoutes = false
        ),
        TripTransportSnapshot(
            id = "preview-bus",
            label = "Bus",
            usesRoutes = true
        )
    ),
    selectedRoutes = listOf(
        TripRouteSnapshot(
            id = "preview-route",
            transportTypeId = "preview-bus",
            label = "Preview route"
        )
    ),
    attractionIntensity = 60f,
    distanceKm = 10.0,
    createdAtEpochMillis = 1_000L
)

@Preview(
    name = "Current trip — 0 percent",
    showBackground = true,
    widthDp = 320,
    heightDp = 850
)
@Composable
fun CurrentTripStartPreview() {
    CurrentTripExample(progress = 0f)
}

@Preview(
    name = "Current trip — 50 percent",
    showBackground = true,
    widthDp = 390,
    heightDp = 850
)
@Composable
fun CurrentTripHalfwayPreview() {
    CurrentTripExample(progress = 0.5f)
}

@Preview(
    name = "Current trip — paused at 50 percent",
    showBackground = true,
    widthDp = 320,
    heightDp = 850
)
@Composable
fun CurrentTripPausedPreview() {
    CurrentTripExample(
        progress = 0.5f,
        isRunning = false
    )
}

@Preview(
    name = "Current trip — 100 percent",
    showBackground = true,
    widthDp = 390,
    heightDp = 850
)
@Composable
fun CurrentTripCompletedPreview() {
    CurrentTripExample(
        progress = 1f,
        isRunning = false
    )
}

@Preview(
    name = "Desktop current trip — 50 percent",
    showBackground = true,
    widthDp = 1000,
    heightDp = 900
)
@Composable
fun CurrentTripDesktopPreview() {
    CurrentTripExample(progress = 0.5f)
}

@Preview(
    name = "Current trip information popup",
    showBackground = true,
    widthDp = 390,
    heightDp = 850
)
@Composable
fun CurrentTripInformationPopupPreview() {
    CurrentTripExample(progress = 0.5f, showInformation = true)
}

@Preview(
    name = "Completed trip information — no cancellation",
    showBackground = true,
    widthDp = 390,
    heightDp = 850
)
@Composable
fun CompletedTripInformationPopupPreview() {
    CurrentTripExample(progress = 1f, isRunning = false, showInformation = true)
}

@Composable
private fun CurrentTripExample(
    progress: Float,
    isRunning: Boolean = true,
    showInformation: Boolean = false
) {
    val record = currentTripPreviewRecord.copy(
        completedAtEpochMillis = if (progress == 1f) {
            2_000L
        } else {
            null
        }
    )

    RNDTransitTheme {
        CurrentTripContent(
            trip = record,
            progress = progress,
            isRunning = isRunning,
            actionsEnabled = true,
            message = null,
            onLeave = {},
            onCancel = {},
            onRequestReview = {},
            onOpenImageReference = {},
            showTripInformation = showInformation
        )
    }
}
