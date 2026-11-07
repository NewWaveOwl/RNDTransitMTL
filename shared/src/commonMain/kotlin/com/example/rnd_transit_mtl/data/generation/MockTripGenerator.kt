package com.example.rnd_transit_mtl.data.generation

import com.example.rnd_transit_mtl.model.ids.ZoneId
import com.example.rnd_transit_mtl.model.ids.RouteId
import com.example.rnd_transit_mtl.model.ids.TransportId
import com.example.rnd_transit_mtl.model.ids.TripId
import com.example.rnd_transit_mtl.model.ids.UserId
import com.example.rnd_transit_mtl.model.transport.TransportRoute
import com.example.rnd_transit_mtl.model.transport.TransportType
import com.example.rnd_transit_mtl.data.assets.toSampleMapPlace
import com.example.rnd_transit_mtl.model.trip.RouteLeg
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.model.trip.TripPoint
import com.example.rnd_transit_mtl.model.trip.TripRouteSnapshot
import com.example.rnd_transit_mtl.model.trip.TripStatus
import com.example.rnd_transit_mtl.model.trip.TripTransportSnapshot
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.uuid.Uuid
import com.example.rnd_transit_mtl.data.fake.FakeRoutingService
import com.example.rnd_transit_mtl.data.routing.RoutingResult
import com.example.rnd_transit_mtl.data.routing.RoutingService
import com.example.rnd_transit_mtl.model.trip.Place
import com.example.rnd_transit_mtl.model.trip.TripRequest
import com.example.rnd_transit_mtl.model.trip.message

/**
 * Current planner values supplied to one generation request.
 *
 * This input may contain invalid form values. Validation occurs in
 * generate(), allowing normal input errors to become a result.
 *
 * It is not a Trip record or a saved planner-draft implementation.
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
    val originPlace: Place? = null
)

/**
 * Restricted outcomes of generating one mock trip.
 *
 * Success carries the complete record. InvalidInput represents
 * correctable planner/catalogue issues. IdUnavailable means the
 * supplied ID source could not provide a free ID within the limit.
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
 * Endpoints are chosen in normalized PNG units. New trips can use the
 * planner's visible crop as generation bounds. They are then converted
 * to real places with SampleMapProjection, but are not snapped to streets.
 *
 * The endpoints become a TripRequest, which is validated and passed to
 * routingService for the legs. With the default FakeRoutingService the trip
 * has one straight, unrouted leg using the first selected mode, so its
 * distance is the straight-line distance between the places.
 * The trip starts as Generated and owned by input.ownerId.
 *
 * Call generate() once in response to starting a trip, after checking
 * that no unfinished trip exists. Store and navigate with the returned
 * Trip. Never call it from map drawing, details, or restoration logic.
 *
 * Random, ID creation, and time are injectable for predictable checks.
 * Injected factories must not throw; time must be a nonnegative epoch
 * millisecond value. A seeded Random controls endpoints. For complete
 * reproducibility, also inject a fixed time and predictable ID source.
 *
 * @param routingService Plans the legs between the chosen places.
 */
class MockTripGenerator(
    private val routingService: RoutingService = FakeRoutingService(),
    private val random: Random = Random.Default,
    private val idFactory: () -> String = { Uuid.random().toString() },
    private val nowEpochMillis: () -> Long = {
        Clock.System.now().toEpochMilliseconds()
    }
) {
    /**
     * Creates independent snapshots of selected catalogue records.
     *
     * existingTripIds must contain IDs from both the active trip and
     * completed collection. An empty set is appropriate for a new store.
     *
     * IDs are checked before returning Success. The future store must
     * also enforce uniqueness when inserting the returned record.
     */
    suspend fun generate(
        input: TripGenerationInput,
        transportTypes: List<TransportType>,
        transportRoutes: List<TransportRoute>,
        existingTripIds: Set<TripId>,
        bounds: TripGenerationBounds = TripGenerationBounds()
    ): TripGenerationResult {
        val normalizedInput = input.copy(
            title = input.title.trim(),
            description = input.description.trim(),
            imageUrl = input.imageUrl.trim(),
            selectedTransportIds = input.selectedTransportIds.toList(),
            selectedRouteIds = input.selectedRouteIds.toList(),
            selectedZoneIds = input.selectedZoneIds.toList(),
            selectedZonesByTransport = input.selectedZonesByTransport.mapValues { (_, zones) -> zones.toList() }
        )

        val snapshots = try {
            Trip.validatePlannerValues(
                title = normalizedInput.title,
                description = normalizedInput.description,
                imageUrl = normalizedInput.imageUrl,
                plannedMinutes = normalizedInput.plannedMinutes,
                attractionIntensity = normalizedInput.attractionIntensity
            )

            require(
                normalizedInput.selectedZoneIds.all {
                    it.value in setOf("A", "B", "C", "D")
                }
            ) {
                "A selected transit zone is unavailable."
            }

            require(
                normalizedInput.selectedZoneIds.distinct().size ==
                    normalizedInput.selectedZoneIds.size
            ) {
                "Selected transit zones must be unique."
            }

            val includesTransit = transportTypes.any {
                it.usesRoutes &&
                    it.id in normalizedInput.selectedTransportIds
            }

            require(!includesTransit || normalizedInput.selectedZoneIds.isNotEmpty()) {
                "Choose at least one transit zone."
            }

            Trip.validateTransportZones(
                transportTypes.filter { it.usesRoutes && it.id in normalizedInput.selectedTransportIds }
                    .map { it.id }.toSet(),
                normalizedInput.selectedZoneIds,
                normalizedInput.selectedZonesByTransport
            )

            resolveSelections(
                input = normalizedInput,
                transportTypes = transportTypes,
                transportRoutes = transportRoutes
            )
        } catch (invalid: IllegalArgumentException) {
            return TripGenerationResult.InvalidInput(
                invalid.message ?: "Invalid trip input."
            )
        }

        val id = findUnusedId(existingTripIds)
            ?: return TripGenerationResult.IdUnavailable

        val createdAt = nowEpochMillis()
        if (createdAt < 0L) {
            return TripGenerationResult.InvalidInput(
                "Creation timestamp must not be negative."
            )
        }

        val start: TripPoint
        val destination: TripPoint

        val requestedOrigin = normalizedInput.origin

        if (requestedOrigin == null) {
            // Compatibility for existing test fixtures and older callers.
            val localStart = randomPoint("Start")
            val localDestination = generateDestination(localStart)

            start = bounds.toSourcePoint(localStart)
            destination = bounds.toSourcePoint(localDestination)
        } else {
            start = requestedOrigin.copy()
            destination = generateDestinationFromOrigin(start, bounds)
        }

        // Live origins retain their exact coordinates. Bundled-map endpoints are projected from the PNG.
        val origin = normalizedInput.originPlace ?: start.toSampleMapPlace()
        val destinationPlace = destination.toSampleMapPlace()

        val request = TripRequest(
            ownerId = normalizedInput.ownerId,
            origin = origin,
            destination = destinationPlace,
            plannedMinutes = normalizedInput.plannedMinutes,
            modes = normalizedInput.selectedTransportIds,
            zonesByTransport = normalizedInput.selectedZonesByTransport
        )
        request.validate(transportTypes.map { it.id }.toSet()).firstOrNull()?.let {
            return TripGenerationResult.InvalidInput(it.message)
        }

        val legs = when (val routed = routingService.route(request)) {
            is RoutingResult.Success -> routed.legs
            RoutingResult.NoRouteFound, is RoutingResult.OutsideServiceArea ->
                return TripGenerationResult.NoRoute
            is RoutingResult.Unavailable ->
                return TripGenerationResult.RoutingUnavailable(routed.message)
        }

        val trip = try {
            createTrip(id, normalizedInput, snapshots, origin, destinationPlace, legs, createdAt)
        } catch (_: IllegalArgumentException) {
            // Legs that break Trip's rules are a provider fault, not a user input error.
            return TripGenerationResult.RoutingUnavailable("The routing service returned an invalid route.")
        }

        return TripGenerationResult.Success(trip)
    }

    private fun createTrip(
        id: TripId,
        normalizedInput: TripGenerationInput,
        snapshots: Pair<List<TripTransportSnapshot>, List<TripRouteSnapshot>>,
        origin: Place,
        destinationPlace: Place,
        legs: List<RouteLeg>,
        createdAt: Long
    ): Trip = Trip(
        id = id,
        ownerId = normalizedInput.ownerId,
        status = TripStatus.Generated,
        title = normalizedInput.title,
        description = normalizedInput.description,
        imageUrl = normalizedInput.imageUrl,
        origin = origin,
        destination = destinationPlace,
        legs = legs,
        plannedMinutes = normalizedInput.plannedMinutes,
        selectedTransports = snapshots.first,
        selectedRoutes = snapshots.second,
        attractionIntensity = normalizedInput.attractionIntensity,
        createdAt = createdAt,
        completedAt = null,
        review = null,
        selectedZoneIds = normalizedInput.selectedZoneIds.toList(),
        selectedZonesByTransport = normalizedInput.selectedZonesByTransport.mapValues { (_, zones) -> zones.toList() }
    )

    private fun resolveSelections(
        input: TripGenerationInput,
        transportTypes: List<TransportType>,
        transportRoutes: List<TransportRoute>
    ): Pair<List<TripTransportSnapshot>, List<TripRouteSnapshot>> {
        require(input.selectedTransportIds.isNotEmpty()) {
            "Choose at least one transport type."
        }
        require(
            input.selectedTransportIds.distinct().size ==
                    input.selectedTransportIds.size
        ) {
            "Selected transport IDs must be unique."
        }
        require(
            input.selectedRouteIds.distinct().size ==
                    input.selectedRouteIds.size
        ) {
            "Selected route IDs must be unique."
        }
        require(
            transportTypes.map { it.id }.distinct().size ==
                    transportTypes.size
        ) {
            "Transport catalogue contains duplicate IDs."
        }
        require(
            transportRoutes.map { it.id }.distinct().size ==
                    transportRoutes.size
        ) {
            "Route catalogue contains duplicate IDs."
        }

        val typesById = transportTypes.associateBy { it.id }
        val routesById = transportRoutes.associateBy { it.id }

        val transports = input.selectedTransportIds.map { id ->
            val source = requireNotNull(typesById[id]) {
                "Selected transport is unavailable: $id."
            }
            TripTransportSnapshot(
                id = source.id,
                label = source.label,
                usesRoutes = source.usesRoutes
            )
        }

        val selectedTypesById = transports.associateBy { it.id }
        val routes = input.selectedRouteIds.map { id ->
            val source = requireNotNull(routesById[id]) {
                "Selected route is unavailable: $id."
            }
            val owner = selectedTypesById[source.transportTypeId]
            require(owner != null && owner.usesRoutes) {
                "Selected route $id requires its route-based transport."
            }
            TripRouteSnapshot(
                id = source.id,
                transportTypeId = source.transportTypeId,
                label = source.label
            )
        }

        return transports to routes
    }

    private fun findUnusedId(existingTripIds: Set<TripId>): TripId? {
        repeat(MAX_ID_ATTEMPTS) {
            val candidate = idFactory()
            if (candidate.isNotBlank() &&
                candidate == candidate.trim() &&
                TripId(candidate) !in existingTripIds
            ) {
                return TripId(candidate)
            }
        }
        return null
    }

    private fun randomPoint(label: String): TripPoint =
        TripPoint(
            label = label,
            x = random.nextDouble(
                SAFE_MIN.toDouble(),
                SAFE_MAX.toDouble()
            ).toFloat(),
            y = random.nextDouble(
                SAFE_MIN.toDouble(),
                SAFE_MAX.toDouble()
            ).toFloat()
        )

    /** Samples a destination in the generation bounds, with a far-corner fallback.
     * @param origin Selected source-image point, unchanged by generation.
     * @param bounds Destination area within the source PNG.
     */
    private fun generateDestinationFromOrigin(
        origin: TripPoint,
        bounds: TripGenerationBounds
    ): TripPoint {
        val minimumSeparation = minOf(
            bounds.right - bounds.left,
            bounds.bottom - bounds.top
        ).toDouble() * MIN_NORMALIZED_SEPARATION

        repeat(MAX_DESTINATION_ATTEMPTS) {
            val candidate = bounds.toSourcePoint(
                randomPoint("Destination")
            )
            if (origin.normalizedDistanceTo(candidate) >= minimumSeparation) {
                return candidate
            }
        }

        return listOf(
            TripPoint("Destination", SAFE_MIN, SAFE_MIN),
            TripPoint("Destination", SAFE_MIN, SAFE_MAX),
            TripPoint("Destination", SAFE_MAX, SAFE_MIN),
            TripPoint("Destination", SAFE_MAX, SAFE_MAX)
        )
            .map(bounds::toSourcePoint)
            .maxByOrNull { origin.normalizedDistanceTo(it) }!!
    }

    private fun generateDestination(start: TripPoint): TripPoint {
        repeat(MAX_DESTINATION_ATTEMPTS) {
            val candidate = randomPoint("Destination")
            if (start.normalizedDistanceTo(candidate) >=
                MIN_NORMALIZED_SEPARATION
            ) {
                return candidate
            }
        }

        /*
         * The farther safe corner on each axis is at least 0.35
         * normalized units away on that axis. Together, the fallback
         * separation is at least sqrt(0.35² + 0.35²), about 0.495.
         * This exceeds the required 0.25 separation.
         */
        val fallback = TripPoint(
            label = "Destination",
            x = if (start.x <= 0.5f) SAFE_MAX else SAFE_MIN,
            y = if (start.y <= 0.5f) SAFE_MAX else SAFE_MIN
        )

        check(
            start.normalizedDistanceTo(fallback) >=
                    MIN_NORMALIZED_SEPARATION
        ) {
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
