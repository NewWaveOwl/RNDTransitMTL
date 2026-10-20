package com.example.rnd_transit_mtl.model

import kotlinx.serialization.Serializable

/**
 * Captures the selected transport information at generation time.
 *
 * This independent serializable record preserves historical labels
 * without requiring the live transport catalogue during restoration.
 */
@Serializable
data class TripTransportSnapshot(
    val id: String,
    val label: String,
    val usesRoutes: Boolean
) {
    init {
        require(id.isNotBlank()) {
            "Transport ID must not be blank."
        }
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
    val id: String,
    val transportTypeId: String,
    val label: String
) {
    init {
        require(id.isNotBlank()) {
            "Route ID must not be blank."
        }
        require(transportTypeId.isNotBlank()) {
            "Route transport ID must not be blank."
        }
        require(label.isNotBlank()) {
            "Route label must not be blank."
        }
    }
}

/**
 * One generated trip record. The shared collection will use List<Trip>.
 *
 * Endpoints and distanceKm are generated once and then stored.
 * Opening details, resizing, drawing, and restoration must reuse them.
 *
 * distanceKm is a synthetic demo value, not a measured geographic
 * distance or a street-routing result. Planned minutes describe the
 * planner choice and do not determine the simulation duration.
 *
 * Timestamps are UTC epoch milliseconds used as record information.
 * They must not be used to measure the simulation's active time.
 *
 * completedAtEpochMillis is null until completion. A null review means
 * no saved feedback, including when the user skips the review.
 *
 * List properties are read-only contracts. Callers must not pass lists
 * that they subsequently mutate. The generator creates fresh snapshots.
 */
@Serializable
data class Trip(
    val id: String,
    val title: String,
    val description: String,
    // Empty for bundled-map trips; older trips can retain their reference URL.
    val imageUrl: String,
    val start: TripPoint,
    val destination: TripPoint,
    val plannedMinutes: Int,
    val selectedTransports: List<TripTransportSnapshot>,
    val selectedRoutes: List<TripRouteSnapshot>,
    val attractionIntensity: Float,
    val distanceKm: Double,
    val createdAtEpochMillis: Long,
    val completedAtEpochMillis: Long? = null,
    val review: TripReview? = null
) {
    init {
        require(id.isNotBlank()) {
            "Trip ID must not be blank."
        }

        validatePlannerValues(
            title = title,
            description = description,
            imageUrl = imageUrl,
            plannedMinutes = plannedMinutes,
            attractionIntensity = attractionIntensity
        )

        require(start.normalizedDistanceTo(destination) > 0.0) {
            "Start and destination must be different."
        }
        require(distanceKm.isFinite() && distanceKm > 0.0) {
            "Demo distance must be finite and positive."
        }
        require(createdAtEpochMillis >= 0L) {
            "Creation timestamp must not be negative."
        }
        require(
            completedAtEpochMillis == null ||
                    completedAtEpochMillis >= createdAtEpochMillis
        ) {
            "Completion timestamp must not precede creation."
        }
        require(review == null || completedAtEpochMillis != null) {
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

        val transportsById = selectedTransports.associateBy { it.id }
        selectedRoutes.forEach { route ->
            val transport = transportsById[route.transportTypeId]
            require(transport != null && transport.usesRoutes) {
                "Each selected route must belong to a selected route-based transport."
            }
        }
    }

    companion object {
        const val MIN_PLANNED_MINUTES: Int = 0
        const val MAX_PLANNED_MINUTES: Int = 360
        const val PLANNED_MINUTES_STEP: Int = 5

        /** Keep planner selections in the supported five-minute range. */
        fun coercePlannedMinutes(value: Int): Int {
            val bounded = value.coerceIn(MIN_PLANNED_MINUTES, MAX_PLANNED_MINUTES)
            return bounded - bounded % PLANNED_MINUTES_STEP
        }

        /** Clamp arrow/drag movement at 0 and 360 minutes. */
        fun adjustPlannedMinutes(value: Int, direction: Int): Int {
            val current = coercePlannedMinutes(value)
            return when {
                direction > 0 ->
                    (current + PLANNED_MINUTES_STEP).coerceAtMost(MAX_PLANNED_MINUTES)
                direction < 0 ->
                    (current - PLANNED_MINUTES_STEP).coerceAtLeast(MIN_PLANNED_MINUTES)
                else -> current
            }
        }

        const val MIN_ATTRACTION_INTENSITY: Float = 0f
        const val MAX_ATTRACTION_INTENSITY: Float = 100f

        /**
         * Shared record-level validation for planner-derived values.
         *
         * Throws IllegalArgumentException for invalid values.
         * The generator translates these failures into InvalidInput.
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
                "Planned minutes must be 0–360 in five-minute steps."
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
