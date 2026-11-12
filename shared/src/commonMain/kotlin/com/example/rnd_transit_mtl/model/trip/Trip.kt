package com.example.rnd_transit_mtl.model.trip

import com.example.rnd_transit_mtl.model.ids.RouteId
import com.example.rnd_transit_mtl.model.ids.TripId
import com.example.rnd_transit_mtl.model.ids.UserId
import com.example.rnd_transit_mtl.model.ids.ZoneId
import com.example.rnd_transit_mtl.model.ids.TransportId
import kotlinx.serialization.Serializable
import com.example.rnd_transit_mtl.model.review.TripReview

/**
 * Captures the selected transport information at generation time.
 *
 * This independent serializable record preserves historical labels
 * without requiring the live transport catalogue during restoration.
 */
@Serializable
data class TripTransportSnapshot(
    val id: TransportId,
    val label: String,
    val usesRoutes: Boolean
) {
    init {
        require(label.isNotBlank()) {
            "Transport label must not be blank."
        }
    }
}

/**
 * Captures a selected route and its owning transport at generation time.
 */
@Serializable
data class TripRouteSnapshot(
    val id: RouteId,
    val transportTypeId: TransportId,
    val label: String
) {
    init {
        require(label.isNotBlank()) {
            "Route label must not be blank."
        }
    }
}

/**
 * One generated trip record. The shared collection will use List<Trip>.
 *
 * Places and legs are generated once and then stored. Opening details,
 * resizing, drawing, and restoration must reuse them. Legs are in travel
 * order, connect end to start, and run from origin to destination.
 * Until a routing service exists, legs are straight lines with isRouted
 * false, so distanceKm is a straight-line distance, not a street route.
 * Planned minutes describe the planner choice and do not determine the
 * simulation duration.
 *
 * status follows TripStatus: completedAt is set exactly when the
 * status is Completed. Timestamps are UTC epoch milliseconds used as record
 * information. They must not be used to measure the simulation's active time.
 *
 * A null review means no saved feedback, including when the user skips the review.
 *
 * List properties are read-only contracts. Callers must not pass lists
 * that they subsequently mutate. The generator creates fresh snapshots.
 *
 * @property ownerId User who planned the trip; null for a guest.
 * @property createdAt When the trip was generated, in UTC milliseconds since 1970-01-01.
 * @property completedAt When the trip was completed, in UTC milliseconds since 1970-01-01;
 * null unless the status is Completed.
 * @property cancelledAt When the trip was cancelled, in UTC milliseconds since 1970-01-01;
 * null unless the status is Cancelled. Only trips the user chose to keep are stored.
 */
@Serializable
data class Trip(
    val id: TripId,
    val ownerId: UserId? = null,
    val status: TripStatus,
    val title: String,
    val description: String,
    // Empty for bundled-map trips; older trips can retain their reference URL.
    val imageUrl: String,
    val origin: Place,
    val destination: Place,
    val legs: List<RouteLeg>,
    val plannedMinutes: Int,
    val selectedTransports: List<TripTransportSnapshot>,
    val selectedRoutes: List<TripRouteSnapshot>,
    val attractionIntensity: Float,
    val createdAt: Long,
    val completedAt: Long? = null,
    val cancelledAt: Long? = null,
    val review: TripReview? = null,
    /** Transit-zone constraints recorded when this trip was generated. */
    val selectedZoneIds: List<ZoneId> = emptyList(),
    /** Independent zone constraints recorded for each selected transit mode; empty on older trips. */
    val selectedZonesByTransport: Map<TransportId, List<ZoneId>> = emptyMap(),
    /** Time from requested departure through arrival, including initial waiting. */
    val routedDurationSeconds: Long? = null,
    /** Provider transfer waiting time, separate from the fixed playback clock. */
    val waitingTimeSeconds: Long? = null
) {
    init {
        require(routedDurationSeconds == null || routedDurationSeconds > 0)
        require(waitingTimeSeconds == null || waitingTimeSeconds >= 0)
        require(routedDurationSeconds == null || waitingTimeSeconds == null || waitingTimeSeconds <= routedDurationSeconds)
        validatePlannerValues(
            title = title,
            description = description,
            imageUrl = imageUrl,
            plannedMinutes = plannedMinutes,
            attractionIntensity = attractionIntensity
        )

        require(origin.point != destination.point) {
            "Start and destination must be different."
        }
        require(legs.isNotEmpty()) { "A trip needs at least one leg." }
        require(legs.first().start == origin.point && legs.last().end == destination.point) {
            "Legs must run from the origin to the destination."
        }
        require(legs.zipWithNext().all { (previous, next) -> previous.end == next.start }) {
            "Each leg must start where the previous leg ends."
        }
        require(distanceKm > 0.0) {
            "Trip distance must be positive."
        }
        require(createdAt >= 0L) {
            "Creation timestamp must not be negative."
        }
        require((completedAt != null) == (status == TripStatus.Completed)) {
            "Only a completed trip has a completion timestamp."
        }
        require(
            completedAt == null ||
                    completedAt >= createdAt
        ) {
            "Completion timestamp must not precede creation."
        }
        require((cancelledAt != null) == (status == TripStatus.Cancelled)) {
            "Only a cancelled trip has a cancellation timestamp."
        }
        require(cancelledAt == null || cancelledAt >= createdAt) {
            "Cancellation timestamp must not precede creation."
        }
        require(review == null || completedAt != null) {
            "Only a completed trip can have a saved review."
        }

        require(selectedTransports.isNotEmpty()) {
            "Choose at least one transport type."
        }
        require(
            selectedTransports.map { it.id }.distinct().size ==
                    selectedTransports.size
        ) {
            "Selected transport IDs must be unique."
        }
        require(
            selectedRoutes.map { it.id }.distinct().size ==
                    selectedRoutes.size
        ) {
            "Selected route IDs must be unique."
        }

        validateTransportZones(
            selectedTransports.filter { it.usesRoutes }.map { it.id }.toSet(),
            selectedZoneIds, selectedZonesByTransport
        )
        val transportsById = selectedTransports.associateBy { it.id }
        require(legs.all { it.transportId in transportsById }) {
            "Each leg must use a selected transport."
        }
        selectedRoutes.forEach { route ->
            val transport = transportsById[route.transportTypeId]
            require(transport != null && transport.usesRoutes) {
                "Each selected route must belong to a selected route-based transport."
            }
        }
    }

    /** Total distance of all legs in kilometers; straight-line while legs are unrouted. */
    val distanceKm: Double get() = legs.sumOf { it.distanceMeters } / 1000.0

    companion object {
        /** Checks new per-mode zone snapshots; an empty map remains valid for legacy records.
         * @param transitIds IDs of selected route-based modes.
         * @param zoneIds Combined zone snapshot, retained for older consumers.
         * @param zonesByTransport Independent zones for every selected transit mode.
         */
        internal fun validateTransportZones(
            transitIds: Set<TransportId>,
            zoneIds: List<ZoneId>,
            zonesByTransport: Map<TransportId, List<ZoneId>>
        ) {
            if (zonesByTransport.isEmpty()) return
            require(zonesByTransport.keys == transitIds) {
                "Zone choices must match the selected transit modes."
            }
            zonesByTransport.forEach { (transportId, zones) ->
                require(zones.isNotEmpty()) { "Choose at least one transit zone for $transportId." }
                require(zones.all { it.value in setOf("A", "B", "C", "D") }) {
                    "A selected transit zone is unavailable for $transportId."
                }
                require(zones.distinct().size == zones.size) {
                    "Selected transit zones must be unique for $transportId."
                }
            }
            require(zonesByTransport.values.flatten().toSet() == zoneIds.toSet()) {
                "Combined zones must match the individual transport choices."
            }
        }

        const val MIN_PLANNED_MINUTES: Int = 5
        const val MAX_PLANNED_MINUTES: Int = 360
        const val PLANNED_MINUTES_STEP: Int = 5

        /**
         * Normalizes selector values into the supported 5–360 minute range.
         *
         * Used by arrows, dragging and restored planner selections.
         * Submitted generation requests are validated separately.
         */
        fun coercePlannedMinutes(value: Int): Int {
            val bounded = value.coerceIn(
                MIN_PLANNED_MINUTES,
                MAX_PLANNED_MINUTES
            )

            return bounded - bounded % PLANNED_MINUTES_STEP
        }

        /** Moves one five-minute step without crossing either boundary. */
        fun adjustPlannedMinutes(
            value: Int,
            direction: Int
        ): Int {
            val current = coercePlannedMinutes(value)

            return when {
                direction > 0 -> {
                    (current + PLANNED_MINUTES_STEP)
                        .coerceAtMost(MAX_PLANNED_MINUTES)
                }

                direction < 0 -> {
                    (current - PLANNED_MINUTES_STEP)
                        .coerceAtLeast(MIN_PLANNED_MINUTES)
                }

                else -> current
            }
        }

        const val MIN_ATTRACTION_INTENSITY: Float = 0f
        const val MAX_ATTRACTION_INTENSITY: Float = 100f

        /**
         * Rejects invalid record and generation-request values.
         *
         * Zero, negative minutes and values outside the supported five-minute
         * steps fail validation rather than being silently accepted.
         */
        fun validatePlannerValues(
            title: String,
            description: String,
            imageUrl: String,
            plannedMinutes: Int,
            attractionIntensity: Float
        ) {
            require(title.isNotBlank()) {
                "Enter a trip title."
            }

            require(description.isNotBlank()) {
                "Enter a trip description."
            }

            require(imageUrl.isEmpty() || isSupportedImageUrl(imageUrl)) {
                "An optional image reference must be a valid HTTPS URL."
            }

            require(
                plannedMinutes in MIN_PLANNED_MINUTES..MAX_PLANNED_MINUTES &&
                    plannedMinutes % PLANNED_MINUTES_STEP == 0
            ) {
                "Choose 5–360 minutes in five-minute steps."
            }

            require(
                attractionIntensity.isFinite() &&
                    attractionIntensity in
                        MIN_ATTRACTION_INTENSITY..MAX_ATTRACTION_INTENSITY
            ) {
                "Attraction intensity must be finite and between 0 and 100."
            }
        }

        /**
         * Checks the supported demo input format without making a request.
         *
         * Supports HTTPS URLs with a DNS-style host or IPv4-style host
         * and an optional numeric port. Credentials and IPv6 literals
         * are outside this deliberately small input contract.
         *
         * Passing this check does not prove that the URL exists,
         * permits access, or returns an image.
         */
        fun isSupportedImageUrl(value: String): Boolean {
            if (value != value.trim()) return false
            if (!value.startsWith("https://", ignoreCase = true)) return false
            if (value.any {
                    it.isWhitespace() || it.code < 32 || it == '\\'
                }
            ) {
                return false
            }

            val authority = value.substring(8)
                .substringBefore('/')
                .substringBefore('?')
                .substringBefore('#')

            val parts = authority.split(':')
            if (parts.size !in 1..2) return false

            val host = parts[0]
            if (host.isEmpty() || host.length > 253) return false

            val labels = host.split('.')
            if (labels.any { label ->
                    label.isEmpty() ||
                            label.length > 63 ||
                            !isAsciiLetterOrDigit(label.first()) ||
                            !isAsciiLetterOrDigit(label.last()) ||
                            label.any {
                                !isAsciiLetterOrDigit(it) && it != '-'
                            }
                }
            ) {
                return false
            }

            if (parts.size == 2) {
                val portText = parts[1]
                if (portText.isEmpty() ||
                    portText.any { it !in '0'..'9' }
                ) {
                    return false
                }
                val port = portText.toIntOrNull() ?: return false
                if (port !in 1..65535) return false
            }

            return true
        }

        private fun isAsciiLetterOrDigit(value: Char): Boolean =
            value in 'a'..'z' ||
                    value in 'A'..'Z' ||
                    value in '0'..'9'
    }
}
