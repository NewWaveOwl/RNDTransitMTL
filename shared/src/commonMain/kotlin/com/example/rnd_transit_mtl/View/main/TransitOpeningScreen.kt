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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.example.rnd_transit_mtl.View.current_trip.ActiveMapContent
import com.example.rnd_transit_mtl.View.current_trip.TripLocationState
import com.example.rnd_transit_mtl.ViewModels.main.MainPlannerState
import com.example.rnd_transit_mtl.ViewModels.main.MainPlannerViewModel
import com.example.rnd_transit_mtl.data.fake.FakeRoutingService
import com.example.rnd_transit_mtl.data.generation.LocalBackendTripGenerator
import com.example.rnd_transit_mtl.data.generation.MockTripGenerator
import com.example.rnd_transit_mtl.data.generation.TripGenerationBounds
import com.example.rnd_transit_mtl.data.generation.TripGenerationResult
import com.example.rnd_transit_mtl.data.location.LocalPlannerLocation
import com.example.rnd_transit_mtl.data.location.LocationUpdate
import com.example.rnd_transit_mtl.data.location.PlannerLocation
import com.example.rnd_transit_mtl.model.geo.GeoPoint
import com.example.rnd_transit_mtl.model.ids.TripId
import com.example.rnd_transit_mtl.model.transport.TransportRoute
import com.example.rnd_transit_mtl.model.transport.TransportType
import com.example.rnd_transit_mtl.model.trip.Place
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.model.trip.TripPoint
import com.example.rnd_transit_mtl.navigation.LocalNavigator
import com.example.rnd_transit_mtl.navigation.LocalTripNavigation
import com.example.rnd_transit_mtl.navigation.MainScreenKey
import com.example.rnd_transit_mtl.navigation.SettingsScreenKey
import com.example.rnd_transit_mtl.state.NullableTripIdSaver
import com.example.rnd_transit_mtl.state.debug.LocalDebugModeState
import com.example.rnd_transit_mtl.state.trip.LocalTripsStore
import com.example.rnd_transit_mtl.state.trip.TRIP_SIMULATION_DURATION_MILLIS
import com.example.rnd_transit_mtl.state.trip.TripActionResult
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.LocalTransitMap
import com.example.rnd_transit_mtl.ui.global_ui_ellements.map.TransitMapData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Timing settings — milliseconds before surfacing an automatic-origin timeout notice.
private const val AUTOMATIC_ORIGIN_TIMEOUT_MILLIS = 15_000L
private const val RECENT_DESTINATIONS_HISTORY_LIMIT = 10

/**
 * Saves planner selections and coordinates trip generation, proposals, origin picking, and active-trip resume.
 *
 * @param transportTypes Supported mode catalogue.
 * @param transportRoutes Supported route catalogue.
 * @param onOpenCurrentTrip Requests active-trip navigation after the planner exit.
 * @param onOpenCompletedTrip Requests saved completion navigation.
 * @param isDestinationActive Whether this destination accepts actions.
 * @param modifier Parent layout constraints.
 * @param sampleOutcome Explicit preview/test fixture; [Trip33Sample.Normal] is used by the application.
 */
@Composable
internal fun TransitOpeningScreen(
    transportTypes: List<TransportType>,
    transportRoutes: List<TransportRoute>,
    onOpenCurrentTrip: ((Trip) -> Boolean)? = null,
    onOpenCompletedTrip: ((TripId) -> Boolean)? = null,
    isDestinationActive: Boolean = true,
    modifier: Modifier = Modifier,
    sampleOutcome: Trip33Sample = Trip33Sample.Normal,
) {
    val tripsStore = LocalTripsStore.current
    val simulationEnabled = LocalDebugModeState.current?.simulationEnabled == true
    val liveMap = LocalTransitMap.current != null
    val location = LocalPlannerLocation.current
    val backendGenerator = LocalBackendTripGenerator.current
    var previousDestinations by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    val generator = remember(location?.containsPoint) {
        MockTripGenerator(routingService = FakeRoutingService(containsPoint = location?.containsPoint))
    }
    // Viewport is measured again after rotation; stored trip endpoints are unchanged.
    var generationBounds by remember { mutableStateOf<TripGenerationBounds?>(null) }

    // Serialized restoration snapshot; the ViewModel remains the authoritative runtime state owner.
    var savedPlannerDraft by rememberSaveable {
        mutableStateOf(Json.encodeToString(if (liveMap) MainPlannerState(origin = null) else MainPlannerState()))
    }
    val plannerViewModel = viewModel<MainPlannerViewModel> {
        MainPlannerViewModel(
            initialState = runCatching {
                Json.decodeFromString<MainPlannerState>(savedPlannerDraft)
            }.getOrDefault(MainPlannerState()),
        )
    }
    val planner by plannerViewModel.uiState.collectAsStateWithLifecycle()
    val plannerPresentationState = rememberSaveableStateHolder()

    var originMode by rememberSaveable { mutableStateOf("planner") }
    var liveDraft by remember { mutableStateOf<GeoPoint?>(null) }
    var originPickerFromMap by rememberSaveable { mutableStateOf(false) }
    var originDraftX by rememberSaveable { mutableStateOf<Float?>(null) }
    var originDraftY by rememberSaveable { mutableStateOf<Float?>(null) }
    var originMessage by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(isDestinationActive) {
        if (!isDestinationActive) {
            originMode = "planner"
            originMessage = null
        }
    }
    LaunchedEffect(isDestinationActive, planner.origin == null, planner.locationAccessOff) {
        if (isDestinationActive && planner.origin == null && planner.locationAccessOff) {
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
    var validationMessage by rememberSaveable { mutableStateOf("") }

    // Blocks overlapping GO actions; the store also prevents another active trip.
    var processingAction by remember { mutableStateOf(false) }
    val generationScope = rememberCoroutineScope()
    var generationJob by remember { mutableStateOf<Job?>(null) }

    // Store a proposal separately. It becomes an active trip only after Start Trip.
    var proposalJson by rememberSaveable { mutableStateOf<String?>(null) }
    val proposal = remember(proposalJson) { proposalJson?.let { Json.decodeFromString<Trip>(it) } }
    var failureName by rememberSaveable { mutableStateOf<String?>(null) }
    var failureMessage by rememberSaveable { mutableStateOf("") }
    var requestSummary by rememberSaveable { mutableStateOf("") }
    var pickingOrigin by rememberSaveable { mutableStateOf(false) }
    val failure = Trip33Failure.entries.firstOrNull { it.name == failureName }
    val proposalGuard = remember {
        ProposalGuard(initialProposalId = proposal?.id)
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
        liveDraft = null
        originDraftX = null
        originDraftY = null
        originPickerFromMap = false
    }

    // Save only the opening intent and temporary labels, not a second Trip.
    var pendingOpenTripId by rememberSaveable(stateSaver = NullableTripIdSaver) { mutableStateOf<TripId?>(null) }
    var pendingActionLabel by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingSummary by rememberSaveable { mutableStateOf<String?>(null) }

    val latestDestinationActive by rememberUpdatedState(isDestinationActive)
    val latestOpenCurrentTrip by rememberUpdatedState(onOpenCurrentTrip)
    val latestOpenCompletedTrip by rememberUpdatedState(onOpenCompletedTrip)

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

    fun queueTripOpening(tripId: TripId) {
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

    var defaultOriginStatus by remember { mutableStateOf<String?>(null) }
    AutomaticOriginUpdatesEffect(
        location = location,
        enabled = isDestinationActive && canChangeInputs() && originMode == "planner" && !planner.originIsManual,
        canApplyFix = { canChangeInputs() && originMode == "planner" },
        onStatusChanged = { defaultOriginStatus = it },
        onDefaultOriginFound = plannerViewModel::setDefaultOrigin,
    )

    fun clearValidation() {
        validationMessage = ""
    }

    fun performPrimaryAction() {
        if (!latestDestinationActive || processingAction || generationJob?.isActive == true || pendingOpenTripId != null) {
            return
        }
        tripsStore.activeTrip?.let { active ->
            if (latestOpenCurrentTrip != null) {
                queueTripOpening(active.id)
                if (liveMap) finishPlannerExit()
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
        failureName = null
        failureMessage = ""
        validationMessage = ""
        processingAction = true

        val modes = transportTypes
            .filter { it.id in planner.selectedTransportIds }
            .joinToString(" + ") { it.label }
        requestSummary = "$boundedMinutes minutes · $modes · " +
            (planner.originPlace?.label ?: planner.origin?.label ?: "Choose starting point")

        generationJob = generationScope.launch {
            fun fail(kind: Trip33Failure, message: String = "") {
                if (proposalGuard.complete(token, null)) {
                    failureName = kind.name
                    failureMessage = message.ifBlank { "No new trip could be generated. Your last trip is still available." }
                }
            }

            try {
                when (
                    val prepared = prepareTripGenerationInput(
                        planner = planner,
                        boundedMinutes = boundedMinutes,
                        liveMap = liveMap,
                        generationBounds = generationBounds,
                        location = location,
                        transportTypes = transportTypes,
                        completedTripsCount = tripsStore.completedTrips.size,
                        sampleOutcome = sampleOutcome,
                        onDefaultOriginResolved = plannerViewModel::setDefaultOrigin,
                    )
                ) {
                    is PreparedTripGeneration.Failed -> fail(prepared.kind, prepared.message)
                    is PreparedTripGeneration.Ready -> {
                        val generated = executeTripGeneration(
                            ready = prepared,
                            liveMap = liveMap,
                            backendGenerator = backendGenerator,
                            mockGenerator = generator,
                            tripsStore = tripsStore,
                            transportTypes = transportTypes,
                            transportRoutes = transportRoutes,
                            previousDestinations = previousDestinations,
                        )
                        when (generated) {
                            is TripGenerationResult.Success -> {
                                val encoded = Json.encodeToString(generated.trip)
                                if (latestDestinationActive && proposalGuard.complete(token, generated.trip.id)) {
                                    previousDestinations = (
                                        previousDestinations + Json.encodeToString(generated.trip.destination.point)
                                    ).takeLast(RECENT_DESTINATIONS_HISTORY_LIMIT)
                                    proposalJson = encoded
                                }
                            }

                            is TripGenerationResult.InvalidInput -> fail(Trip33Failure.InvalidInput, generated.message)
                            TripGenerationResult.IdUnavailable -> fail(
                                Trip33Failure.InvalidInput,
                                "A unique trip ID could not be created. Try again.",
                            )
                            TripGenerationResult.NoRoute -> fail(Trip33Failure.NoRoute)
                            is TripGenerationResult.RoutingUnavailable -> fail(
                                Trip33Failure.ProviderUnavailable,
                                generated.message,
                            )
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                fail(Trip33Failure.ProviderUnavailable, "The trip request could not finish. Please try again.")
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
            val saved = tripsStore.findInHistory(copyRequest.first)
            navigation.consumePlannerCopy()
            if (saved != null) {
                closeProposalFlow()
                plannerViewModel.copyTripPreferences(saved, liveMap)
                if (copyRequest.second) copiedTripToGenerate = saved
            }
        }
    }
    LaunchedEffect(planner, copiedTripToGenerate, isDestinationActive) {
        val saved = copiedTripToGenerate
        if (saved != null && isDestinationActive && plannerMatchesSavedTrip(planner, saved, liveMap)) {
            copiedTripToGenerate = null
            performPrimaryAction()
        }
    }

    fun closeCurrentProposalScreen() {
        if (pickingOrigin) pickingOrigin = false else closeProposalFlow()
    }

    val proposalScreenVisible = processingAction || proposal != null || failure != null
    val pickingLiveOriginFromFailure = liveMap && failure != null && pickingOrigin
    val proposalBackState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    NavigationBackHandler(
        state = proposalBackState,
        isBackEnabled = isDestinationActive && proposalScreenVisible && !pickingLiveOriginFromFailure,
        onBackCompleted = { closeCurrentProposalScreen() },
    )

    val proposalModifier = modifier.onPreviewKeyEvent {
        if (isDestinationActive && proposalScreenVisible && it.type == KeyEventType.KeyDown && it.key == Key.Escape) {
            closeCurrentProposalScreen()
            true
        } else {
            false
        }
    }

    if (failure != null && proposal == null && isDestinationActive && !pickingLiveOriginFromFailure) {
        Trip33FallbackContent(
            failure = failure,
            detail = failureMessage,
            requestSummary = requestSummary,
            originLabel = planner.originPlace?.label ?: planner.origin?.label ?: "Choose starting point",
            pickingOrigin = pickingOrigin,
            enabled = !processingAction,
            onChooseMap = {
                pickingOrigin = true
                if (liveMap) {
                    liveDraft = null
                    originMessage = null
                    originMode = "pick"
                } else {
                    failureMessage = ""
                }
            },
            onConfirmOrigin = { point ->
                plannerViewModel.setOrigin(point)
                closeProposalFlow()
            },
            onRetry = {
                closeProposalFlow()
                performPrimaryAction()
            },
            onEditSettings = { closeProposalFlow() },
            onUnavailableAction = {
                if (liveMap) {
                    closeProposalFlow()
                    performPrimaryAction()
                } else {
                    failureMessage = "Address search and live location are not connected " +
                        "in this sample. Choose a point on the map."
                }
            },
            onCancelPicking = { pickingOrigin = false },
            sampleLabel = if (sampleOutcome != Trip33Sample.Normal) "Sample · ${sampleOutcome.name}" else null,
            modifier = proposalModifier,
        )
        return
    }

    if (processingAction && activeState == null && pendingReviewId == null) {
        TripLoadingContent(
            origin = planner.origin,
            liveOrigin = planner.originPlace,
            originIsManual = planner.originIsManual,
            minutes = boundedMinutes,
            transportSummary = transportTypes
                .filter { it.id in selectedTransportIds }
                .joinToString(" and ") { it.label },
            onCancel = { closeProposalFlow() },
            modifier = proposalModifier,
        )
        return
    }

    if (proposal != null) {
        TripProposalContent(
            trip = proposal,
            generationError = failureMessage.takeIf { failure != null },
            scheduled = proposal.routedDurationSeconds != null,
            onRename = { name ->
                if (latestDestinationActive && !processingAction && proposalGuard.canStart(proposal.id) && name.isNotBlank()) {
                    proposalJson = Json.encodeToString(proposal.copy(title = name.trim()))
                }
            },
            onStart = {
                if (latestDestinationActive && !processingAction &&
                    proposalGuard.canStart(proposal.id) && pendingOpenTripId == null
                ) {
                    val result = proposalGuard.start(tripsStore, proposal)
                    proposalJson = null
                    if (result == TripActionResult.Applied) {
                        queueTripOpening(proposal.id)
                        if (liveMap) finishPlannerExit()
                    } else {
                        validationMessage = describeProposalStartFailure(result)
                    }
                }
            },
            onGenerateAnother = {
                performPrimaryAction()
            },
            onEditSettings = { closeProposalFlow() },
            modifier = proposalModifier,
        )
        return
    }

    if (activeState != null && pendingOpenTripId == null) {
        ActiveMapContent(
            trip = activeState.trip,
            progress = (
                activeState.routeProgress ?: (activeState.elapsedMillis.toFloat() / TRIP_SIMULATION_DURATION_MILLIS)
            ).coerceIn(0f, 1f),
            actionsEnabled = isDestinationActive && !processingAction,
            mainOverview = true,
            onResume = { performPrimaryAction() },
            onFinish = {},
            onConfirmCancel = { _ -> },
            simulationEnabled = simulationEnabled,
            userPosition = if (simulationEnabled) null else location?.fix?.point,
            onRequestLocation = { location?.request() },
            locationState = when {
                simulationEnabled -> TripLocationState.Sample
                location?.fix != null -> TripLocationState.Live
                else -> TripLocationState.NotTracking
            },
            modifier = modifier,
        )
        return
    }

    if (originMode != "planner" && isDestinationActive && activeState == null && pendingOpenTripId == null) {
        val draftX = originDraftX
        val draftY = originDraftY
        val draftOrigin = if (draftX != null && draftY != null) {
            TripPoint("Selected point on map", draftX, draftY)
        } else {
            null
        }
        OriginSelectionContent(
            origin = if (originMode == "pick") draftOrigin else planner.origin,
            minutes = boundedMinutes,
            locationAccessOff = if (liveMap) location?.currentUpdate == LocationUpdate.PermissionDenied else planner.locationAccessOff,
            picking = originMode == "pick",
            draftLabel = liveDraft?.let { "Pinned location" },
            enabled = canChangeInputs(),
            message = originMessage,
            onPick = { point ->
                if (canChangeInputs()) {
                    originDraftX = point.x
                    originDraftY = point.y
                    originMessage = null
                    originMode = "pick"
                }
            },
            onChooseMap = {
                liveDraft = null
                originDraftX = null
                originDraftY = null
                originMessage = null
                originMode = "pick"
            },
            onConfirm = {
                val pin = liveDraft
                if (canChangeInputs() && (pin != null || draftOrigin != null)) {
                    if (pin != null) plannerViewModel.setOrigin(Place("Pinned location", pin))
                    else if (draftOrigin != null) plannerViewModel.setOrigin(draftOrigin)
                    closeProposalFlow()
                    clearValidation()
                }
            },
            onSearch = {
                originMessage = "Place and address search is not connected in this sample. Choose a point on the map."
            },
            onRetryLocation = {
                if (location != null) {
                    location.request { fix ->
                        if (originMode == "options" && canChangeInputs()) {
                            if (fix != null && location.containsPoint(fix.point)) {
                                plannerViewModel.useCurrentOrigin(Place("Current location", fix.point))
                                proposalJson = null
                                clearValidation()
                                originMode = "planner"
                            } else {
                                originMessage = "Location unavailable or outside the supported area."
                            }
                        }
                    }
                } else {
                    originMessage = "Live location is not connected in this sample. Your choices are kept."
                }
            },
            onBack = {
                liveDraft = null
                originDraftX = null
                originDraftY = null
                originMessage = null
                if (pickingLiveOriginFromFailure || originPickerFromMap) {
                    pickingOrigin = false
                    originMode = "planner"
                    originPickerFromMap = false
                } else {
                    originMode = if (originMode == "pick") "options" else "planner"
                }
            },
            liveMapData = if (liveMap) {
                TransitMapData(
                    browsing = true,
                    pickerOpen = true,
                    interactive = canChangeInputs(),
                    pin = liveDraft,
                    onPick = if (canChangeInputs()) {
                        { point ->
                            if (location?.containsPoint?.invoke(point) == true) {
                                liveDraft = point
                                originMessage = null
                                originMode = "pick"
                            } else {
                                originMessage = "Choose a point inside the supported area."
                            }
                        }
                    } else null,
                )
            } else null,
            modifier = modifier,
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
                    plannerViewModel.toggleTransport(id, transportTypes.map { it.id }.toSet())
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
            roundTrip = planner.roundTrip,
            onRoundTripChange = {
                if (canChangeInputs()) {
                    plannerViewModel.setRoundTrip(it)
                    clearValidation()
                }
            },
            bikeSpeedKmh = planner.bikeSpeedKmh,
            onBikeSpeedChange = { if (canChangeInputs()) plannerViewModel.setBikeSpeedKmh(it) },
            selectedAttractionIds = planner.selectedAttractionIds,
            onWalkingPreferenceChange = { if (canChangeInputs()) plannerViewModel.setWalkingPreference(it) },
            onToggleAttraction = { if (canChangeInputs()) plannerViewModel.toggleAttraction(it) },
            validationMessage = validationMessage,
            actionLabel = pendingActionLabel ?: actionLabel,
            actionEnabled = isDestinationActive &&
                !processingAction && generationJob?.isActive != true &&
                pendingOpenTripId == null &&
                actionHasCallback &&
                (
                    activeState != null ||
                        pendingReviewId != null ||
                        (if (liveMap) planner.originPlace != null else generationBounds != null && planner.origin != null)
                ),
            inputsEnabled = canChangeInputs(),
            activeTripSummary = if (pendingOpenTripId != null) pendingSummary else activeSummary,
            pendingReviewSummary = if (pendingOpenTripId != null) null else completedSummary,
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
            liveOrigin = planner.originPlace,
            originIsManual = planner.originIsManual,
            originStatus = defaultOriginStatus,
            onTapOrigin = { point ->
                if (canChangeInputs()) {
                    liveDraft = null
                    originDraftX = point.x
                    originDraftY = point.y
                    originMessage = null
                    originPickerFromMap = true
                    originMode = "pick"
                }
            },
            onTapLiveOrigin = { point ->
                if (canChangeInputs()) {
                    originDraftX = null
                    originDraftY = null
                    val supported = location?.containsPoint?.invoke(point) == true
                    liveDraft = point.takeIf { supported }
                    originMessage = if (supported) null else "Choose a point inside the supported area."
                    originPickerFromMap = true
                    originMode = "pick"
                }
            },
            onEditOrigin = {
                if (canChangeInputs()) {
                    liveDraft = null
                    originDraftX = null
                    originDraftY = null
                    originPickerFromMap = false
                    originMessage = null
                    originMode = "options"
                }
            },
            modifier = modifier,
        )
    }
}

/**
 * Collects automatic location updates for the planner's default origin and surfaces a timeout notice
 * when no reading arrives within [AUTOMATIC_ORIGIN_TIMEOUT_MILLIS].
 */
@Composable
private fun AutomaticOriginUpdatesEffect(
    location: PlannerLocation?,
    enabled: Boolean,
    canApplyFix: () -> Boolean,
    onStatusChanged: (String?) -> Unit,
    onDefaultOriginFound: (Place) -> Unit,
) {
    val latestCanApply by rememberUpdatedState(canApplyFix)
    val latestOnStatus by rememberUpdatedState(onStatusChanged)
    val latestOnFound by rememberUpdatedState(onDefaultOriginFound)

    LaunchedEffect(location, enabled) {
        latestOnStatus(null)
        if (!enabled || location == null) return@LaunchedEffect
        val updates = location.automaticOriginUpdates ?: return@LaunchedEffect
        var resolvedStatus: String? = null
        val timeout = launch {
            delay(AUTOMATIC_ORIGIN_TIMEOUT_MILLIS)
            if (resolvedStatus == null) {
                resolvedStatus = "Location unavailable. Choose a starting point."
                latestOnStatus(resolvedStatus)
            }
        }
        try {
            updates.collect { update ->
                val usable = update is LocationUpdate.Fix && location.containsPoint(update.point)
                resolvedStatus = when (update) {
                    LocationUpdate.PermissionDenied -> "Location permission is off. Choose a starting point."
                    LocationUpdate.Unavailable -> "Location unavailable. Choose a starting point."
                    is LocationUpdate.Fix -> if (usable) null else "Outside the supported area. Choose a starting point."
                }
                latestOnStatus(resolvedStatus)
                if (usable && latestCanApply()) {
                    timeout.cancel()
                    latestOnFound(Place("Current location", update.point))
                }
            }
        } finally {
            timeout.cancel()
        }
    }
}
