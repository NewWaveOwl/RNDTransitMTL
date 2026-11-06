package com.example.rnd_transit_mtl.View.current_trip.previews
import com.example.rnd_transit_mtl.model.ids.RouteId
import com.example.rnd_transit_mtl.model.ids.TransportId
import com.example.rnd_transit_mtl.model.ids.TripId

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.rnd_transit_mtl.data.assets.toSampleMapPlace
import com.example.rnd_transit_mtl.model.trip.RouteLeg
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.model.trip.TripStatus
import com.example.rnd_transit_mtl.model.trip.TripPoint
import com.example.rnd_transit_mtl.model.trip.TripRouteSnapshot
import com.example.rnd_transit_mtl.model.trip.TripTransportSnapshot
import com.example.rnd_transit_mtl.View.current_trip.CurrentTripContent
import com.example.rnd_transit_mtl.ui.theme.RNDTransitTheme

private val currentTripPreviewStart = TripPoint(
    label = "Start",
    x = 0.45f,
    y = 0.25f
).toSampleMapPlace()

private val currentTripPreviewDestination = TripPoint(
    label = "Destination",
    x = 0.55f,
    y = 0.48f
).toSampleMapPlace()

private val currentTripPreviewRecord = Trip(
    id = TripId("current-trip-static-preview"),
    status = TripStatus.Active,
    title = "Current trip 1",
    description = "A relaxed afternoon exploring the mock transit route.",
    imageUrl = "",
    origin = currentTripPreviewStart,
    destination = currentTripPreviewDestination,
    legs = listOf(
        RouteLeg.straightLine(
            transportId = TransportId("preview-walk"),
            from = currentTripPreviewStart.point,
            to = currentTripPreviewDestination.point,
            durationSeconds = 30 * 60L
        )
    ),
    plannedMinutes = 30,
    selectedTransports = listOf(
        TripTransportSnapshot(
            id = TransportId("preview-walk"),
            label = "Walking",
            usesRoutes = false
        ),
        TripTransportSnapshot(
            id = TransportId("preview-bus"),
            label = "Bus",
            usesRoutes = true
        )
    ),
    selectedRoutes = listOf(
        TripRouteSnapshot(
            id = RouteId("preview-route"),
            transportTypeId = TransportId("preview-bus"),
            label = "Preview route"
        )
    ),
    attractionIntensity = 60f,
    createdAt = 1_000L
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
    name = "Current trip — 100 percent",
    showBackground = true,
    widthDp = 390,
    heightDp = 850
)
@Composable
fun CurrentTripCompletedPreview() {
    CurrentTripExample(
        progress = 1f
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
    CurrentTripExample(progress = 1f, showInformation = true)
}

@Composable
private fun CurrentTripExample(
    progress: Float,
    showInformation: Boolean = false
) {
    val record = currentTripPreviewRecord.copy(
        status = if (progress == 1f) TripStatus.Completed else TripStatus.Active,
        completedAt = if (progress == 1f) {
            2_000L
        } else {
            null
        }
    )

    RNDTransitTheme {
        CurrentTripContent(
            onFinishRequested = {},
            trip = record,
            progress = progress,
            actionsEnabled = true,
            message = null,
            onLeave = {},
            onCancel = { _ -> },
            onRequestReview = {},
            onOpenImageReference = {},
            showTripInformation = showInformation
        )
    }
}
