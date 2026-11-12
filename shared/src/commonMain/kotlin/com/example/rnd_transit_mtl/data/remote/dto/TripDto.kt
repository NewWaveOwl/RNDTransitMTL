package com.example.rnd_transit_mtl.data.remote.dto

import com.example.rnd_transit_mtl.model.ids.RouteId
import com.example.rnd_transit_mtl.model.ids.TransportId
import com.example.rnd_transit_mtl.model.ids.TripId
import com.example.rnd_transit_mtl.model.ids.UserId
import com.example.rnd_transit_mtl.model.ids.ZoneId
import com.example.rnd_transit_mtl.model.review.TripReview
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.model.trip.TripRouteSnapshot
import com.example.rnd_transit_mtl.model.trip.TripTransportSnapshot
import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Backend form of a TripTransportSnapshot. */
@Serializable
data class TripTransportDto(
    val id: String,
    val label: String,
    @SerialName("uses_routes") val usesRoutes: Boolean
)

/** Backend form of a TripRouteSnapshot. */
@Serializable
data class TripRouteDto(
    val id: String,
    @SerialName("transport_id") val transportId: String,
    val label: String
)

/**
 * Backend form of a TripReview. The design keeps reviews in their own table;
 * a trip query can embed the matching review row in this shape.
 */
@Serializable
data class TripReviewDto(
    val overall: Int,
    val quality: Int? = null,
    val interesting: Int? = null,
    @SerialName("fun") val funRating: Int? = null,
    val comment: String = "",
    @SerialName("image_url") val imageUrl: String = ""
)

/**
 * Backend form of a Trip, one row of a trips table.
 *
 * Timestamps are ISO-8601 UTC text, which is how Postgres timestamptz
 * columns arrive through Supabase. Places, legs and snapshots are nested
 * JSON (jsonb columns). distanceKm is not stored because Trip derives it from legs.
 */
@Serializable
data class TripDto(
    val id: String,
    @SerialName("owner_id") val ownerId: String? = null,
    val status: String,
    val title: String,
    val description: String,
    @SerialName("image_url") val imageUrl: String,
    val origin: PlaceDto,
    val destination: PlaceDto,
    val legs: List<RouteLegDto>,
    @SerialName("planned_minutes") val plannedMinutes: Int,
    val transports: List<TripTransportDto>,
    val routes: List<TripRouteDto>,
    @SerialName("attraction_intensity") val attractionIntensity: Float,
    @SerialName("created_at") val createdAt: String,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("cancelled_at") val cancelledAt: String? = null,
    val review: TripReviewDto? = null,
    @SerialName("zone_ids") val zoneIds: List<String> = emptyList(),
    @SerialName("zones_by_transport") val zonesByTransport: Map<String, List<String>> = emptyMap(),
    @SerialName("routed_duration_seconds") val routedDurationSeconds: Long? = null,
    @SerialName("waiting_time_seconds") val waitingTimeSeconds: Long? = null
)

/** Converts a trip into its backend form. */
fun Trip.toDto(): TripDto = TripDto(
    id = id.value,
    ownerId = ownerId?.value,
    status = status.toDto(),
    title = title,
    description = description,
    imageUrl = imageUrl,
    origin = origin.toDto(),
    destination = destination.toDto(),
    legs = legs.map { it.toDto() },
    plannedMinutes = plannedMinutes,
    transports = selectedTransports.map { TripTransportDto(it.id.value, it.label, it.usesRoutes) },
    routes = selectedRoutes.map { TripRouteDto(it.id.value, it.transportTypeId.value, it.label) },
    attractionIntensity = attractionIntensity,
    createdAt = Instant.fromEpochMilliseconds(createdAt).toString(),
    completedAt = completedAt?.let { Instant.fromEpochMilliseconds(it).toString() },
    cancelledAt = cancelledAt?.let { Instant.fromEpochMilliseconds(it).toString() },
    review = review?.let {
        TripReviewDto(it.overall, it.quality, it.interesting, it.`fun`, it.comment, it.imageUrl)
    },
    zoneIds = selectedZoneIds.map { it.value },
    routedDurationSeconds = routedDurationSeconds,
    waitingTimeSeconds = waitingTimeSeconds,
    zonesByTransport = selectedZonesByTransport.entries.associate { (mode, zones) ->
        mode.value to zones.map { it.value }
    }
)

/**
 * Converts a backend row into a Trip.
 *
 * @return The trip, or null when any nested place or leg is invalid, the status
 * or a timestamp cannot be read, or the row breaks a Trip rule.
 */
fun TripDto.toDomainOrNull(): Trip? = runCatchingInvalid {
    Trip(
        id = TripId(id),
        ownerId = ownerId?.let(::UserId),
        status = requireNotNull(tripStatusFromDtoOrNull(status)) { "Unknown trip status: $status" },
        title = title,
        description = description,
        imageUrl = imageUrl,
        origin = requireNotNull(origin.toDomainOrNull()) { "Invalid origin." },
        destination = requireNotNull(destination.toDomainOrNull()) { "Invalid destination." },
        legs = legs.map { requireNotNull(it.toDomainOrNull()) { "Invalid leg." } },
        plannedMinutes = plannedMinutes,
        selectedTransports = transports.map { TripTransportSnapshot(TransportId(it.id), it.label, it.usesRoutes) },
        selectedRoutes = routes.map { TripRouteSnapshot(RouteId(it.id), TransportId(it.transportId), it.label) },
        attractionIntensity = attractionIntensity,
        createdAt = Instant.parse(createdAt).toEpochMilliseconds(),
        completedAt = completedAt?.let { Instant.parse(it).toEpochMilliseconds() },
        cancelledAt = cancelledAt?.let { Instant.parse(it).toEpochMilliseconds() },
        review = review?.let {
            TripReview(it.overall, it.quality, it.interesting, it.funRating, it.comment, it.imageUrl)
        },
        selectedZoneIds = zoneIds.map(::ZoneId),
        routedDurationSeconds = routedDurationSeconds,
        waitingTimeSeconds = waitingTimeSeconds,
        selectedZonesByTransport = zonesByTransport.entries.associate { (mode, zones) ->
            TransportId(mode) to zones.map(::ZoneId)
        }
    )
}
