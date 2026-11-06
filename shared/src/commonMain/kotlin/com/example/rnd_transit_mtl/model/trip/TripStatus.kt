package com.example.rnd_transit_mtl.model.trip

import kotlinx.serialization.Serializable

/**
 * Lifecycle of a trip.
 *
 * A trip exists only after generation succeeds, so a failed generation never
 * has a status and can never become active. The only allowed moves are
 * Generated → Active (Start Trip), Active → Completed (Finish), and
 * Active → Cancelled (Cancel). Completed and Cancelled are final.
 */
@Serializable
enum class TripStatus {
    Generated,
    Active,
    Completed,
    Cancelled;

    /** True when no further status change is allowed. */
    val isFinal: Boolean get() = this == Completed || this == Cancelled

    /**
     * Reports whether a trip in this status may change to [next].
     *
     * @param next Requested new status.
     */
    fun canMoveTo(next: TripStatus): Boolean = when (this) {
        Generated -> next == Active
        Active -> next == Completed || next == Cancelled
        Completed, Cancelled -> false
    }
}
