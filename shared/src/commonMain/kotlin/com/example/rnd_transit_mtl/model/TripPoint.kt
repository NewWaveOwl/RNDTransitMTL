package com.example.rnd_transit_mtl.model

import kotlinx.serialization.Serializable
import kotlin.math.sqrt

/**
 * A labelled position in the mock map's normalized drawing rectangle.
 *
 * x = 0 is the left edge and x = 1 is the right edge.
 * y = 0 is the top edge and y = 1 is the bottom edge.
 *
 * These values are neither pixels nor geographic latitude/longitude.
 * The map component converts them to its current drawing dimensions.
 */
@Serializable
data class TripPoint(
    val label: String,
    val x: Float,
    val y: Float
) {
    init {
        require(label.isNotBlank()) {
            "A trip point must have a label."
        }
        require(x.isFinite() && x in 0f..1f) {
            "Point x must be finite and between 0 and 1."
        }
        require(y.isFinite() && y in 0f..1f) {
            "Point y must be finite and between 0 and 1."
        }
    }

    /**
     * Straight-line separation in normalized map units.
     *
     * This is useful for avoiding overlapping demo endpoints.
     * It is not a geographic or road distance.
     */
    fun normalizedDistanceTo(other: TripPoint): Double {
        val dx = other.x.toDouble() - x.toDouble()
        val dy = other.y.toDouble() - y.toDouble()
        return sqrt(dx * dx + dy * dy)
    }
}