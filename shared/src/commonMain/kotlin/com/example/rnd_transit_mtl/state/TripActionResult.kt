package com.example.rnd_transit_mtl.state

/**
 * Restricted outcomes of a store operation.
 *
 * These are returned to screen coordinators. The store does not
 * display messages or change navigation destinations.
 */
sealed class TripActionResult {
    data object Applied : TripActionResult()

    data object AlreadyCompleted : TripActionResult()

    data class ActiveTripExists(
        val tripId: String
    ) : TripActionResult()

    data class MissingTrip(
        val tripId: String
    ) : TripActionResult()

    data class InvalidInput(
        val message: String
    ) : TripActionResult()
}