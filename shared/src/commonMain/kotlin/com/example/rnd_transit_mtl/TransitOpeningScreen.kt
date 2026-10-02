package com.example.rnd_transit_mtl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

import com.example.rnd_transit_mtl.model.TransportRoute
import com.example.rnd_transit_mtl.model.TransportType
import com.example.rnd_transit_mtl.ui.TripPlannerContent

/**
 * Owns all user-editable trip state and supplies it to the stateless trip planner.
 * Route selections are stored independently so every route-based transport supports
 * selecting more than one route.
 */
@Composable
internal fun TransitOpeningScreen(
    transportTypes: List<TransportType>,
    transportRoutes: List<TransportRoute>
) {
    val routesByTransport = remember(transportRoutes) {
        transportRoutes.groupBy { it.transportTypeId }
    }
    var minutes by rememberSaveable { mutableIntStateOf(30) }
    var selectedTransportIds by rememberSaveable { mutableStateOf(listOf("walk")) }
    var selectedRouteIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var intensity by rememberSaveable { mutableStateOf(90f) }
    var savedTrips by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var showTripResults by rememberSaveable { mutableStateOf(false) }
    var validationMessage by rememberSaveable { mutableStateOf("") }

    TripPlannerContent(
        minutes = minutes,
        onMinutesChange = {
            minutes = it
            validationMessage = ""
        },
        transportTypes = transportTypes,
        transportRoutes = transportRoutes,
        selectedTransportIds = selectedTransportIds,
        selectedRouteIds = selectedRouteIds,
        savedTrips = savedTrips,
        showTripResults = showTripResults,
        onToggleTransport = { transportId ->
            selectedTransportIds = if (transportId in selectedTransportIds) {
                selectedTransportIds - transportId
            } else {
                selectedTransportIds + transportId
            }
            validationMessage = ""
        },
        onToggleRoute = { transportId, routeId ->
            selectedRouteIds = selectedRouteIds.toggled(routeId)
            val hasSelectedRoute = routesByTransport[transportId]
                .orEmpty()
                .any { it.id in selectedRouteIds }

            selectedTransportIds = if (!hasSelectedRoute) {
                selectedTransportIds - transportId
            } else if (transportId !in selectedTransportIds) {
                selectedTransportIds + transportId
            } else {
                selectedTransportIds
            }
            validationMessage = ""
        },
        intensity = intensity,
        onIntensityChange = {
            intensity = it
            validationMessage = ""
        },
        validationMessage = validationMessage,
        onTripResultsVisibilityChange = { shouldShowResults ->
            if (!shouldShowResults || savedTrips.isNotEmpty()) {
                showTripResults = shouldShowResults
            }
        },
        onRemoveTrip = { tripIndex ->
            savedTrips = savedTrips.filterIndexed { index, _ -> index != tripIndex }
            if (savedTrips.isEmpty()) {
                showTripResults = false
            }
        },
        onGo = {
            if (selectedTransportIds.isEmpty()) {
                validationMessage = "Choose at least one transport type."
            } else {
                val choices = selectedTransportIds.joinToString(", ") { transportId ->
                    val typeLabel = transportTypes
                        .first { it.id == transportId }
                        .label
                    val routeLabels = routesByTransport[transportId]
                        .orEmpty()
                        .filter { it.id in selectedRouteIds }
                        .map { it.label }
                    if (routeLabels.isEmpty()) typeLabel
                    else "$typeLabel ${routeLabels.joinToString("/")}"
                }
                val trip = "$minutes min by $choices with ${intensity.toInt()}% intensity."
                savedTrips = savedTrips + trip
                validationMessage = ""
                showTripResults = true
            }
        }
    )
}

/** Returns a new list with [value] added when absent or removed when already selected. */
private fun List<String>.toggled(value: String): List<String> =
    if (value in this) this - value else this + value
