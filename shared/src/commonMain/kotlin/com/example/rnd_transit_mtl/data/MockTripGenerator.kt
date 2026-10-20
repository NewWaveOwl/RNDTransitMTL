package com.example.rnd_transit_mtl.data

import com.example.rnd_transit_mtl.model.TransportRoute
import com.example.rnd_transit_mtl.model.TransportType
import com.example.rnd_transit_mtl.model.Trip
import com.example.rnd_transit_mtl.model.TripPoint
import com.example.rnd_transit_mtl.model.TripRouteSnapshot
import com.example.rnd_transit_mtl.model.TripTransportSnapshot
import kotlin.math.round
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Current planner values supplied to one generation request.
 *
 * This input may contain invalid form values. Validation occurs in
 * generate(), allowing normal input errors to become a result.
 *
 * It is not a Trip record or a saved planner-draft implementation.
 */
data class TripGenerationInput(
    val title: String,
    val description: String,
    val imageUrl: String,
    val plannedMinutes: Int,
    val selectedTransportIds: List<String>,
    val selectedRouteIds: List<String>,
    val attractionIntensity: Float
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
}

/**
 * Creates one mock trip from validated planner values.
 *
 * Coordinates describe the whole PNG in normalized units. New trips can use
 * the planner's visible crop as generation bounds. Coordinates do
 * not represent GPS coordinates or positions on actual streets.
 *
 * Demo distance is separation inside the generation area multiplied
 * by a synthetic scale and rounded to two decimal places. It is
 * intentionally independent of planned minutes and transport modes.
 *
 * Call generate() once in response to starting a trip, after checking
 * that no unfinished trip exists. Store and navigate with the returned
 * Trip. Never call it from map drawing, details, or restoration logic.
 *
 * Random, ID creation, and time are injectable for predictable checks.
 * Injected factories must not throw; time must be a nonnegative epoch
 * millisecond value. A seeded Random controls endpoints. For complete
 * reproducibility, also inject a fixed time and predictable ID source.
 */
class MockTripGenerator(
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
    fun generate(
        input: TripGenerationInput,
        transportTypes: List<TransportType>,
        transportRoutes: List<TransportRoute>,
        existingTripIds: Set<String>,
        bounds: TripGenerationBounds = TripGenerationBounds()
    ): TripGenerationResult {
        val normalizedInput = input.copy(
            title = input.title.trim(),
            description = input.description.trim(),
            imageUrl = input.imageUrl.trim(),
            selectedTransportIds = input.selectedTransportIds.toList(),
            selectedRouteIds = input.selectedRouteIds.toList()
        )

        val snapshots = try {
            Trip.validatePlannerValues(
                title = normalizedInput.title,
                description = normalizedInput.description,
                imageUrl = normalizedInput.imageUrl,
                plannedMinutes = normalizedInput.plannedMinutes,
                attractionIntensity = normalizedInput.attractionIntensity
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

        val start = randomPoint("Start")
        val destination = generateDestination(start)
        val distanceKm = round(
            start.normalizedDistanceTo(destination) *
                    DEMO_KM_PER_NORMALIZED_UNIT *
                    100.0
        ) / 100.0

        val trip = Trip(
            id = id,
            title = normalizedInput.title,
            description = normalizedInput.description,
            imageUrl = normalizedInput.imageUrl,
            start = bounds.toSourcePoint(start),
            destination = bounds.toSourcePoint(destination),
            plannedMinutes = normalizedInput.plannedMinutes,
            selectedTransports = snapshots.first,
            selectedRoutes = snapshots.second,
            attractionIntensity = normalizedInput.attractionIntensity,
            distanceKm = distanceKm,
            createdAtEpochMillis = createdAt,
            completedAtEpochMillis = null,
            review = null
        )

        return TripGenerationResult.Success(trip)
    }

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

    private fun findUnusedId(existingTripIds: Set<String>): String? {
        repeat(MAX_ID_ATTEMPTS) {
            val candidate = idFactory()
            if (candidate.isNotBlank() &&
                candidate == candidate.trim() &&
                candidate !in existingTripIds
            ) {
                return candidate
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

        /**
         * Synthetic conversion for the demo only.
         * This does not calibrate the bundled map geographically.
         */
        const val DEMO_KM_PER_NORMALIZED_UNIT: Double = 5.0
    }
}
