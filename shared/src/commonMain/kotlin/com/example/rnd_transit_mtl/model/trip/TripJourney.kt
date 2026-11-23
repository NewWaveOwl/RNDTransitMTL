package com.example.rnd_transit_mtl.model.trip

import com.example.rnd_transit_mtl.model.ids.TripId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Additional journey information; null on Trip preserves the legacy one-way contract. */
@Serializable
data class TripJourney(
    val kind: TripJourneyKind,
    val outboundTripId: TripId? = null,
    val turnaround: Place? = null
) {
    init {
        require((kind == TripJourneyKind.Return) == (outboundTripId != null))
        require((kind == TripJourneyKind.RoundTrip) == (turnaround != null))
    }
}

/** Return journeys are separately routed records; round trips contain both routed directions. */
@Serializable
enum class TripJourneyKind {
    @SerialName("return") Return,
    @SerialName("round_trip") RoundTrip
}
