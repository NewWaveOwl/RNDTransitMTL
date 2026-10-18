package com.example.rnd_transit_mtl.ui.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.rnd_transit_mtl.model.TransportRoute
import com.example.rnd_transit_mtl.model.TransportType
import com.example.rnd_transit_mtl.ui.TripPlannerContent
import com.example.rnd_transit_mtl.ui.theme.RNDTransitTheme

private val plannerPreviewTypes = listOf(
    TransportType("train", "Train", true),
    TransportType("rem", "REM", true),
    TransportType("walk", "Walk", false),
    TransportType("bus", "Bus", true),
    TransportType("metro", "Metro", true),
    TransportType("bike", "Bike", false)
)

private val plannerPreviewRoutes = listOf(
    TransportRoute("train:11", "train", "11"),
    TransportRoute("train:14", "train", "14"),
    TransportRoute("bus:401", "bus", "401"),
    TransportRoute("bus:747", "bus", "747")
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
    name = "Zero minutes hides lower arrow",
    showBackground = true,
    widthDp = 320,
    heightDp = 780
)
@Composable
fun TripPlannerZeroMinutesPreview() {
    PlannerPreviewContent(minutes = 0)
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
            selectedTransportIds = if (showErrors) emptyList() else listOf("walk", "bus"),
            selectedRouteIds = if (showErrors) emptyList() else listOf("bus:401"),
            expandedTransportId = null,
            onExpandedTransportChange = {},
            onToggleTransport = {},
            onToggleRoute = { _, _ -> },
            intensity = 90f,
            onIntensityChange = {},
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
