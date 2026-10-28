package com.example.rnd_transit_mtl.View.main

import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.rnd_transit_mtl.View.current_trip.ActiveMapContent
import com.example.rnd_transit_mtl.state.trip.TRIP_SIMULATION_DURATION_MILLIS
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.rnd_transit_mtl.data.generation.MockTripGenerator
import com.example.rnd_transit_mtl.data.generation.TripGenerationBounds
import com.example.rnd_transit_mtl.data.generation.TripGenerationInput
import com.example.rnd_transit_mtl.data.generation.TripGenerationResult
import com.example.rnd_transit_mtl.model.transport.TransportRoute
import com.example.rnd_transit_mtl.model.transport.TransportType
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.state.trip.LocalTripsStore
import com.example.rnd_transit_mtl.state.trip.TripActionResult
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.rnd_transit_mtl.ViewModels.main.MainPlannerState
import com.example.rnd_transit_mtl.ViewModels.main.MainPlannerViewModel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.example.rnd_transit_mtl.navigation.LocalNavigator
import com.example.rnd_transit_mtl.navigation.LocalTripNavigation
import com.example.rnd_transit_mtl.navigation.MainScreenKey
import com.example.rnd_transit_mtl.navigation.SettingsScreenKey

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

    // This serialized value is a restoration snapshot.
    // The ViewModel remains the authoritative runtime state owner.
    var savedPlannerDraft by rememberSaveable {
        mutableStateOf(Json.encodeToString(MainPlannerState()))
    }

    val plannerViewModel = viewModel<MainPlannerViewModel> {
        MainPlannerViewModel(
            initialState = runCatching {
                Json.decodeFromString<MainPlannerState>(savedPlannerDraft)
            }.getOrDefault(MainPlannerState())
        )
    }

    val planner by plannerViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(plannerViewModel) {
        plannerViewModel.uiState.collect { state ->
            savedPlannerDraft = Json.encodeToString(state)
        }
    }

    val boundedMinutes = Trip.coercePlannedMinutes(planner.minutes)
    val selectedTransportIds = planner.selectedTransportIds
    val intensity = planner.intensity
    var validationMessage by rememberSaveable {
        mutableStateOf("")
    }

    // Blocks overlapping GO actions; the store also prevents another active trip.
    var processingAction by remember { mutableStateOf(false) }
    val generationScope = rememberCoroutineScope()
    var generationJob by remember { mutableStateOf<Job?>(null) }

    // Store a proposal separately. It becomes an active trip only after Start Trip.
    var proposalJson by rememberSaveable { mutableStateOf<String?>(null) }
    val proposal = remember(proposalJson) {
        proposalJson?.let { Json.decodeFromString<Trip>(it) }
    }
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
            generationJob?.cancel()
            proposalJson = null
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

        generationJob = generationScope.launch {
            try {
                // Re-read the store at click time, including before generation.
                val existingActive = tripsStore.activeTrip
                if (existingActive != null) {
                    val openTrip = latestOpenCurrentTrip
                    if (openTrip == null) {
                        validationMessage = "Current Trip navigation is unavailable."
                        return@launch
                    }
                    validationMessage = ""

                    queueTripOpening(existingActive.id)
                    return@launch
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
                    return@launch
                }

                val openTrip = latestOpenCurrentTrip
                if (openTrip == null) {
                    validationMessage = "Current Trip navigation is unavailable."
                    return@launch
                }

                val bounds = generationBounds
                if (bounds == null) {
                    validationMessage = "The map is preparing. Please try GO again."
                    return@launch
                }
                validationMessage = ""

                val transitModes = transportTypes.filter {
                    it.usesRoutes && it.id in planner.selectedTransportIds
                }
                val zonesByTransport = transitModes.associate { it.id to planner.zonesForTransport(it.id).toList() }
                val missingZones = transitModes.firstOrNull { zonesByTransport[it.id].isNullOrEmpty() }
                if (missingZones != null) {
                    validationMessage = "Choose at least one transit zone for ${missingZones.label}."
                    return@launch
                }

                val input = TripGenerationInput(
                    title = "Trip ${tripsStore.completedTrips.size + 1}",
                    description = "Random mock route with $boundedMinutes planned minutes.",
                    imageUrl = "",
                    plannedMinutes = boundedMinutes,
                    selectedTransportIds = selectedTransportIds.toList(),
                    selectedRouteIds = emptyList(),
                    selectedZoneIds = zonesByTransport.values.flatten().distinct().sorted(),
                    selectedZonesByTransport = zonesByTransport,
                    attractionIntensity = intensity
                )

                val existingIds = buildSet {
                    addAll(tripsStore.completedTrips.map { it.id })
                    tripsStore.activeTrip?.let { add(it.id) }
                }

                when (
                    val generated = withContext(Dispatchers.Default) {
                        generator.generate(
                        input = input,
                        transportTypes = transportTypes,
                        transportRoutes = transportRoutes,
                        existingTripIds = existingIds,
                        bounds = bounds
                        )
                    }
                ) {
                    is TripGenerationResult.InvalidInput -> {
                        validationMessage = generated.message
                    }

                    TripGenerationResult.IdUnavailable -> {
                        validationMessage =
                            "A unique trip ID could not be created. Please try again."
                    }

                    is TripGenerationResult.Success -> {
                        proposalJson = Json.encodeToString(generated.trip)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                validationMessage = "Unable to generate a trip. Please try again."
            } finally {
                processingAction = false
            }
        }
    }

    val actionHasCallback = if (activeState == null && pendingReviewId != null) {
        onOpenCompletedTrip != null
    } else {
        onOpenCurrentTrip != null
    }

    val navigator = LocalNavigator.current
    val navigation = LocalTripNavigation.current
    val copyRequest = navigation.plannerCopyRequest
    var copiedTripToGenerate by remember { mutableStateOf<Trip?>(null) }
    LaunchedEffect(copyRequest, generationBounds, isDestinationActive) {
        if (copyRequest != null && isDestinationActive && generationBounds != null) {
            val saved = tripsStore.findCompleted(copyRequest.first)
            navigation.consumePlannerCopy()
            if (saved != null) {
                proposalJson = null
                plannerViewModel.copyTripPreferences(saved)
                if (copyRequest.second) copiedTripToGenerate = saved
            }
        }
    }
    LaunchedEffect(planner, copiedTripToGenerate, isDestinationActive) {
        val saved = copiedTripToGenerate
        if (saved != null && isDestinationActive &&
            planner.minutes == saved.plannedMinutes &&
            planner.selectedTransportIds == saved.selectedTransports.map { it.id } &&
            planner.selectedZoneIds == saved.selectedZoneIds &&
            planner.selectedZonesByTransport == saved.selectedZonesByTransport &&
            planner.intensity == saved.attractionIntensity) {
            copiedTripToGenerate = null
            performPrimaryAction()
        }
    }

    if (processingAction &&
        activeState == null &&
        pendingReviewId == null
    ) {
        TripLoadingContent(
            minutes = boundedMinutes,
            transportSummary = transportTypes
                .filter { it.id in selectedTransportIds }
                .joinToString(" and ") { it.label },
            onCancel = {
                generationJob?.cancel()
            },
            modifier = modifier
        )
        return
    }

    if (proposal != null) {
        TripProposalContent(
            trip = proposal,
            onStart = {
                if (latestDestinationActive &&
                    !processingAction &&
                    pendingOpenTripId == null
                ) {
                    when (val result = tripsStore.start(proposal)) {
                        TripActionResult.Applied -> {
                            proposalJson = null
                            queueTripOpening(proposal.id)
                        }

                        is TripActionResult.ActiveTripExists -> {
                            proposalJson = null
                            validationMessage =
                                "An unfinished trip already exists. Use Resume trip."
                        }

                        is TripActionResult.InvalidInput -> {
                            proposalJson = null
                            validationMessage = result.message
                        }

                        else -> {
                            proposalJson = null
                            validationMessage =
                                "The trip could not be started. Please try again."
                        }
                    }
                }
            },
            onGenerateAnother = {
                proposalJson = null
                performPrimaryAction()
            },
            onEditSettings = {
                // Return to the planner with all current choices preserved.
                proposalJson = null
            },
            modifier = modifier
        )
        return
    }

    if (activeState != null && pendingOpenTripId == null) {
        ActiveMapContent(
            trip = activeState.trip,
            progress = (
                activeState.elapsedMillis.toFloat() /
                    TRIP_SIMULATION_DURATION_MILLIS
            ).coerceIn(0f, 1f),
            actionsEnabled = isDestinationActive && !processingAction,
            mainOverview = true,
            onResume = { performPrimaryAction() },
            onFinish = {},
            onConfirmCancel = {},
            modifier = modifier
        )
        return
    }

    MainPlannerContent(
        minutes = boundedMinutes,
        onMinutesChange = {
            if (canChangeInputs()) {
                plannerViewModel.setMinutes(it)
                clearValidation()
            }
        },
        transportTypes = transportTypes,
        selectedTransportIds = selectedTransportIds,
        selectedZonesByTransport = transportTypes.filter { it.usesRoutes }.associate {
            it.id to planner.zonesForTransport(it.id)
        },
        onToggleTransport = { id ->
            if (canChangeInputs()) {
                plannerViewModel.toggleTransport(
                    id = id,
                    availableIds = transportTypes.map { it.id }.toSet()
                )
                clearValidation()
            }
        },
        onToggleZone = { transportId, id ->
            if (canChangeInputs()) {
                plannerViewModel.toggleZone(transportId, id)
                clearValidation()
            }
        },
        intensity = intensity,
        walkingPreference = planner.walkingPreference,
        bikeSpeedKmh = planner.bikeSpeedKmh,
        onBikeSpeedChange = { if (canChangeInputs()) plannerViewModel.setBikeSpeedKmh(it) },
        selectedAttractionIds = planner.selectedAttractionIds,
        onWalkingPreferenceChange = {
            if (canChangeInputs()) plannerViewModel.setWalkingPreference(it)
        },
        onToggleAttraction = {
            if (canChangeInputs()) plannerViewModel.toggleAttraction(it)
        },
        onIntensityChange = {
            if (canChangeInputs()) {
                plannerViewModel.setIntensity(it)
                clearValidation()
            }
        },
        validationMessage = validationMessage,
        actionLabel = pendingActionLabel ?: actionLabel,
        actionEnabled = isDestinationActive &&
            !processingAction &&
            pendingOpenTripId == null &&
            actionHasCallback &&
            (
                activeState != null ||
                pendingReviewId != null ||
                generationBounds != null
            ),
        inputsEnabled = canChangeInputs(),
        activeTripSummary = if (pendingOpenTripId != null) {
            pendingSummary
        } else {
            activeSummary
        },
        pendingReviewSummary = if (pendingOpenTripId != null) {
            null
        } else {
            completedSummary
        },
        onPrimaryAction = { performPrimaryAction() },
        onOpenDetailedSettings = {
            if (
                canChangeInputs() &&
                navigator.current == MainScreenKey
            ) {
                navigation.openSection(SettingsScreenKey)
            }
        },
        controlsVisible =
            isDestinationActive && pendingOpenTripId == null,
        onControlsHidden = { finishPlannerExit() },
        onMapViewportReady = { generationBounds = it },
        modifier = modifier
    )
}
