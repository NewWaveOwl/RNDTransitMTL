package com.example.rnd_transit_mtl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.rnd_transit_mtl.data.MockTripGenerator
import com.example.rnd_transit_mtl.data.TripGenerationBounds
import com.example.rnd_transit_mtl.data.TripGenerationInput
import com.example.rnd_transit_mtl.data.TripGenerationResult
import com.example.rnd_transit_mtl.model.TransportRoute
import com.example.rnd_transit_mtl.model.TransportType
import com.example.rnd_transit_mtl.model.Trip
import com.example.rnd_transit_mtl.state.LocalTripsStore
import com.example.rnd_transit_mtl.state.TripActionResult
import com.example.rnd_transit_mtl.ui.TripPlannerContent

/** Saves planner selections and starts or resumes one generated mock trip. */
@Composable
internal fun TransitOpeningScreen(
    transportTypes: List<TransportType>,
    transportRoutes: List<TransportRoute>,
    onOpenCurrentTrip: ((Trip) -> Boolean)? = null,
    onOpenCompletedTrip: ((String) -> Boolean)? = null,
    isDestinationActive: Boolean = true,
    modifier: Modifier = Modifier
) {
    val tripsStore = LocalTripsStore.current
    val generator = remember { MockTripGenerator() }
    // Viewport is measured again after rotation; stored trip endpoints are unchanged.
    var generationBounds by remember { mutableStateOf<TripGenerationBounds?>(null) }

    var minutes by rememberSaveable { mutableStateOf(30) }
    // The displayed choice and generated snapshot share the same bounded value.
    val boundedMinutes = Trip.coercePlannedMinutes(minutes)

    var selectedTransportIds by rememberSaveable {
        mutableStateOf(
            if (transportTypes.any { it.id == "walk" }) {
                listOf("walk")
            } else {
                emptyList<String>()
            }
        )
    }

    var selectedRouteIds by rememberSaveable {
        mutableStateOf(emptyList<String>())
    }
    var expandedTransportId by rememberSaveable {
        mutableStateOf<String?>(null)
    }
    var intensity by rememberSaveable { mutableStateOf(90f) }
    var validationMessage by rememberSaveable {
        mutableStateOf("")
    }

    // Blocks overlapping GO actions; the store also prevents another active trip.
    var processingAction by remember { mutableStateOf(false) }
    // Save only the opening intent and temporary labels, not a second Trip.
    var pendingOpenTripId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingActionLabel by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingSummary by rememberSaveable { mutableStateOf<String?>(null) }

    val latestDestinationActive by rememberUpdatedState(
        isDestinationActive
    )
    val latestOpenCurrentTrip by rememberUpdatedState(
        onOpenCurrentTrip
    )
    val latestOpenCompletedTrip by rememberUpdatedState(
        onOpenCompletedTrip
    )

    val currentState by tripsStore.state
    val activeState = currentState.activeTrip
    val pendingReviewId = currentState.pendingReviewTripId
    val pendingReviewTrip = pendingReviewId?.let { id ->
        currentState.completedTrips.firstOrNull { it.id == id }
    }

    val actionLabel = when {
        activeState != null -> "Resume trip"
        pendingReviewId != null -> "100%"
        else -> "GO"
    }
    val activeSummary = activeState?.let {
        val percentage = (it.elapsedMillis.toFloat() / 10_000f * 100f)
            .toInt().coerceIn(0, 99)
        "Unfinished: ${it.trip.title} · $percentage%"
    }
    val completedSummary = pendingReviewTrip?.let {
        "Completed: ${it.title} · tap 100% to open your trip"
    }

    // Choosing another section cancels the opening intent, keeping the trip.
    LaunchedEffect(isDestinationActive) {
        if (!isDestinationActive) {
            pendingOpenTripId = null
            pendingActionLabel = null
            pendingSummary = null
        }
    }

    fun queueTripOpening(tripId: String) {
        pendingActionLabel = actionLabel
        pendingSummary = activeSummary ?: completedSummary
        pendingOpenTripId = tripId
    }

    fun finishPlannerExit() {
        val tripId = pendingOpenTripId ?: return
        if (!latestDestinationActive) return

        // Resolve the current stored record after the actual animation finishes.
        val active = tripsStore.activeTrip?.takeIf { it.id == tripId }
        val completed = tripsStore.findCompleted(tripId)
        val handled = when {
            active != null -> latestOpenCurrentTrip?.invoke(active) == true
            completed != null -> latestOpenCompletedTrip?.invoke(tripId) == true
            else -> false
        }

        pendingOpenTripId = null
        pendingActionLabel = null
        pendingSummary = null
        if (!handled) {
            validationMessage = if (active == null && completed == null) {
                "This trip is no longer available. Please try GO again."
            } else {
                "Your trip is preserved. Tap the trip button to open it again."
            }
        }
    }

    fun canChangeInputs(): Boolean =
        latestDestinationActive && !processingAction && pendingOpenTripId == null

    fun clearValidation() {
        validationMessage = ""
    }

    fun performPrimaryAction() {
        if (!latestDestinationActive || processingAction || pendingOpenTripId != null) return

        processingAction = true

        try {
            // Re-read the store at click time, including before generation.
            val existingActive = tripsStore.activeTrip
            if (existingActive != null) {
                val openTrip = latestOpenCurrentTrip
                if (openTrip == null) {
                    validationMessage = "Current Trip navigation is unavailable."
                    return
                }
                validationMessage = ""

                queueTripOpening(existingActive.id)
                return
            }

            val pendingId = tripsStore.pendingReviewTripId
            if (pendingId != null) {
                val openCompleted = latestOpenCompletedTrip
                if (openCompleted == null) {
                    validationMessage = "Your completed trip is saved. Try opening it again."
                } else {
                    validationMessage = ""
                    queueTripOpening(pendingId)
                }
                return
            }

            val openTrip = latestOpenCurrentTrip
            if (openTrip == null) {
                validationMessage = "Current Trip navigation is unavailable."
                return
            }

            val bounds = generationBounds
            if (bounds == null) {
                validationMessage = "The map is preparing. Please try GO again."
                return
            }
            validationMessage = ""

            val input = TripGenerationInput(
                title = "Trip ${tripsStore.completedTrips.size + 1}",
                description = "Random mock route with $boundedMinutes planned minutes.",
                imageUrl = "",
                plannedMinutes = boundedMinutes,
                selectedTransportIds = selectedTransportIds.toList(),
                selectedRouteIds = selectedRouteIds.toList(),
                attractionIntensity = intensity
            )

            val existingIds = buildSet {
                addAll(tripsStore.completedTrips.map { it.id })
                tripsStore.activeTrip?.let { add(it.id) }
            }

            when (
                val generated = generator.generate(
                    input = input,
                    transportTypes = transportTypes,
                    transportRoutes = transportRoutes,
                    existingTripIds = existingIds,
                    bounds = bounds
                )
            ) {
                is TripGenerationResult.InvalidInput -> {
                    validationMessage = generated.message
                }

                TripGenerationResult.IdUnavailable -> {
                    validationMessage =
                        "A unique trip ID could not be created. Please try again."
                }

                is TripGenerationResult.Success -> {
                    when (val started = tripsStore.start(generated.trip)) {
                        TripActionResult.Applied -> {
                            queueTripOpening(generated.trip.id)
                        }

                        is TripActionResult.ActiveTripExists -> {
                            validationMessage =
                                "An unfinished trip already exists. Use Resume trip."
                        }

                        is TripActionResult.InvalidInput -> {
                            validationMessage = started.message
                        }

                        else -> {
                            validationMessage =
                                "The trip could not be started. Please try again."
                        }
                    }
                }
            }
        } finally {
            processingAction = false
        }
    }

    val actionHasCallback = if (activeState == null && pendingReviewId != null) {
        onOpenCompletedTrip != null
    } else {
        onOpenCurrentTrip != null
    }

    TripPlannerContent(
        minutes = boundedMinutes,
        onMinutesChange = {
            if (canChangeInputs()) {
                minutes = Trip.coercePlannedMinutes(it)
                clearValidation()
            }
        },
        transportTypes = transportTypes,
        transportRoutes = transportRoutes,
        selectedTransportIds = selectedTransportIds,
        selectedRouteIds = selectedRouteIds,
        expandedTransportId = expandedTransportId,
        onExpandedTransportChange = { transportId ->
            if (
                canChangeInputs() &&
                transportTypes.any {
                    it.id == transportId && it.usesRoutes
                }
            ) {
                expandedTransportId =
                    if (expandedTransportId == transportId) {
                        null
                    } else {
                        transportId
                    }
            }
        },
        onToggleTransport = { transportId ->
            val transport = transportTypes.firstOrNull {
                it.id == transportId
            }

            if (
                canChangeInputs() &&
                transport != null &&
                !transport.usesRoutes
            ) {
                selectedTransportIds =
                    selectedTransportIds.toggledPlannerId(transportId)
                clearValidation()
            }
        },
        onToggleRoute = { transportId, routeId ->
            val transport = transportTypes.firstOrNull {
                it.id == transportId && it.usesRoutes
            }
            val route = transportRoutes.firstOrNull {
                it.id == routeId &&
                        it.transportTypeId == transportId
            }

            if (
                canChangeInputs() &&
                transport != null &&
                route != null
            ) {
                selectedRouteIds =
                    selectedRouteIds.toggledPlannerId(routeId)

                val hasSelectedRoute = transportRoutes.any {
                    it.transportTypeId == transportId &&
                            it.id in selectedRouteIds
                }

                selectedTransportIds = if (hasSelectedRoute) {
                    if (transportId in selectedTransportIds) {
                        selectedTransportIds
                    } else {
                        selectedTransportIds + transportId
                    }
                } else {
                    selectedTransportIds.filterNot {
                        it == transportId
                    }
                }

                clearValidation()
            }
        },
        intensity = intensity,
        onIntensityChange = {
            if (canChangeInputs() && it.isFinite()) {
                intensity = it.coerceIn(
                    Trip.MIN_ATTRACTION_INTENSITY,
                    Trip.MAX_ATTRACTION_INTENSITY
                )
                clearValidation()
            }
        },
        validationMessage = validationMessage,
        actionLabel = pendingActionLabel ?: actionLabel,
        actionEnabled = isDestinationActive &&
                !processingAction &&
                pendingOpenTripId == null &&
                actionHasCallback &&
                (activeState != null || pendingReviewId != null || generationBounds != null),
        activeTripSummary = if (pendingOpenTripId != null) pendingSummary else activeSummary,
        pendingReviewSummary = if (pendingOpenTripId != null) null else completedSummary,
        onPrimaryAction = { performPrimaryAction() },
        controlsVisible = isDestinationActive && pendingOpenTripId == null,
        onControlsHidden = { finishPlannerExit() },
        onMapViewportReady = { generationBounds = it },
        modifier = modifier
    )
}

private fun List<String>.toggledPlannerId(id: String): List<String> =
    if (id in this) {
        filterNot { it == id }
    } else {
        this + id
    }
