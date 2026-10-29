package com.example.rnd_transit_mtl.View.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.example.rnd_transit_mtl.View.current_trip.ActiveMapContent
import com.example.rnd_transit_mtl.ViewModels.main.MainPlannerState
import com.example.rnd_transit_mtl.ViewModels.main.MainPlannerViewModel
import com.example.rnd_transit_mtl.data.generation.MockTripGenerator
import com.example.rnd_transit_mtl.data.generation.TripGenerationBounds
import com.example.rnd_transit_mtl.data.generation.TripGenerationInput
import com.example.rnd_transit_mtl.data.generation.TripGenerationResult
import com.example.rnd_transit_mtl.model.transport.TransportRoute
import com.example.rnd_transit_mtl.model.transport.TransportType
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.model.trip.TripPoint
import com.example.rnd_transit_mtl.navigation.LocalNavigator
import com.example.rnd_transit_mtl.navigation.LocalTripNavigation
import com.example.rnd_transit_mtl.navigation.MainScreenKey
import com.example.rnd_transit_mtl.navigation.SettingsScreenKey
import com.example.rnd_transit_mtl.state.trip.LocalTripsStore
import com.example.rnd_transit_mtl.state.trip.TRIP_SIMULATION_DURATION_MILLIS
import com.example.rnd_transit_mtl.state.trip.TripActionResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Saves planner selections and starts or resumes one generated mock trip.
 * @param transportTypes Supported mode catalogue.
 * @param transportRoutes Supported route catalogue.
 * @param onOpenCurrentTrip Requests active-trip navigation after the planner exit.
 * @param onOpenCompletedTrip Requests saved completion navigation.
 * @param isDestinationActive Whether this destination accepts actions.
 * @param modifier Parent layout constraints.
 * @param sampleOutcome Explicit preview/test fixture; Normal is used by the application.
 */
@Composable
internal fun TransitOpeningScreen(
    transportTypes: List<TransportType>,
    transportRoutes: List<TransportRoute>,
    onOpenCurrentTrip: ((Trip) -> Boolean)? = null,
    onOpenCompletedTrip: ((String) -> Boolean)? = null,
    isDestinationActive: Boolean = true,
    modifier: Modifier = Modifier,
    sampleOutcome: Trip33Sample = Trip33Sample.Normal
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

    val plannerPresentationState = rememberSaveableStateHolder()

    var originMode by rememberSaveable {
        mutableStateOf("planner")
    }
    var originDraftX by rememberSaveable {
        mutableStateOf<Float?>(null)
    }
    var originDraftY by rememberSaveable {
        mutableStateOf<Float?>(null)
    }
    var originMessage by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(isDestinationActive) {
        if (!isDestinationActive) {
            originMode = "planner"
            originMessage = null
        }
    }

    LaunchedEffect(
        isDestinationActive,
        planner.origin == null,
        planner.locationAccessOff
    ) {
        if (isDestinationActive &&
            planner.origin == null &&
            planner.locationAccessOff
        ) {
            originMode = "options"
        }
    }


    LaunchedEffect(plannerViewModel) {
        plannerViewModel.uiState.collect { state ->
            savedPlannerDraft = Json.encodeToString(state)
        }
    }

    val boundedMinutes = Trip.coercePlannedMinutes(planner.minutes)
    val selectedTransportIds = planner.selectedTransportIds
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
    var failureName by rememberSaveable {
        mutableStateOf<String?>(null)
    }
    var failureMessage by rememberSaveable {
        mutableStateOf("")
    }
    var requestSummary by rememberSaveable {
        mutableStateOf("")
    }
    var pickingOrigin by rememberSaveable {
        mutableStateOf(false)
    }

    val failure = Trip33Failure.entries.firstOrNull {
        it.name == failureName
    }

    val proposalGuard = remember {
        ProposalGuard(
            initialProposalId = if (failureName == null) proposal?.id else null
        )
    }

    fun closeProposalFlow() {
        proposalGuard.cancel()
        generationJob?.cancel()
        generationJob = null
        processingAction = false
        proposalJson = null
        failureName = null
        failureMessage = ""
        pickingOrigin = false
        originMode = "planner"
        originMessage = null
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
        val percentage = (it.elapsedMillis.toFloat() / TRIP_SIMULATION_DURATION_MILLIS * 100f)
            .toInt().coerceIn(0, 99)
        "Unfinished: ${it.trip.title} · $percentage%"
    }
    val completedSummary = pendingReviewTrip?.let {
        "Completed: ${it.title} · tap 100% to open your trip"
    }

    // Choosing another section cancels the opening intent, keeping the trip.
    LaunchedEffect(isDestinationActive) {
        if (!isDestinationActive) {
            closeProposalFlow()
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
        if (!latestDestinationActive ||
            processingAction ||
            pendingOpenTripId != null
        ) return

        tripsStore.activeTrip?.let { active ->
            if (latestOpenCurrentTrip != null) {
                queueTripOpening(active.id)
            } else {
                validationMessage = "Current Trip navigation is unavailable."
            }
            return
        }

        tripsStore.pendingReviewTripId?.let { completedId ->
            if (latestOpenCompletedTrip != null) {
                queueTripOpening(completedId)
            } else {
                validationMessage = "Completed Trip navigation is unavailable."
            }
            return
        }

        val token = proposalGuard.begin()
        proposalJson = null
        failureName = null
        failureMessage = ""
        validationMessage = ""
        processingAction = true

        val modes = transportTypes
            .filter { it.id in planner.selectedTransportIds }
            .joinToString(" + ") { it.label }

        requestSummary =
            "$boundedMinutes minutes · $modes · " +
                (planner.origin?.label ?: "Choose starting point")

        generationJob = generationScope.launch {
            fun fail(kind: Trip33Failure, message: String = "") {
                if (proposalGuard.complete(token, null)) {
                    proposalJson = null
                    failureName = kind.name
                    failureMessage = message
                }
            }

            try {
                val bounds = generationBounds
                if (bounds == null) {
                    fail(
                        Trip33Failure.InvalidInput,
                        "The map is preparing. Please try again."
                    )
                    return@launch
                }

                val origin = planner.origin
                if (origin == null ||
                    (sampleOutcome == Trip33Sample.PermissionDenied &&
                        !planner.originIsManual)
                ) {
                    fail(Trip33Failure.PermissionDenied)
                    return@launch
                }

                val transitModes = transportTypes.filter {
                    it.usesRoutes && it.id in planner.selectedTransportIds
                }

                val zones = transitModes.associate {
                    it.id to planner.zonesForTransport(it.id).toList()
                }

                val missingZones = transitModes.firstOrNull {
                    zones[it.id].isNullOrEmpty()
                }

                if (missingZones != null) {
                    fail(
                        Trip33Failure.InvalidInput,
                        "Choose at least one transit zone for ${missingZones.label}."
                    )
                    return@launch
                }

                val input = TripGenerationInput(
                    title = "Trip ${tripsStore.completedTrips.size + 1}",
                    description = "Random mock route with $boundedMinutes planned minutes.",
                    imageUrl = "",
                    plannedMinutes = boundedMinutes,
                    selectedTransportIds = planner.selectedTransportIds.toList(),
                    selectedRouteIds = emptyList(),
                    attractionIntensity = planner.intensity,
                    selectedZoneIds = zones.values.flatten().distinct().sorted(),
                    selectedZonesByTransport = zones,
                    origin = origin.copy()
                )

                // Explicit samples are distinct from validation failures.
                when (sampleOutcome) {
                    Trip33Sample.NoRoute -> {
                        fail(Trip33Failure.NoRoute)
                        return@launch
                    }
                    Trip33Sample.ProviderError -> {
                        fail(Trip33Failure.ProviderUnavailable)
                        return@launch
                    }
                    else -> Unit
                }

                val existingIds = buildSet {
                    addAll(tripsStore.completedTrips.map { it.id })
                    tripsStore.activeTrip?.let { add(it.id) }
                }

                val generated = withContext(Dispatchers.Default) {
                    generator.generate(
                        input = input,
                        transportTypes = transportTypes,
                        transportRoutes = transportRoutes,
                        existingTripIds = existingIds,
                        bounds = bounds
                    )
                }

                when (generated) {
                    is TripGenerationResult.Success -> {
                        val encoded = Json.encodeToString(generated.trip)

                        if (latestDestinationActive &&
                            proposalGuard.complete(token, generated.trip.id)
                        ) {
                            proposalJson = encoded
                        }
                    }

                    is TripGenerationResult.InvalidInput -> {
                        fail(
                            Trip33Failure.InvalidInput,
                            generated.message
                        )
                    }

                    TripGenerationResult.IdUnavailable -> {
                        fail(
                            Trip33Failure.InvalidInput,
                            "A unique trip ID could not be created. Try again."
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // An unexpected mock-generation exception is not evidence
                // that a real routing provider is unavailable.
                fail(
                    Trip33Failure.InvalidInput,
                    "The sample request could not finish. Please try again."
                )
            } finally {
                if (proposalGuard.isCurrent(token)) {
                    processingAction = false
                }
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
                closeProposalFlow()
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
            planner.origin == saved.start &&
            planner.intensity == saved.attractionIntensity) {
            copiedTripToGenerate = null
            performPrimaryAction()
        }
    }

    fun closeCurrentProposalScreen() {
        if (pickingOrigin) {
            pickingOrigin = false
        } else {
            closeProposalFlow()
        }
    }

    val proposalScreenVisible =
        processingAction || proposal != null || failure != null

    val proposalBackState = rememberNavigationEventState(
        currentInfo = NavigationEventInfo.None
    )

    NavigationBackHandler(
        state = proposalBackState,
        isBackEnabled = isDestinationActive && proposalScreenVisible,
        onBackCompleted = { closeCurrentProposalScreen() }
    )

    val proposalModifier = modifier.onPreviewKeyEvent {
        if (isDestinationActive &&
            proposalScreenVisible &&
            it.type == KeyEventType.KeyDown &&
            it.key == Key.Escape
        ) {
            closeCurrentProposalScreen()
            true
        } else {
            false
        }
    }

    if (failure != null && isDestinationActive) {
        Trip33FallbackContent(
            failure = failure,
            detail = failureMessage,
            requestSummary = requestSummary,
            originLabel = planner.origin?.label ?: "Choose starting point",
            pickingOrigin = pickingOrigin,
            enabled = !processingAction,
            onChooseMap = {
                pickingOrigin = true
                failureMessage = ""
            },
            onConfirmOrigin = { point ->
                plannerViewModel.setOrigin(point)
                closeProposalFlow()
            },
            onRetry = {
                closeProposalFlow()
                performPrimaryAction()
            },
            onEditSettings = {
                closeProposalFlow()
            },
            onUnavailableAction = {
                failureMessage =
                    "Address search and live location are not connected " +
                        "in this sample. Choose a point on the map."
            },
            onCancelPicking = {
                pickingOrigin = false
            },
            sampleLabel = if (sampleOutcome != Trip33Sample.Normal) {
                "Sample · ${sampleOutcome.name}"
            } else {
                null
            },
            modifier = proposalModifier
        )
        return
    }

    if (processingAction &&
        activeState == null &&
        pendingReviewId == null
    ) {
        TripLoadingContent(
            origin = planner.origin,
            minutes = boundedMinutes,
            transportSummary = transportTypes
                .filter { it.id in selectedTransportIds }
                .joinToString(" and ") { it.label },
            onCancel = { closeProposalFlow() },
            modifier = proposalModifier
        )
        return
    }

    if (proposal != null) {
        TripProposalContent(
            trip = proposal,
            onStart = {
                if (latestDestinationActive &&
                    !processingAction &&
                    failure == null &&
                    proposalGuard.canStart(proposal.id) &&
                    pendingOpenTripId == null
                ) {
                    when (val result = proposalGuard.start(tripsStore, proposal)) {
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
                closeProposalFlow()
                performPrimaryAction()
            },
            onEditSettings = { closeProposalFlow() },
            modifier = proposalModifier
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

    if (originMode != "planner" &&
        isDestinationActive &&
        activeState == null &&
        pendingOpenTripId == null
    ) {
        val draftOrigin = if (
            originDraftX != null && originDraftY != null
        ) {
            TripPoint(
                "Selected point on map",
                originDraftX!!,
                originDraftY!!
            )
        } else {
            null
        }

        OriginSelectionContent(
            origin = if (originMode == "pick") draftOrigin else planner.origin,
            minutes = boundedMinutes,
            locationAccessOff = planner.locationAccessOff,
            picking = originMode == "pick",
            enabled = canChangeInputs(),
            message = originMessage,
            onPick = { point ->
                if (canChangeInputs()) {
                    originDraftX = point.x
                    originDraftY = point.y
                }
            },
            onChooseMap = {
                originDraftX = null
                originDraftY = null
                originMessage = null
                originMode = "pick"
            },
            onConfirm = {
                if (canChangeInputs() && draftOrigin != null) {
                    plannerViewModel.setOrigin(draftOrigin)
                    proposalJson = null
                    clearValidation()
                    originMode = "planner"
                    originMessage = null
                }
            },
            onSearch = {
                originMessage =
                    "Place and address search is not connected in this sample. " +
                        "Choose a point on the map."
            },
            onRetryLocation = {
                originMessage =
                    "Live location is not connected in this sample. " +
                        "Your choices are kept."
            },
            onBack = {
                originMessage = null
                originMode = if (originMode == "pick") "options" else "planner"
            },
            modifier = modifier
        )
        return
    }

    plannerPresentationState.SaveableStateProvider("main-planner") {
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
            selectedZonesByTransport = transportTypes
                .filter { it.usesRoutes }
                .associate { it.id to planner.zonesForTransport(it.id) },
            onToggleTransport = { id ->
                if (canChangeInputs()) {
                    plannerViewModel.toggleTransport(
                        id,
                        transportTypes.map { it.id }.toSet()
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
            walkingPreference = planner.walkingPreference,
            bikeSpeedKmh = planner.bikeSpeedKmh,
            onBikeSpeedChange = {
                if (canChangeInputs()) plannerViewModel.setBikeSpeedKmh(it)
            },
            selectedAttractionIds = planner.selectedAttractionIds,
            onWalkingPreferenceChange = {
                if (canChangeInputs()) {
                    plannerViewModel.setWalkingPreference(it)
                }
            },
            onToggleAttraction = {
                if (canChangeInputs()) plannerViewModel.toggleAttraction(it)
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
                        (generationBounds != null && planner.origin != null)
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
                if (canChangeInputs() && navigator.current == MainScreenKey) {
                    navigation.openSection(SettingsScreenKey)
                }
            },
            controlsVisible = isDestinationActive && pendingOpenTripId == null,
            onControlsHidden = { finishPlannerExit() },
            onMapViewportReady = { generationBounds = it },
            origin = planner.origin,
            onEditOrigin = {
                if (canChangeInputs()) {
                    originMessage = null
                    originMode = "options"
                }
            },
            modifier = modifier
        )
    }

}
