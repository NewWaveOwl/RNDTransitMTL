package com.example.rnd_transit_mtl.View.main

import com.example.rnd_transit_mtl.ViewModels.main.MainPlannerState
import com.example.rnd_transit_mtl.data.assets.toSampleMapPoint
import com.example.rnd_transit_mtl.data.generation.BackendTripGenerator
import com.example.rnd_transit_mtl.data.generation.MockTripGenerator
import com.example.rnd_transit_mtl.data.generation.TripGenerationBounds
import com.example.rnd_transit_mtl.data.generation.TripGenerationInput
import com.example.rnd_transit_mtl.data.generation.TripGenerationResult
import com.example.rnd_transit_mtl.data.generation.explorationZoneCount
import com.example.rnd_transit_mtl.data.location.LocationUpdate
import com.example.rnd_transit_mtl.data.location.PlannerLocation
import com.example.rnd_transit_mtl.data.location.awaitPlanningLocation
import com.example.rnd_transit_mtl.model.geo.GeoPoint
import com.example.rnd_transit_mtl.model.transport.TransportRoute
import com.example.rnd_transit_mtl.model.transport.TransportType
import com.example.rnd_transit_mtl.model.trip.Place
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.state.trip.TripActionResult
import com.example.rnd_transit_mtl.state.trip.TripsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** Outcome of validating planner state and resolving origin before calling a trip generator. */
internal sealed interface PreparedTripGeneration {
    /** Validated generation request ready to execute on a background dispatcher. */
    data class Ready(
        val input: TripGenerationInput,
        val bounds: TripGenerationBounds,
    ) : PreparedTripGeneration

    /** Validation, permission, or explicit sample-fixture failure surfaced without running routing. */
    data class Failed(
        val kind: Trip33Failure,
        val message: String = "",
    ) : PreparedTripGeneration
}

/**
 * Validates planner inputs, awaits live planning location when automatic origin is active,
 * and builds a [TripGenerationInput] or returns an explicit [PreparedTripGeneration.Failed].
 *
 * @param planner Current immutable planner UI state.
 * @param boundedMinutes Coerced trip budget in minutes.
 * @param liveMap Whether the live platform map renderer is active.
 * @param generationBounds Measured sample-map bounds, required when [liveMap] is false.
 * @param location Optional live location provider.
 * @param transportTypes Supported transport-mode catalogue.
 * @param completedTripsCount Number of completed trips used for default numbering.
 * @param sampleOutcome Explicit preview/test fixture; [Trip33Sample.Normal] runs real generation.
 * @param onDefaultOriginResolved Called when a fresh automatic live location is resolved.
 * @return Ready request payload or explicit failure cause.
 */
internal suspend fun prepareTripGenerationInput(
    planner: MainPlannerState,
    boundedMinutes: Int,
    liveMap: Boolean,
    generationBounds: TripGenerationBounds?,
    location: PlannerLocation?,
    transportTypes: List<TransportType>,
    completedTripsCount: Int,
    sampleOutcome: Trip33Sample,
    onDefaultOriginResolved: (Place) -> Unit,
): PreparedTripGeneration {
    val bounds = if (liveMap) TripGenerationBounds() else generationBounds
    if (bounds == null) {
        return PreparedTripGeneration.Failed(
            Trip33Failure.InvalidInput,
            "The map is preparing. Please try again.",
        )
    }

    val routingOrigin = if (liveMap && !planner.originIsManual) {
        val update = location?.awaitPlanningLocation()
        println(
            "RndTransitGeneration stage=location result=${update?.let { it::class.simpleName }} " +
                "roundTrip=${planner.roundTrip} " +
                "interestZones=${explorationZoneCount(planner.selectedAttractionIds.size)}",
        )
        if (update !is LocationUpdate.Fix) {
            return PreparedTripGeneration.Failed(
                if (update == LocationUpdate.PermissionDenied) Trip33Failure.PermissionDenied
                else Trip33Failure.LocationUnavailable,
            )
        }
        if (!location.containsPoint(update.point)) {
            return PreparedTripGeneration.Failed(
                Trip33Failure.LocationUnavailable,
                "Outside the supported area. Choose a starting point.",
            )
        }
        Place("Current location", update.point).also(onDefaultOriginResolved)
    } else {
        planner.originPlace
    }

    val origin = planner.origin
    val missingOrigin = if (liveMap) routingOrigin == null else origin == null
    if (missingOrigin || (sampleOutcome == Trip33Sample.PermissionDenied && !planner.originIsManual)) {
        val kind = if (
            sampleOutcome == Trip33Sample.PermissionDenied ||
            planner.locationAccessOff ||
            location?.currentUpdate == LocationUpdate.PermissionDenied
        ) {
            Trip33Failure.PermissionDenied
        } else {
            Trip33Failure.LocationUnavailable
        }
        return PreparedTripGeneration.Failed(kind)
    }

    if (planner.selectedTransportIds.isEmpty()) {
        return PreparedTripGeneration.Failed(
            Trip33Failure.InvalidInput,
            "Choose at least one transport type.",
        )
    }

    val transitModes = transportTypes.filter { it.usesRoutes && it.id in planner.selectedTransportIds }
    val zones = transitModes.associate { it.id to planner.zonesForTransport(it.id).toList() }
    val missingZones = transitModes.firstOrNull { zones[it.id].isNullOrEmpty() }
    if (missingZones != null) {
        return PreparedTripGeneration.Failed(
            Trip33Failure.InvalidInput,
            "Choose at least one transit zone for ${missingZones.label}.",
        )
    }

    // Explicit samples are distinct from validation failures.
    when (sampleOutcome) {
        Trip33Sample.NoRoute -> return PreparedTripGeneration.Failed(Trip33Failure.NoRoute)
        Trip33Sample.ProviderError -> return PreparedTripGeneration.Failed(Trip33Failure.ProviderUnavailable)
        else -> Unit
    }

    val input = TripGenerationInput(
        title = "Trip ${completedTripsCount + 1}",
        description = "Random route with $boundedMinutes planned minutes.",
        imageUrl = "",
        plannedMinutes = boundedMinutes,
        selectedTransportIds = planner.selectedTransportIds.toList(),
        selectedRouteIds = emptyList(),
        attractionIntensity = planner.intensity,
        selectedZoneIds = zones.values.flatten().distinct().sortedBy { it.value },
        selectedZonesByTransport = zones,
        origin = origin?.copy(),
        originPlace = routingOrigin,
        walkingPreference = planner.walkingPreference,
        bikeSpeedKmh = planner.bikeSpeedKmh,
        roundTrip = planner.roundTrip,
        interestZoneCount = explorationZoneCount(planner.selectedAttractionIds.size),
    )
    return PreparedTripGeneration.Ready(input = input, bounds = bounds)
}

/**
 * Executes trip generation on [Dispatchers.Default] using either the backend service or local mock generator.
 *
 * @param ready Validated generation input and map bounds.
 * @param liveMap Whether the live platform map renderer is active.
 * @param backendGenerator Optional remote routing generator.
 * @param mockGenerator Local sample-map fallback generator.
 * @param tripsStore Store supplying existing trip IDs to avoid collisions.
 * @param transportTypes Supported transport modes.
 * @param transportRoutes Supported transport routes for sample generation.
 * @param previousDestinations Serialized [GeoPoint] list excluded from repeat generation.
 * @return Generation outcome from the active generator.
 */
internal suspend fun executeTripGeneration(
    ready: PreparedTripGeneration.Ready,
    liveMap: Boolean,
    backendGenerator: BackendTripGenerator?,
    mockGenerator: MockTripGenerator,
    tripsStore: TripsStore,
    transportTypes: List<TransportType>,
    transportRoutes: List<TransportRoute>,
    previousDestinations: List<String>,
): TripGenerationResult {
    val existingIds = buildSet {
        addAll(tripsStore.completedTrips.map { it.id })
        addAll(tripsStore.cancelledTrips.map { it.id })
        tripsStore.activeTrip?.let { add(it.id) }
    }
    return withContext(Dispatchers.Default) {
        if (liveMap || backendGenerator != null) {
            backendGenerator?.generate(
                input = ready.input,
                transportTypes = transportTypes,
                existingTripIds = existingIds,
                excludedDestinations = previousDestinations.map { Json.decodeFromString<GeoPoint>(it) },
            ) ?: TripGenerationResult.RoutingUnavailable(
                "The trip planner is not configured. Please try again later.",
            )
        } else {
            mockGenerator.generate(
                input = ready.input,
                transportTypes = transportTypes,
                transportRoutes = transportRoutes,
                existingTripIds = existingIds,
                bounds = ready.bounds,
            )
        }
    }
}

/**
 * Maps a non-applied proposal-start [TripActionResult] into user-facing validation copy.
 *
 * @param result Result returned by [ProposalGuard.start].
 * @return Explanatory validation message for the planner screen.
 */
internal fun describeProposalStartFailure(result: TripActionResult): String = when (result) {
    is TripActionResult.ActiveTripExists -> "An unfinished trip already exists. Use Resume trip."
    is TripActionResult.InvalidInput -> result.message
    else -> "The trip could not be started. Please try again."
}

/**
 * Checks whether [planner] has finished applying all copied preferences from [saved].
 *
 * @param planner Current planner UI state.
 * @param saved Historical trip whose settings were copied.
 * @param liveMap Whether geographic [Place] origin or normalized sample-map [TripPoint] origin applies.
 * @return True once all copied preferences match [saved].
 */
internal fun plannerMatchesSavedTrip(
    planner: MainPlannerState,
    saved: Trip,
    liveMap: Boolean,
): Boolean =
    planner.minutes == saved.plannedMinutes &&
        planner.selectedTransportIds == (saved.requestedTransportIds ?: saved.selectedTransports.map { it.id }) &&
        planner.selectedZoneIds == saved.selectedZoneIds &&
        planner.selectedZonesByTransport == saved.selectedZonesByTransport &&
        (if (liveMap) planner.originPlace == saved.origin else planner.origin == saved.origin.toSampleMapPoint()) &&
        planner.intensity == saved.attractionIntensity
