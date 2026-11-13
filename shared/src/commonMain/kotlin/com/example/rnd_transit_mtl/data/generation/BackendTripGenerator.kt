package com.example.rnd_transit_mtl.data.generation

import androidx.compose.runtime.staticCompositionLocalOf
import com.example.rnd_transit_mtl.data.routing.OtpMode
import com.example.rnd_transit_mtl.data.routing.OtpRoutingService
import com.example.rnd_transit_mtl.data.routing.RoutingResult
import com.example.rnd_transit_mtl.model.geo.GeoPoint
import com.example.rnd_transit_mtl.model.ids.TripId
import com.example.rnd_transit_mtl.model.transport.TransportType
import com.example.rnd_transit_mtl.model.trip.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock
import kotlin.uuid.Uuid

@Serializable
internal data class BackendGenerationRequest(
    val origin: Place,
    val plannedMinutes: Int,
    val modes: List<String>,
    val zonesByTransport: Map<String, List<String>>,
    val selectedRouteIds: List<String>,
    val excludedDestinations: List<GeoPoint>
)

@Serializable
internal data class BackendGenerationResponse(
    val status: String,
    val message: String? = null,
    val origin: Place? = null,
    val destination: Place? = null,
    val elapsedSeconds: Long? = null,
    val departure: String? = null,
    val otp: JsonObject? = null
)

/** One GO request. Destination search and OTP calls execute exclusively on the backend.
 * @param post Cancellable JSON transport to the generation endpoint.
 * @param idFactory Creates IDs for the local trip store.
 * @param now Current epoch milliseconds for the saved trip record.
 * @param reportFailure Optional diagnostics; never handles cancellation.
 */
internal class BackendTripGenerator(
    private val post: suspend (String) -> String,
    private val idFactory: () -> String = { Uuid.random().toString() },
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val reportFailure: (Exception) -> Unit = {}
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Preserve exact origin and accepted provider geometry; never regenerate when opening a trip.
     * @param input Confirmed origin and planner choices.
     * @param transportTypes Supported app catalogue used for request validation.
     * @param existingTripIds IDs already reserved in the current store.
     * @param excludedDestinations Previous proposals to avoid repeating.
     */
    suspend fun generate(
        input: TripGenerationInput,
        transportTypes: List<TransportType>,
        existingTripIds: Set<TripId>,
        excludedDestinations: List<GeoPoint> = emptyList()
    ): TripGenerationResult {
        val origin = input.originPlace ?: return TripGenerationResult.InvalidInput("Choose a starting point.")
        try {
            Trip.validatePlannerValues(input.title.trim(), input.description.trim(), input.imageUrl.trim(),
                input.plannedMinutes, input.attractionIntensity)
            require(input.selectedTransportIds.isNotEmpty()) { "Choose at least one transport type." }
            require(input.selectedTransportIds.distinct().size == input.selectedTransportIds.size)
            require(input.selectedTransportIds.all { id -> OtpMode.entries.any { it.id == id } }) {
                "Choose walking, cycling, STM bus/métro or exo train. REM routing is not available yet."
            }
            require(input.selectedTransportIds.all { id -> transportTypes.any { it.id == id } })
            Trip.validateTransportZones(
                transportTypes.filter { it.usesRoutes && it.id in input.selectedTransportIds }.map { it.id }.toSet(),
                input.selectedZoneIds, input.selectedZonesByTransport
            )
        } catch (invalid: IllegalArgumentException) {
            return TripGenerationResult.InvalidInput(invalid.message ?: "Check your trip choices.")
        }
        return try {
            val request = BackendGenerationRequest(
                origin, input.plannedMinutes, input.selectedTransportIds.map { it.value },
                input.selectedZonesByTransport.entries.associate { (mode, zones) -> mode.value to zones.map { it.value } },
                input.selectedRouteIds.map { it.value }, excludedDestinations.takeLast(10)
            )
            val response = json.decodeFromString<BackendGenerationResponse>(post(json.encodeToString(request)))
            when (response.status) {
                "no_trip" -> TripGenerationResult.NoRoute
                "invalid_input" -> TripGenerationResult.InvalidInput(response.message ?: "Check your trip choices.")
                "unauthorized" -> TripGenerationResult.RoutingUnavailable("Sign in again to generate a trip.")
                "success" -> acceptedTrip(response, input, origin, existingTripIds)
                else -> TripGenerationResult.RoutingUnavailable(response.message ?: "Routing service unavailable. Try again.")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            reportFailure(error)
            TripGenerationResult.RoutingUnavailable("Could not reach the trip planner. Try again.")
        }
    }

    private fun acceptedTrip(
        response: BackendGenerationResponse, input: TripGenerationInput, origin: Place, existingIds: Set<TripId>
    ): TripGenerationResult {
        require(response.origin == origin) { "The backend changed the confirmed origin." }
        val destination = requireNotNull(response.destination)
        val elapsed = requireNotNull(response.elapsedSeconds)
        require(elapsed in 1..input.plannedMinutes * 60L)
        val request = TripRequest(input.ownerId, origin, destination, input.plannedMinutes, input.selectedTransportIds,
            input.selectedZonesByTransport)
        val decoder = OtpRoutingService(post = { error("An accepted trip must never be routed again.") },
            departure = { "" }, reportFailure = reportFailure)
        val route = decoder.decodeResponse(requireNotNull(response.otp), request)
        if (route !is RoutingResult.Success) return TripGenerationResult.RoutingUnavailable("The trip planner returned an invalid route.")
        require(route.durationSeconds <= elapsed)
        val id = (1..16).asSequence().map { TripId(idFactory()) }.firstOrNull { it !in existingIds }
            ?: return TripGenerationResult.IdUnavailable
        val modes = (input.selectedTransportIds + route.legs.map { it.transportId }).distinct()
        val trip = Trip(
            id = id, ownerId = input.ownerId, status = TripStatus.Generated,
            title = input.title.trim(), description = "${origin.label} to ${destination.label}", imageUrl = input.imageUrl.trim(),
            origin = origin, destination = destination, legs = route.legs, plannedMinutes = input.plannedMinutes,
            selectedTransports = modes.map { mode -> OtpMode.entries.first { it.id == mode }.snapshot },
            selectedRoutes = route.routes, attractionIntensity = input.attractionIntensity, createdAt = now(),
            selectedZoneIds = input.selectedZoneIds.toList(),
            selectedZonesByTransport = input.selectedZonesByTransport.mapValues { it.value.toList() },
            routedDurationSeconds = elapsed, waitingTimeSeconds = route.waitingTimeSeconds
        )
        return TripGenerationResult.Success(trip)
    }
}

internal val LocalBackendTripGenerator = staticCompositionLocalOf<BackendTripGenerator?> { null }
