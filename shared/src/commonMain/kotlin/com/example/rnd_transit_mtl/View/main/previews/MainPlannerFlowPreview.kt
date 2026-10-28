package com.example.rnd_transit_mtl.View.main.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.rnd_transit_mtl.View.main.MainPlannerContent
import com.example.rnd_transit_mtl.View.main.PlannerSheetPosition
import com.example.rnd_transit_mtl.model.transport.TransportType
import com.example.rnd_transit_mtl.ui.theme.RNDTransitTheme

@Preview(name = "Main sheet / Figma 412", widthDp = 412, heightDp = 800)
@Composable
private fun MainSheetPreview() = PlannerFlowExample(PlannerSheetPosition.Main)

@Preview(name = "Routes sheet / Figma 412", widthDp = 412, heightDp = 800)
@Composable
private fun RoutesSheetPreview() = PlannerFlowExample(PlannerSheetPosition.Routes)

@Preview(name = "Preferences sheet / Figma 412", widthDp = 412, heightDp = 800)
@Composable
private fun PreferencesSheetPreview() = PlannerFlowExample(PlannerSheetPosition.Preferences)

@Preview(name = "Preferences sheet / short window", widthDp = 350, heightDp = 420)
@Composable
private fun ShortPreferencesPreview() = PlannerFlowExample(PlannerSheetPosition.Preferences)

/** Supplies a stable preview draft without generating a trip or requiring navigation. */
@Composable
private fun PlannerFlowExample(position: PlannerSheetPosition) {
    RNDTransitTheme {
        MainPlannerContent(
            minutes = 30, onMinutesChange = {},
            transportTypes = listOf(
                TransportType("walk", "Walk", false), TransportType("bike", "Bike", false),
                TransportType("bus", "Bus", true), TransportType("metro", "Metro", true),
                TransportType("train", "Train", true), TransportType("rem", "REM", true)
            ),
            selectedTransportIds = listOf("walk", "bus"), selectedZonesByTransport = mapOf("bus" to listOf("A")),
            onToggleTransport = {}, onToggleZone = { _, _ -> }, intensity = 90f, onIntensityChange = {},
            walkingPreference = "Amble", selectedAttractionIds = listOf("Nature", "Surprise"),
            bikeSpeedKmh = 18, onBikeSpeedChange = {},
            onWalkingPreferenceChange = {}, onToggleAttraction = {}, validationMessage = "",
            actionLabel = "GO", actionEnabled = true, inputsEnabled = true,
            activeTripSummary = null, pendingReviewSummary = null, onPrimaryAction = {},
            onOpenDetailedSettings = {}, initialSheetPosition = position
        )
    }
}
