package com.example.rnd_transit_mtl.View.main.previews
import com.example.rnd_transit_mtl.model.ids.RouteId
import com.example.rnd_transit_mtl.model.ids.TransportId

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.rnd_transit_mtl.model.transport.TransportRoute
import com.example.rnd_transit_mtl.model.transport.TransportType
import com.example.rnd_transit_mtl.View.main.TripPlannerContent
import com.example.rnd_transit_mtl.ui.theme.RNDTransitTheme

private val plannerPreviewTypes = listOf(
    TransportType(TransportId("train"), "Train", true),
    TransportType(TransportId("rem"), "REM", true),
    TransportType(TransportId("walk"), "Walk", false),
    TransportType(TransportId("bus"), "Bus", true),
    TransportType(TransportId("metro"), "Metro", true),
    TransportType(TransportId("bike"), "Bike", false)
)

private val plannerPreviewRoutes = listOf(
    TransportRoute(RouteId("train:11"), TransportId("train"), "11"),
    TransportRoute(RouteId("train:14"), TransportId("train"), "14"),
    TransportRoute(RouteId("bus:401"), TransportId("bus"), "401"),
    TransportRoute(RouteId("bus:747"), TransportId("bus"), "747")
)

@Preview(
    name = "Planner phone",
    showBackground = true,
    widthDp = 350,
    heightDp = 780
)
@Composable
fun TripPlannerPreview() {
    PlannerPreviewContent()
}

@Preview(
    name = "Planner desktop",
    showBackground = true,
    widthDp = 1000,
    heightDp = 900
)
@Composable
fun TripPlannerDesktopPreview() {
    PlannerPreviewContent()
}

@Preview(
    name = "Resume existing trip",
    showBackground = true,
    widthDp = 350,
    heightDp = 780
)
@Composable
fun TripPlannerResumePreview() {
    PlannerPreviewContent(resume = true)
}

@Preview(
    name = "360 minutes on narrow phone",
    showBackground = true,
    widthDp = 320,
    heightDp = 780
)
@Composable
fun TripPlannerMaxMinutesPreview() {
    PlannerPreviewContent(minutes = 360)
}

@Preview(
    name = "Five minutes hides lower arrow",
    showBackground = true,
    widthDp = 320,
    heightDp = 780
)
@Composable
fun TripPlannerMinimumMinutesPreview() {
    PlannerPreviewContent(minutes = 5)
}

@Preview(
    name = "360 minutes with Resume",
    showBackground = true,
    widthDp = 320,
    heightDp = 780
)
@Composable
fun TripPlannerMaxMinutesResumePreview() {
    PlannerPreviewContent(minutes = 360, resume = true)
}

@Preview(
    name = "Planner validation",
    showBackground = true,
    widthDp = 350,
    heightDp = 780
)
@Composable
fun TripPlannerValidationPreview() {
    PlannerPreviewContent(showErrors = true)
}

@Composable
private fun PlannerPreviewContent(
    resume: Boolean = false,
    showErrors: Boolean = false,
    minutes: Int = 30
) {
    RNDTransitTheme {
        TripPlannerContent(
            minutes = minutes,
            onMinutesChange = {},
            transportTypes = plannerPreviewTypes,
            transportRoutes = plannerPreviewRoutes,
            selectedTransportIds = if (showErrors) emptyList() else listOf(TransportId("walk"), TransportId("bus")),
            selectedRouteIds = if (showErrors) emptyList() else listOf(RouteId("bus:401")),
            expandedTransportId = null,
            onExpandedTransportChange = {},
            onToggleTransport = {},
            onToggleRoute = { _, _ -> },
            validationMessage = if (showErrors) {
                "Choose at least one transport type."
            } else {
                ""
            },
            actionLabel = if (resume) "Resume trip" else "GO",
            actionEnabled = true,
            activeTripSummary = if (resume) {
                "Unfinished: Sunday discovery walk · 50%"
            } else {
                null
            },
            pendingReviewSummary = null,
            onPrimaryAction = {}
        )
    }
}
