package com.example.rnd_transit_mtl.model.trip

import com.example.rnd_transit_mtl.model.ids.TripId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Additional journey information; null on Trip preserves the legacy one-way contract. */
@Serializable
data class TripJourney(
    val kind: TripJourneyKind,
    val outboundTripId: TripId? = null,
    val turnaround: Place? = null,
    val waypoints: List<Place> = emptyList()
) {
    init {
        require(waypoints.isEmpty() || (kind == TripJourneyKind.RoundTrip && waypoints.size in 2..6 &&
            waypoints.map { it.point }.distinct().size == waypoints.size && turnaround == waypoints.first()))
        require((kind == TripJourneyKind.Return) == (outboundTripId != null))
        require((kind == TripJourneyKind.RoundTrip) == (turnaround != null))
    }
}

/** Flags for a loop; old single-turnaround records remain readable. */
val Trip.explorationWaypoints: List<Place>
    get() = journey?.let { it.waypoints.ifEmpty { listOfNotNull(it.turnaround) } }.orEmpty()

/** Return journeys are separately routed records; round trips contain a complete loop. */
@Serializable
enum class TripJourneyKind {
    @SerialName("return") Return,
    @SerialName("round_trip") RoundTrip
}
