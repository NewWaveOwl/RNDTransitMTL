package com.example.rnd_transit_mtl.data.routing

import com.example.rnd_transit_mtl.data.generation.TripGenerationResult
import com.example.rnd_transit_mtl.model.geo.GeoPoint
import com.example.rnd_transit_mtl.model.ids.TransportId
import com.example.rnd_transit_mtl.model.ids.TripId
import com.example.rnd_transit_mtl.model.trip.Place
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.model.trip.TripRequest
import com.example.rnd_transit_mtl.model.trip.TripStatus
import com.example.rnd_transit_mtl.model.trip.message
import kotlin.time.Clock
import kotlin.uuid.Uuid

typealias DebugTripLoader = suspend (List<TransportId>) -> TripGenerationResult

enum class OtpTripDemo(
    internal val request: TripRequest,
    internal val originStop: String,
    internal val destinationStop: String,
    internal val feeds: List<String>
) {
    StmBus(
        TripRequest(
            null, Place("Saint-Urbain / du Mont-Royal", GeoPoint(45.51871, -73.58712)),
            Place("Saint-Urbain / Sherbrooke", GeoPoint(45.51117, -73.57025)),
            10, listOf(OtpMode.Bus.id)
        ),
        "STM:62787", "STM:52486", listOf("STM")
    ),
    StmExo(
        TripRequest(
            null, Place("Lionel-Groulx", GeoPoint(45.48250, -73.58018)),
            Place("Montréal-Ouest", GeoPoint(45.45367, -73.64173)),
            20, listOf(OtpMode.Metro.id, OtpMode.Walk.id, OtpMode.Train.id)
        ),
        "STM:STATION_M132", "EXO_TRAINS:MTL1C", listOf("STM", "EXO_TRAINS")
    );

    fun loader(post: suspend (String) -> String, departure: () -> String): DebugTripLoader {
        val router = OtpRoutingService(this, post, departure)
        return { enabledModes -> loadOtpDebugTrip(request, enabledModes, router) }
    }
}

internal suspend fun loadOtpDebugTrip(
    request: TripRequest,
    enabledModes: List<TransportId>,
    router: RoutingService
): TripGenerationResult {
    request.validate(OtpMode.entries.map { it.id }.toSet()).firstOrNull()?.let {
        return TripGenerationResult.InvalidInput(it.message)
    }
    val destination = request.destination
        ?: return TripGenerationResult.InvalidInput("Choose a destination.")

    var result = router.route(request)
    var usedFallback = false
    if (result !is RoutingResult.Success) {
        // Retry the same endpoints in the planner's selected mode order.
        val fallbackModes = enabledModes.filter { it == OtpMode.Walk.id || it == OtpMode.Bike.id }.distinct()
        for (mode in fallbackModes) {
            val alternative = router.route(request.copy(modes = listOf(mode), zonesByTransport = emptyMap()))
            when (alternative) {
                is RoutingResult.Success -> {
                    result = alternative
                    usedFallback = true
                    break
                }
                is RoutingResult.Unavailable -> result = alternative
                else -> Unit
            }
        }
    }

    val route = when (val outcome = result) {
        is RoutingResult.Success -> outcome
        is RoutingResult.Unavailable -> return TripGenerationResult.RoutingUnavailable(outcome.message)
        RoutingResult.NoRouteFound, is RoutingResult.OutsideServiceArea -> return TripGenerationResult.NoRoute
    }
    val transports = route.legs.map { leg -> OtpMode.entries.first { it.id == leg.transportId }.snapshot }.distinct()
    val title = if (usedFallback) {
        "${transports.single().label} fallback"
    } else {
        route.routes.joinToString(" → ") { it.label }.ifBlank { destination.label }
    }
    return try {
        TripGenerationResult.Success(Trip(
            id = TripId(Uuid.random().toString()),
            ownerId = request.ownerId,
            status = TripStatus.Generated,
            title = title,
            description = "${request.origin.label} to ${destination.label}",
            imageUrl = "",
            origin = request.origin,
            destination = destination,
            legs = route.legs,
            plannedMinutes = Trip.coercePlannedMinutes(((route.durationSeconds + 299) / 300 * 5).toInt()),
            selectedTransports = transports,
            selectedRoutes = route.routes,
            attractionIntensity = 0f,
            createdAt = Clock.System.now().toEpochMilliseconds()
        ))
    } catch (_: IllegalArgumentException) {
        TripGenerationResult.RoutingUnavailable("The routing service returned an invalid route.")
    }
}
