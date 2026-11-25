package com.example.rnd_transit_mtl.data.generation

import com.example.rnd_transit_mtl.data.assets.toSampleMapPlace
import com.example.rnd_transit_mtl.data.fake.FakeRoutingService
import com.example.rnd_transit_mtl.data.routing.RoutingResult
import com.example.rnd_transit_mtl.data.routing.RoutingService
import com.example.rnd_transit_mtl.model.ids.RouteId
import com.example.rnd_transit_mtl.model.ids.TransportId
import com.example.rnd_transit_mtl.model.ids.TripId
import com.example.rnd_transit_mtl.model.ids.UserId
import com.example.rnd_transit_mtl.model.ids.ZoneId
import com.example.rnd_transit_mtl.model.transport.TransportRoute
import com.example.rnd_transit_mtl.model.transport.TransportType
import com.example.rnd_transit_mtl.model.trip.Place
import com.example.rnd_transit_mtl.model.trip.RouteLeg
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.model.trip.TripJourney
import com.example.rnd_transit_mtl.model.trip.TripJourneyKind
import com.example.rnd_transit_mtl.model.trip.TripPoint
import com.example.rnd_transit_mtl.model.trip.TripRequest
import com.example.rnd_transit_mtl.model.trip.TripRouteSnapshot
import com.example.rnd_transit_mtl.model.trip.TripStatus
import com.example.rnd_transit_mtl.model.trip.TripTransportSnapshot
import com.example.rnd_transit_mtl.model.trip.message
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Current planner values supplied to one generation request.
 *
 * This input may contain invalid form values. Validation occurs in
 * [MockTripGenerator.generate], allowing normal input errors to become a result.
 *
 * @property origin Exact source-image start; null preserves legacy random-start generation.
 * @property ownerId User planning the trip; null for a guest.
 */
data class TripGenerationInput(
    val title: String,
    val description: String,
    val imageUrl: String,
    val plannedMinutes: Int,
    val selectedTransportIds: List<TransportId>,
    val selectedRouteIds: List<RouteId>,
    val attractionIntensity: Float,
    val selectedZoneIds: List<ZoneId> = emptyList(),
    val selectedZonesByTransport: Map<TransportId, List<ZoneId>> = emptyMap(),
    val origin: TripPoint? = null,
    val ownerId: UserId? = null,
    val originPlace: Place? = null,
    val walkingPreference: String = "Amble",
    val bikeSpeedKmh: Int = 18,
    /** Fixed geographic target for a return request; null keeps random adventure generation. */
    val destinationPlace: Place? = null,
    /** Compare available modes by arrival time for a fixed-target request. */
    val compareModes: Boolean = false,
    /** Routes outward and back within the same available-time budget. */
    val roundTrip: Boolean = false,
    /** Number of generated exploration waypoints; ignored for regular trips. */
    val interestZoneCount: Int = 1,
)

/**
 * Restricted outcomes of generating one mock trip.
 */
sealed class TripGenerationResult {
    data class Success(val trip: Trip) : TripGenerationResult()
    data class InvalidInput(val message: String) : TripGenerationResult()
    data object IdUnavailable : TripGenerationResult()

    /** The routing service found no route, or a point is outside its area. */
    data object NoRoute : TripGenerationResult()

    /** The routing service failed; retrying later may work. */
    data class RoutingUnavailable(val message: String) : TripGenerationResult()
}

/**
 * Creates one mock trip from validated planner values.
 *
 * Endpoints are chosen in normalized PNG units and projected to geographic [Place] instances.
 * Random, ID creation, and time are injectable for deterministic verification.
 *
 * @param routingService Plans the legs between the chosen places.
 * @param random Source of randomness for endpoint sampling.
 * @param idFactory Supplies candidate unique trip IDs.
 * @param nowEpochMillis Clock returning nonnegative epoch milliseconds.
 */
class MockTripGenerator(
    private val routingService: RoutingService = FakeRoutingService(),
    private val random: Random = Random.Default,
    private val idFactory: () -> String = { Uuid.random().toString() },
    private val nowEpochMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    /**
     * Validates [input], samples endpoints within [bounds], routes legs, and returns an immutable [Trip].
     *
     * @param input Raw planner form values.
     * @param transportTypes Supported transport mode catalogue.
     * @param transportRoutes Supported transport route catalogue.
     * @param existingTripIds Existing active and historical IDs to avoid collisions.
     * @param bounds Normalized area within the sample map image for endpoint sampling.
     * @return Generation outcome carrying either the new [Trip] or an explicit failure reason.
     */
    suspend fun generate(
        input: TripGenerationInput,
        transportTypes: List<TransportType>,
        transportRoutes: List<TransportRoute>,
        existingTripIds: Set<TripId>,
        bounds: TripGenerationBounds = TripGenerationBounds(),
    ): TripGenerationResult {
        val normalizedInput = normalizeInput(input)
        val snapshots = try {
            validateAndResolveSelections(normalizedInput, transportTypes, transportRoutes)
        } catch (invalid: IllegalArgumentException) {
            return TripGenerationResult.InvalidInput(invalid.message ?: "Invalid trip input.")
        }

        val id = findUnusedId(existingTripIds) ?: return TripGenerationResult.IdUnavailable
        val createdAt = nowEpochMillis()
        if (createdAt < 0L) {
            return TripGenerationResult.InvalidInput("Creation timestamp must not be negative.")
        }

        val (start, destination) = resolveSampleEndpoints(normalizedInput.origin, bounds)
        // Live origins retain their exact coordinates. Bundled-map endpoints are projected from the PNG.
        val origin = normalizedInput.originPlace ?: start.toSampleMapPlace()
        val destinationPlace = destination.toSampleMapPlace()

        val request = TripRequest(
            ownerId = normalizedInput.ownerId,
            origin = origin,
            destination = destinationPlace,
            plannedMinutes = normalizedInput.plannedMinutes,
            modes = normalizedInput.selectedTransportIds,
            zonesByTransport = normalizedInput.selectedZonesByTransport,
        )
        request.validate(transportTypes.map { it.id }.toSet()).firstOrNull()?.let {
            return TripGenerationResult.InvalidInput(it.message)
        }

        val waypoints = if (normalizedInput.roundTrip) {
            if (normalizedInput.interestZoneCount !in 1..6) {
                return TripGenerationResult.InvalidInput("Choose 1-6 interest zones.")
            }
            listOf(destinationPlace) + (1 until normalizedInput.interestZoneCount).map {
                generateDestinationFromOrigin(start, bounds).toSampleMapPlace()
            }
        } else emptyList()

        val places = listOf(origin) + if (normalizedInput.roundTrip) waypoints + origin else listOf(destinationPlace)
        val routedLegs = routeSegmentSequence(request, places, normalizedInput.plannedMinutes, normalizedInput.roundTrip)
        val legs = when (routedLegs) {
            is SegmentRoutingOutcome.Legs -> routedLegs.legs
            is SegmentRoutingOutcome.Failure -> return routedLegs.result
        }

        val trip = try {
            createTrip(id, normalizedInput, snapshots, origin, destinationPlace, legs, createdAt, waypoints)
        } catch (_: IllegalArgumentException) {
            // Legs that break Trip's rules are a provider fault, not a user input error.
            return TripGenerationResult.RoutingUnavailable("The routing service returned an invalid route.")
        }

        return TripGenerationResult.Success(trip)
    }

    private sealed interface SegmentRoutingOutcome {
        data class Legs(val legs: List<RouteLeg>) : SegmentRoutingOutcome
        data class Failure(val result: TripGenerationResult) : SegmentRoutingOutcome
    }

    /** Routes consecutive pairs in [places] while enforcing the total [plannedMinutes] budget. */
    private suspend fun routeSegmentSequence(
        request: TripRequest,
        places: List<Place>,
        plannedMinutes: Int,
        roundTrip: Boolean,
    ): SegmentRoutingOutcome {
        val totalBudgetSeconds = plannedMinutes * 60L
        val legs = mutableListOf<RouteLeg>()
        for (index in 0 until places.lastIndex) {
            val left = totalBudgetSeconds - legs.sumOf { it.durationSeconds }
            if (left <= 0) return SegmentRoutingOutcome.Failure(TripGenerationResult.NoRoute)
            val directionalBudget = if (roundTrip) left / (places.lastIndex - index) else null
            val segmentRequest = request.copy(
                origin = places[index],
                destination = places[index + 1],
                durationBudgetSeconds = directionalBudget,
            )
            when (val routed = routingService.route(segmentRequest)) {
                is RoutingResult.Success -> legs += routed.legs
                RoutingResult.NoRouteFound, is RoutingResult.OutsideServiceArea ->
                    return SegmentRoutingOutcome.Failure(TripGenerationResult.NoRoute)
                is RoutingResult.Unavailable ->
                    return SegmentRoutingOutcome.Failure(TripGenerationResult.RoutingUnavailable(routed.message))
            }
        }
        if (roundTrip && legs.sumOf { it.durationSeconds } > totalBudgetSeconds) {
            return SegmentRoutingOutcome.Failure(TripGenerationResult.NoRoute)
        }
        return SegmentRoutingOutcome.Legs(legs)
    }

    /** Copies and trims [input] fields and snapshots mutable collection references. */
    private fun normalizeInput(input: TripGenerationInput): TripGenerationInput = input.copy(
        title = input.title.trim(),
        description = input.description.trim(),
        imageUrl = input.imageUrl.trim(),
        selectedTransportIds = input.selectedTransportIds.toList(),
        selectedRouteIds = input.selectedRouteIds.toList(),
        selectedZoneIds = input.selectedZoneIds.toList(),
        selectedZonesByTransport = input.selectedZonesByTransport.mapValues { (_, zones) -> zones.toList() },
    )

    /** Validates planner fields, transit zone rules, and resolves catalogue snapshots. */
    private fun validateAndResolveSelections(
        input: TripGenerationInput,
        transportTypes: List<TransportType>,
        transportRoutes: List<TransportRoute>,
    ): Pair<List<TripTransportSnapshot>, List<TripRouteSnapshot>> {
        Trip.validatePlannerValues(
            title = input.title,
            description = input.description,
            imageUrl = input.imageUrl,
            plannedMinutes = input.plannedMinutes,
            attractionIntensity = input.attractionIntensity,
        )
        require(input.selectedZoneIds.all { it.value in setOf("A", "B", "C", "D") }) {
            "A selected transit zone is unavailable."
        }
        require(input.selectedZoneIds.distinct().size == input.selectedZoneIds.size) {
            "Selected transit zones must be unique."
        }
        val transitTransportIds = transportTypes
            .filter { it.usesRoutes && it.id in input.selectedTransportIds }
            .map { it.id }
            .toSet()
        require(transitTransportIds.isEmpty() || input.selectedZoneIds.isNotEmpty()) {
            "Choose at least one transit zone."
        }
        Trip.validateTransportZones(transitTransportIds, input.selectedZoneIds, input.selectedZonesByTransport)
        return resolveSelections(input, transportTypes, transportRoutes)
    }

    /** Picks sample-map start and destination points respecting an explicit origin when provided. */
    private fun resolveSampleEndpoints(
        requestedOrigin: TripPoint?,
        bounds: TripGenerationBounds,
    ): Pair<TripPoint, TripPoint> = if (requestedOrigin == null) {
        // Compatibility for existing test fixtures and older callers.
        val localStart = randomPoint("Start")
        val localDestination = generateDestination(localStart)
        bounds.toSourcePoint(localStart) to bounds.toSourcePoint(localDestination)
    } else {
        val start = requestedOrigin.copy()
        start to generateDestinationFromOrigin(start, bounds)
    }

    private fun createTrip(
        id: TripId,
        normalizedInput: TripGenerationInput,
        snapshots: Pair<List<TripTransportSnapshot>, List<TripRouteSnapshot>>,
        origin: Place,
        destinationPlace: Place,
        legs: List<RouteLeg>,
        createdAt: Long,
        waypoints: List<Place>,
    ): Trip = Trip(
        id = id,
        ownerId = normalizedInput.ownerId,
        status = TripStatus.Generated,
        title = normalizedInput.title,
        description = normalizedInput.description,
        imageUrl = normalizedInput.imageUrl,
        origin = origin,
        destination = if (normalizedInput.roundTrip) origin else destinationPlace,
        legs = legs,
        plannedMinutes = normalizedInput.plannedMinutes,
        selectedTransports = snapshots.first,
        selectedRoutes = snapshots.second,
        attractionIntensity = normalizedInput.attractionIntensity,
        createdAt = createdAt,
        completedAt = null,
        review = null,
        selectedZoneIds = normalizedInput.selectedZoneIds.toList(),
        selectedZonesByTransport = normalizedInput.selectedZonesByTransport.mapValues { (_, zones) -> zones.toList() },
        journey = if (normalizedInput.roundTrip) {
            TripJourney(
                TripJourneyKind.RoundTrip,
                turnaround = destinationPlace,
                waypoints = waypoints.takeIf { it.size > 1 }.orEmpty(),
            )
        } else null,
    )

    private fun resolveSelections(
        input: TripGenerationInput,
        transportTypes: List<TransportType>,
        transportRoutes: List<TransportRoute>,
    ): Pair<List<TripTransportSnapshot>, List<TripRouteSnapshot>> {
        require(input.selectedTransportIds.isNotEmpty()) { "Choose at least one transport type." }
        require(input.selectedTransportIds.distinct().size == input.selectedTransportIds.size) {
            "Selected transport IDs must be unique."
        }
        require(input.selectedRouteIds.distinct().size == input.selectedRouteIds.size) {
            "Selected route IDs must be unique."
        }
        require(transportTypes.map { it.id }.distinct().size == transportTypes.size) {
            "Transport catalogue contains duplicate IDs."
        }
        require(transportRoutes.map { it.id }.distinct().size == transportRoutes.size) {
            "Route catalogue contains duplicate IDs."
        }

        val typesById = transportTypes.associateBy { it.id }
        val routesById = transportRoutes.associateBy { it.id }

        val transports = input.selectedTransportIds.map { id ->
            val source = requireNotNull(typesById[id]) { "Selected transport is unavailable: $id." }
            TripTransportSnapshot(id = source.id, label = source.label, usesRoutes = source.usesRoutes)
        }

        val selectedTypesById = transports.associateBy { it.id }
        val routes = input.selectedRouteIds.map { id ->
            val source = requireNotNull(routesById[id]) { "Selected route is unavailable: $id." }
            val owner = selectedTypesById[source.transportTypeId]
            require(owner != null && owner.usesRoutes) {
                "Selected route $id requires its route-based transport."
            }
            TripRouteSnapshot(id = source.id, transportTypeId = source.transportTypeId, label = source.label)
        }

        return transports to routes
    }

    private fun findUnusedId(existingTripIds: Set<TripId>): TripId? {
        repeat(MAX_ID_ATTEMPTS) {
            val candidate = idFactory()
            if (candidate.isNotBlank() && candidate == candidate.trim() && TripId(candidate) !in existingTripIds) {
                return TripId(candidate)
            }
        }
        return null
    }

    private fun randomPoint(label: String): TripPoint = TripPoint(
        label = label,
        x = random.nextDouble(SAFE_MIN.toDouble(), SAFE_MAX.toDouble()).toFloat(),
        y = random.nextDouble(SAFE_MIN.toDouble(), SAFE_MAX.toDouble()).toFloat(),
    )

    /**
     * Samples a destination in the generation bounds, with a far-corner fallback.
     *
     * @param origin Selected source-image point, unchanged by generation.
     * @param bounds Destination area within the source PNG.
     */
    private fun generateDestinationFromOrigin(
        origin: TripPoint,
        bounds: TripGenerationBounds,
    ): TripPoint {
        val minimumSeparation = minOf(bounds.right - bounds.left, bounds.bottom - bounds.top)
            .toDouble() * MIN_NORMALIZED_SEPARATION

        repeat(MAX_DESTINATION_ATTEMPTS) {
            val candidate = bounds.toSourcePoint(randomPoint("Destination"))
            if (origin.normalizedDistanceTo(candidate) >= minimumSeparation) {
                return candidate
            }
        }

        return listOf(
            TripPoint("Destination", SAFE_MIN, SAFE_MIN),
            TripPoint("Destination", SAFE_MIN, SAFE_MAX),
            TripPoint("Destination", SAFE_MAX, SAFE_MIN),
            TripPoint("Destination", SAFE_MAX, SAFE_MAX),
        )
            .map(bounds::toSourcePoint)
            .maxBy { origin.normalizedDistanceTo(it) }
    }

    private fun generateDestination(start: TripPoint): TripPoint {
        repeat(MAX_DESTINATION_ATTEMPTS) {
            val candidate = randomPoint("Destination")
            if (start.normalizedDistanceTo(candidate) >= MIN_NORMALIZED_SEPARATION) {
                return candidate
            }
        }

        // Farther safe corner on each axis gives at least sqrt(0.35^2 + 0.35^2) (~0.495 >= 0.25).
        val fallback = TripPoint(
            label = "Destination",
            x = if (start.x <= 0.5f) SAFE_MAX else SAFE_MIN,
            y = if (start.y <= 0.5f) SAFE_MAX else SAFE_MIN,
        )
        check(start.normalizedDistanceTo(fallback) >= MIN_NORMALIZED_SEPARATION) {
            "Fallback endpoints must satisfy minimum separation."
        }
        return fallback
    }

    companion object {
        const val SAFE_MIN: Float = 0.15f
        const val SAFE_MAX: Float = 0.85f

        /** Separation inside the visible generation area, before mapping into the PNG. */
        const val MIN_NORMALIZED_SEPARATION: Double = 0.25
        const val MAX_DESTINATION_ATTEMPTS: Int = 32
        const val MAX_ID_ATTEMPTS: Int = 16
    }
}
