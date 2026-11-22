package com.example.rnd_transit_mtl.model.trip

import kotlin.math.roundToInt

/**
 * Completion figures derived from the saved itinerary. Timing, distance and steps remain estimates;
 * the application does not claim that a route's average speed is a measured fastest GPS speed.
 * @property distanceByTransportKm Distance grouped by the modes actually used by the route.
 * @property durationSeconds Estimated itinerary time, including transit waits.
 * @property walkingSeconds Estimated time spent on walking legs.
 * @property walkingSteps Approximate steps at 0.75 metres per step, never a pedometer reading.
 * @property averageSpeedKmh Estimated whole-route average; null if duration is unavailable.
 */
data class TripCompletionStatistics(
    val distanceByTransportKm: Map<String, Double>,
    val durationSeconds: Long,
    val walkingSeconds: Long,
    val walkingSteps: Int,
    val averageSpeedKmh: Double?
) {
    val distanceKm: Double get() = distanceByTransportKm.values.sum()
    val walkingDistanceKm: Double get() = distanceByTransportKm["walk"] ?: 0.0

    companion object {
        /** Reads only the stored trip; showing or expanding statistics never regenerates geometry.
         * @param trip Completed or preview record whose itinerary is being summarized.
         */
        fun from(trip: Trip): TripCompletionStatistics {
            val byMode = trip.legs.groupBy { if (it.transportId.value == "walking") "walk" else it.transportId.value }
                .mapValues { (_, legs) -> legs.sumOf { it.distanceMeters } / 1000.0 }
            val walking = trip.legs.filter { it.transportId.value in listOf("walk", "walking") }
            val duration = trip.routedDurationSeconds ?: trip.legs.sumOf { it.durationSeconds }
            return TripCompletionStatistics(byMode, duration,
                walking.sumOf { it.durationSeconds },
                (walking.sumOf { it.distanceMeters } / 0.75).roundToInt(),
                if (duration > 0) trip.distanceKm * 3600.0 / duration else null)
        }
    }
}
