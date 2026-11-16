package com.example.rnd_transit_mtl.data.generation

import androidx.compose.runtime.staticCompositionLocalOf
import com.example.rnd_transit_mtl.data.routing.OtpMode
import com.example.rnd_transit_mtl.data.routing.OtpItineraryDecoder
import com.example.rnd_transit_mtl.data.routing.RoutingResult
import com.example.rnd_transit_mtl.model.geo.GeoPoint
import com.example.rnd_transit_mtl.model.ids.TripId
import com.example.rnd_transit_mtl.model.transport.TransportType
import com.example.rnd_transit_mtl.model.trip.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.network.sockets.ConnectTimeoutException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.time.TimeSource
import kotlin.time.Clock
import kotlin.uuid.Uuid

@Serializable
internal data class BackendGenerationRequest(
    val origin: Place,
    val plannedMinutes: Int,
    val modes: List<String>,
    val zonesByTransport: Map<String, List<String>>,
    val selectedRouteIds: List<String>,
    val excludedDestinations: List<GeoPoint>,
    val walkingPreference: String = "Amble",
    val bikeSpeedKmh: Int = 18,
    val requestId: String = ""
)

@Serializable
internal data class BackendGenerationResponse(
    val status: String,
    val message: String? = null,
    val origin: Place? = null,
    val destination: Place? = null,
    val elapsedSeconds: Long? = null,
    val departure: String? = null,
    val otp: JsonObject? = null,
    val travelTimeSeconds: Long? = null,
    val orientationBufferSeconds: Long = 0
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
    private val reportFailure: (Exception) -> Unit = {},
    private val diagnostic: (String) -> Unit = {}
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
            require(input.bikeSpeedKmh in 8..32) { "Choose a bike speed from 8 to 32 km/h." }
            require(input.walkingPreference in listOf("Amble", "Walk", "Dash")) { "Choose Amble, Walk or Dash." }
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
        val requestId = Uuid.random().toString()
        val started = TimeSource.Monotonic.markNow()
        var stage = "encode"
        fun log(event: String) = diagnostic("request=$requestId elapsedMs=${started.elapsedNow().inWholeMilliseconds} $event")
        return try {
            log("start modes=${input.selectedTransportIds.joinToString { it.value }} minutes=${input.plannedMinutes} bikeSpeedKmh=${input.bikeSpeedKmh}")
            val request = BackendGenerationRequest(
                origin, input.plannedMinutes, input.selectedTransportIds.map { it.value },
                input.selectedZonesByTransport.entries.associate { (mode, zones) -> mode.value to zones.map { it.value } },
                input.selectedRouteIds.map { it.value }, excludedDestinations.takeLast(10), input.walkingPreference, input.bikeSpeedKmh, requestId
            )
            val payload = json.encodeToString(request)
            stage = "http"
            val text = post(payload)
            currentCoroutineContext().ensureActive()
            stage = "decode"
            require(text.length <= 2_000_000)
            val response = json.decodeFromString<BackendGenerationResponse>(text)
            log("response status=${response.status}")
            stage = "validate"
            when (response.status) {
                "no_trip" -> TripGenerationResult.NoRoute
                "invalid_input" -> TripGenerationResult.InvalidInput(response.message ?: "Check your trip choices.")
                "timeout" -> TripGenerationResult.RoutingUnavailable("Trip search timed out. Retry or adjust your choices.")
                "unauthorized" -> TripGenerationResult.RoutingUnavailable("Sign in again to generate a trip.")
                "success" -> acceptedTrip(response, input, origin, existingTripIds, requestId).also { log("validated result=${it::class.simpleName}") }
                else -> TripGenerationResult.RoutingUnavailable(response.message ?: "Routing service unavailable. Try again.")
            }
        } catch (cancelled: CancellationException) {
            log("cancelled stage=$stage")
            throw cancelled
        } catch (error: Exception) {
            log("failed stage=$stage ${generationFailureTrace(error)}")
            reportFailure(error)
            val message = when {
                error is HttpRequestTimeoutException || error is SocketTimeoutException || error is ConnectTimeoutException -> "Trip planner request timed out. Check your connection and retry."
                error is GenerationHttpFailure -> when(error.status) {
                    401, 403 -> "Sign in again to generate a trip."
                    429 -> "A trip is already running or requests are too frequent. Try again in ${error.retryAfterSeconds ?: 3} seconds."
                    400 -> "The planner rejected these choices. Check your settings and retry."
                    else -> "Routing service unavailable. Try again shortly."
                }
                stage == "decode" -> "The planner returned an unreadable response. Retry; check the server or tunnel if this continues."
                stage == "validate" -> "The planner returned an invalid route. Retry or adjust your choices."
                else -> "Could not reach the trip planner. Check your connection and retry."
            }
            if(error is GenerationHttpFailure && error.status == 400) TripGenerationResult.InvalidInput(message)
            else TripGenerationResult.RoutingUnavailable(message)
        }
    }

    private fun acceptedTrip(
        response: BackendGenerationResponse, input: TripGenerationInput, origin: Place, existingIds: Set<TripId>, requestId: String
    ): TripGenerationResult {
        require(response.origin == origin) { "The backend changed the confirmed origin." }
        val destination = requireNotNull(response.destination)
        val elapsed = requireNotNull(response.elapsedSeconds)
        require(elapsed in 1..input.plannedMinutes * 60L)
        require(response.orientationBufferSeconds in 0..elapsed)
        response.travelTimeSeconds?.let { require(it > 0 && it + response.orientationBufferSeconds == elapsed) }
        val request = TripRequest(input.ownerId, origin, destination, input.plannedMinutes, input.selectedTransportIds,
            input.selectedZonesByTransport)
        val decoder = OtpItineraryDecoder(reportFailure = { error ->
            diagnostic("request=$requestId failed stage=itinerary-decode ${generationFailureTrace(error)}")
            reportFailure(error)
        })
        val route = decoder.decodeResponse(requireNotNull(response.otp), request)
        if (route !is RoutingResult.Success) return TripGenerationResult.RoutingUnavailable("The trip planner returned an invalid route.")
        require(route.durationSeconds <= elapsed)
        if(input.selectedTransportIds.map { it.value }.toSet()==setOf("walk","bike")) {
            val walking=route.legs.filter { it.transportId.value=="walk" }
            val cycling=route.legs.filter { it.transportId.value=="bike" }
            require(walking.sumOf { it.distanceMeters }>0 && walking.sumOf { it.durationSeconds }>0 &&
                cycling.sumOf { it.distanceMeters }>walking.sumOf { it.distanceMeters } &&
                cycling.sumOf { it.durationSeconds }>walking.sumOf { it.durationSeconds }) {
                "Walk + Bike requires a walking approach and cycling as the main transport."
            }
        }
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
            routedDurationSeconds = elapsed, waitingTimeSeconds = route.waitingTimeSeconds,
            orientationBufferSeconds = response.orientationBufferSeconds, walkingPreference = input.walkingPreference,
            requestedTransportIds = input.selectedTransportIds.toList()
        )
        return TripGenerationResult.Success(trip)
    }
}

internal val LocalBackendTripGenerator = staticCompositionLocalOf<BackendTripGenerator?> { null }

/** HTTP status only: never retain response bodies, endpoints or authorization headers. */
internal class GenerationHttpFailure(val status: Int, val retryAfterSeconds: Long? = null) : Exception("Trip planner HTTP $status")

/** Keep exception types and code frames; messages can contain credentials or full request URLs. */
internal fun generationFailureTrace(error: Throwable, depth: Int = 0): String = buildString {
    append(error::class.simpleName)
    // Remove the message line, retaining source frames only.
    val frames = error.stackTraceToString().lineSequence().drop(1)
        .filter { it.trimStart().startsWith("at ") }.take(8)
    frames.forEach { append("\n"); append(it) }
    if(depth < 3) error.cause?.takeIf { it !== error }?.let {
        append("\nCaused by: "); append(generationFailureTrace(it, depth + 1))
    }
}
