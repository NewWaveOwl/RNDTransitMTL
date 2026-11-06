package com.example.rnd_transit_mtl.data.remote.dto

import com.example.rnd_transit_mtl.model.ids.TransportId
import com.example.rnd_transit_mtl.model.ids.UserId
import com.example.rnd_transit_mtl.model.ids.ZoneId
import com.example.rnd_transit_mtl.model.trip.TripRequest
import com.example.rnd_transit_mtl.model.trip.TripStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Backend form of a TripRequest. Rule checks such as minutes and supported
 * modes stay in TripRequest.validate(); this conversion only rebuilds values.
 */
@Serializable
data class TripRequestDto(
    @SerialName("owner_id") val ownerId: String? = null,
    val origin: PlaceDto,
    val destination: PlaceDto? = null,
    @SerialName("planned_minutes") val plannedMinutes: Int,
    val modes: List<String>,
    @SerialName("zones_by_transport") val zonesByTransport: Map<String, List<String>> = emptyMap()
)

/** Converts a request into its backend form. */
fun TripRequest.toDto(): TripRequestDto = TripRequestDto(
    ownerId = ownerId?.value,
    origin = origin.toDto(),
    destination = destination?.toDto(),
    plannedMinutes = plannedMinutes,
    modes = modes.map { it.value },
    zonesByTransport = zonesByTransport.entries.associate { (mode, zones) ->
        mode.value to zones.map { it.value }
    }
)

/**
 * Converts a backend row into a TripRequest.
 *
 * @return The request, or null when an ID is blank or a place is invalid.
 */
fun TripRequestDto.toDomainOrNull(): TripRequest? = runCatchingInvalid {
    TripRequest(
        ownerId = ownerId?.let(::UserId),
        origin = requireNotNull(origin.toDomainOrNull()) { "Invalid origin." },
        destination = destination?.let { requireNotNull(it.toDomainOrNull()) { "Invalid destination." } },
        plannedMinutes = plannedMinutes,
        modes = modes.map(::TransportId),
        zonesByTransport = zonesByTransport.entries.associate { (mode, zones) ->
            TransportId(mode) to zones.map(::ZoneId)
        }
    )
}

/** Backend text for a status: "generated", "active", "completed" or "cancelled". */
fun TripStatus.toDto(): String = name.lowercase()

/**
 * Reads a status stored by the backend.
 *
 * @return The status, or null for unknown text.
 */
fun tripStatusFromDtoOrNull(text: String): TripStatus? =
    TripStatus.entries.firstOrNull { it.name.equals(text, ignoreCase = true) }
