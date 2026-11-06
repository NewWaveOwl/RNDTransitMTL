package com.example.rnd_transit_mtl.model.trip

import com.example.rnd_transit_mtl.model.ids.TransportId
import com.example.rnd_transit_mtl.model.ids.UserId
import com.example.rnd_transit_mtl.model.ids.ZoneId
import kotlinx.serialization.Serializable

/**
 * What a user asks a routing service to plan.
 *
 * Like TripGenerationInput, it may hold invalid form values; call validate()
 * before routing. Coordinates are already valid because Place requires a
 * valid GeoPoint.
 *
 * @property ownerId User making the request; null for a guest.
 * @property origin Where the trip starts.
 * @property destination Where the trip ends; null lets the planner choose one.
 * @property plannedMinutes Requested trip length in minutes.
 * @property modes Enabled transport modes, in the user's order.
 * @property zonesByTransport Hard restriction: zones each enabled mode may use.
 * A mode without an entry may use any zone.
 */
@Serializable
data class TripRequest(
    val ownerId: UserId?,
    val origin: Place,
    val destination: Place?,
    val plannedMinutes: Int,
    val modes: List<TransportId>,
    val zonesByTransport: Map<TransportId, List<ZoneId>> = emptyMap()
) {
    /**
     * Checks every rule and reports each problem found.
     *
     * @param supportedModes Modes the transport catalogue offers.
     * @return Every problem found; empty when the request may be routed.
     */
    fun validate(supportedModes: Set<TransportId>): List<TripRequestError> = buildList {
        if (
            plannedMinutes !in Trip.MIN_PLANNED_MINUTES..Trip.MAX_PLANNED_MINUTES ||
            plannedMinutes % Trip.PLANNED_MINUTES_STEP != 0
        ) {
            add(TripRequestError.InvalidPlannedMinutes(plannedMinutes))
        }

        if (modes.isEmpty()) add(TripRequestError.NoModes)
        modes.groupBy { it }.filterValues { it.size > 1 }.keys
            .forEach { add(TripRequestError.DuplicateMode(it)) }
        modes.filterNot { it in supportedModes }.distinct()
            .forEach { add(TripRequestError.UnsupportedMode(it)) }

        zonesByTransport.forEach { (mode, zones) ->
            if (mode !in modes) add(TripRequestError.ZonesForUnselectedMode(mode))
            if (zones.isEmpty()) add(TripRequestError.NoZonesForMode(mode))
        }

        if (destination != null && destination.point == origin.point) {
            add(TripRequestError.SameOriginAndDestination)
        }
    }
}

/** One specific reason a TripRequest cannot be routed. */
sealed class TripRequestError {
    /** Minutes must be within the planner's range and steps; zero always fails. */
    data class InvalidPlannedMinutes(val minutes: Int) : TripRequestError()

    data object NoModes : TripRequestError()

    data class DuplicateMode(val transportId: TransportId) : TripRequestError()

    data class UnsupportedMode(val transportId: TransportId) : TripRequestError()

    /** Zones were restricted for a mode the request does not enable. */
    data class ZonesForUnselectedMode(val transportId: TransportId) : TripRequestError()

    /** A zone restriction with no zones would make the mode unusable. */
    data class NoZonesForMode(val transportId: TransportId) : TripRequestError()

    data object SameOriginAndDestination : TripRequestError()
}

/** A short sentence explaining the problem to the user. */
val TripRequestError.message: String
    get() = when (this) {
        is TripRequestError.InvalidPlannedMinutes ->
            "Choose ${Trip.MIN_PLANNED_MINUTES}–${Trip.MAX_PLANNED_MINUTES} minutes in ${Trip.PLANNED_MINUTES_STEP}-minute steps."
        TripRequestError.NoModes -> "Choose at least one transport type."
        is TripRequestError.DuplicateMode -> "Each transport type can be chosen once."
        is TripRequestError.UnsupportedMode -> "A selected transport is unavailable: $transportId."
        is TripRequestError.ZonesForUnselectedMode -> "Zones were chosen for a transport that is not selected: $transportId."
        is TripRequestError.NoZonesForMode -> "Choose at least one transit zone for $transportId."
        TripRequestError.SameOriginAndDestination -> "Start and destination must be different."
    }
