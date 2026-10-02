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
 * Owns trip selections, validation, and saved summaries for the shared planner.
 *
 * State is retained with rememberSaveable where supported by the saved-state host.
 * The GO action currently builds a text summary rather than calculating a travel route.
 *
 * @param transportTypes Available transport options in display order.
 * @param transportRoutes Available routes, each linked to its owning transport type.
 */
@Composable
internal fun TransitOpeningScreen(
    transportTypes: List<TransportType>,
    transportRoutes: List<TransportRoute>
) {
    /** Cache routes grouped by their owning transport type for selection checks and summary creation. */
    val routesByTransport = remember(transportRoutes) {
        transportRoutes.groupBy { it.transportTypeId }
    }

    /** Initialize saveable planner state with walking, 30 minutes, and 90% intensity selected. */
    var minutes by rememberSaveable { mutableIntStateOf(30) }
    var selectedTransportIds by rememberSaveable { mutableStateOf(listOf("walk")) }
    var selectedRouteIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var intensity by rememberSaveable { mutableStateOf(90f) }
    var savedTrips by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var showTripResults by rememberSaveable { mutableStateOf(false) }
    var validationMessage by rememberSaveable { mutableStateOf("") }

    TripPlannerContent(
        minutes = minutes,

        /** Changing a planner input clears previous validation feedback. */
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

        /** Add an unselected transport ID or remove an already selected one. */
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

            /** After toggling the route, check whether this transport still has any selected routes. */
            val hasSelectedRoute = routesByTransport[transportId]
                .orEmpty()
                .any { it.id in selectedRouteIds }

            /** Include route-based transport only while at least one of its routes remains selected. */
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
            /** Always allow returning to the planner, but open results only when a saved trip exists. */
            if (!shouldShowResults || savedTrips.isNotEmpty()) {
                showTripResults = shouldShowResults
            }
        },
        onRemoveTrip = { tripIndex ->
            /** Remove the requested zero-based position and return to the planner if no trips remain. */
            savedTrips = savedTrips.filterIndexed { index, _ -> index != tripIndex }
            if (savedTrips.isEmpty()) {
                showTripResults = false
            }
        },
        onGo = {
            /** A summary requires at least one selected transport type. */
            if (selectedTransportIds.isEmpty()) {
                validationMessage = "Choose at least one transport type."
            } else {
                /** Build readable transport labels and append only their selected route labels. */
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

                /** Append the new summary to a new list so Compose observes the state change. */
                val trip = "$minutes min by $choices with ${intensity.toInt()}% intensity."
                savedTrips = savedTrips + trip
                validationMessage = ""
                showTripResults = true
            }
        }
    )
}

/**
 * Adds an absent value or removes an already selected value from a new list.
 *
 * List addition and subtraction return a replacement list for Compose state updates.
 *
 * @receiver Current route selection list.
 *
 * @param value Route ID to add or remove.
 * @return A new selection list; the original list is unchanged.
 */
private fun List<String>.toggled(value: String): List<String> =
    if (value in this) this - value else this + value