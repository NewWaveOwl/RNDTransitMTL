package com.example.rnd_transit_mtl.data.routing

import com.example.rnd_transit_mtl.model.geo.GeoPoint
import com.example.rnd_transit_mtl.model.ids.RouteId
import com.example.rnd_transit_mtl.model.ids.TransportId
import com.example.rnd_transit_mtl.model.trip.RouteLeg
import com.example.rnd_transit_mtl.model.trip.TransitTiming
import com.example.rnd_transit_mtl.model.trip.TripRequest
import com.example.rnd_transit_mtl.model.trip.TripRouteSnapshot
import com.example.rnd_transit_mtl.model.trip.TripTransportSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*
import kotlin.math.roundToLong
import kotlin.time.Clock

internal enum class OtpMode(val otp: String, id: String, label: String, val street: Boolean = false) {
    Bus("BUS", "bus", "Bus"),
    Metro("SUBWAY", "metro", "Metro"),
    Train("RAIL", "train", "Train"),
    Walk("WALK", "walk", "Walk", street = true),
    Bike("BICYCLE", "bike", "Bike", street = true);

    val id = TransportId(id)
    val snapshot = TripTransportSnapshot(this.id, label, !street)
}

/** Maps coordinate-based OTP itineraries and the retained debug journeys to shared route legs. */
internal class OtpRoutingService(
    private val demo: OtpTripDemo? = null,
    private val post: suspend (String) -> String,
    private val departure: () -> String,
    private val reportFailure: (Exception) -> Unit = {}
) : RoutingService {
    override suspend fun route(request: TripRequest): RoutingResult {
        val destination = requireNotNull(request.destination)
        val modes = request.modes.map { id ->
            OtpMode.entries.firstOrNull { it.id == id } ?: return RoutingResult.NoRouteFound
        }
        val direct = modes.singleOrNull()?.takeIf { it.street }
        if (demo != null && direct == null && (request.origin != demo.request.origin ||
                destination != demo.request.destination || request.modes != demo.request.modes)
        ) return RoutingResult.NoRouteFound

        return try {
            val body = buildRequest(request.origin.point, destination.point, modes, direct)
            val response = Json.parseToJsonElement(post(body.toString())).jsonObject
            decodeRoute(response, request.origin.point, destination.point, modes)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            reportFailure(error)
            val failureType = error::class.simpleName ?: "Unknown error"
            RoutingResult.Unavailable("Could not get a route from OTP ($failureType). See RndTransitOtp in Logcat.")
        }
    }

    private fun buildRequest(
        origin: GeoPoint,
        destination: GeoPoint,
        modes: List<OtpMode>,
        direct: OtpMode?
    ) = buildJsonObject {
        put("query", otpQuery(direct != null, demo == OtpTripDemo.StmExo, demo == null))
        putJsonObject("variables") {
            put("excludeRealtime", direct != null)
            if (direct != null || demo == null) {
                put("originLat", origin.latitude)
                put("originLon", origin.longitude)
                put("destinationLat", destination.latitude)
                put("destinationLon", destination.longitude)
                if (demo == null) put("departure", departure())
            } else {
                put("origin", demo.originStop)
                put("destination", demo.destinationStop)
                put("departure", departure())
            }
            putJsonObject("modes") {
                if (direct != null) {
                    put("directOnly", true)
                    putJsonArray("direct") { add(direct.otp) }
                } else {
                    put("transitOnly", modes.none { it.street })
                    putJsonArray("direct") { modes.filter { it.street }.forEach { add(it.otp) } }
                    putJsonObject("transit") {
                        putJsonArray("access") { add("WALK") }
                        putJsonArray("egress") { add("WALK") }
                        putJsonArray("transit") {
                            modes.filterNot { it.street }.forEach { mode ->
                                add(buildJsonObject { put("mode", mode.otp) })
                            }
                        }
                        putJsonArray("transfer") { add("WALK") }
                    }
                }
            }
        }
        if (direct == null) {
            putJsonObject("extensions") {
                putJsonArray("realtimeFeeds") { (demo?.feeds ?: listOf("STM", "EXO_TRAINS")).forEach { add(it) } }
            }
        }
    }

    /** Decodes an already accepted backend itinerary without making another routing request. */
    fun decodeResponse(response: JsonObject, request: TripRequest): RoutingResult = try {
        decodeRoute(response, request.origin.point, requireNotNull(request.destination).point,
            request.modes.mapNotNull { id -> OtpMode.entries.firstOrNull { it.id == id } })
    } catch (_: Exception) {
        RoutingResult.Unavailable("The routing service returned an invalid itinerary.")
    }

    private fun decodeRoute(
        response: JsonObject,
        origin: GeoPoint,
        destination: GeoPoint,
        modes: List<OtpMode>
    ): RoutingResult {
        val graphQlError = response.rows("errors").firstOrNull()
        check(graphQlError == null) { graphQlError?.text("message") ?: "OTP rejected the routing request." }
        val plan = requireNotNull(response.obj("data")?.obj("planConnection"))
        val errors = plan.rows("routingErrors")
        if (errors.isNotEmpty()) {
            val outside = errors.firstOrNull { it.text("code") == "OUTSIDE_BOUNDS" }
            if (outside != null) {
                val point = if (outside.text("inputField") == "TO") destination else origin
                return RoutingResult.OutsideServiceArea(point)
            }
            if (errors.all { it.text("code") in noRouteCodes }) return RoutingResult.NoRouteFound
            return RoutingResult.Unavailable("OTP could not process this route. Try again.")
        }
        val expected = modes.map { it.otp }
        check(plan["edges"] is JsonArray) { "OTP omitted the itinerary list." }
        val itinerary = plan.rows("edges").mapNotNull { it.obj("node") }
            .firstOrNull { node ->
                val actual = node.rows("legs").map { it.text("mode") }
                actual.isNotEmpty() && if (demo != null) actual == expected else
                    actual.all { it in expected || (it == "WALK" && modes.any { mode -> !mode.street }) }
            }
            ?: return RoutingResult.NoRouteFound
        val duration = requireNotNull(itinerary.number("duration")?.longOrNull)
        val health = response.obj("extensions")
        val routes = mutableListOf<TripRouteSnapshot>()
        val rawLegs = itinerary.rows("legs").map { leg ->
            val mode = OtpMode.entries.first { it.otp == leg.text("mode") }
            val route = leg.obj("route")
            val routeId = route?.text("gtfsId")?.let(::RouteId)
            if (!mode.street) checkNotNull(routeId)
            val timing = routeId?.let { id ->
                val feed = id.value.substringBefore(':')
                val source = health?.obj("realtimeByFeed")?.obj(feed)
                    ?: health?.obj("stmRealtime")?.takeIf { feed == "STM" }
                val label = requireNotNull(route).text("longName")?.takeIf { it.isNotBlank() }
                    ?: route.text("shortName")?.takeIf { it.isNotBlank() } ?: id.value
                routes += TripRouteSnapshot(id, mode.id, label)
                transitTiming(leg, source)
            }
            val shape = requireNotNull(leg.obj("legGeometry"))
            val geometry = decodePolyline(requireNotNull(shape.text("points")))
            check(geometry.size == shape.number("length")?.intOrNull && geometry.distinct().size >= 2)
            val legDuration = leg.number("duration")?.doubleOrNull
                ?.takeIf { it.isFinite() && it >= 0.0 }
                ?.roundToLong()
            RouteLeg(
                mode.id, routeId, geometry,
                requireNotNull(leg.number("distance")?.doubleOrNull),
                legDuration ?: duration.takeIf { itinerary.rows("legs").size == 1 }
                    ?: error("OTP omitted leg duration."),
                isRouted = true, transitTiming = timing
            )
        }
        check(duration > 0 && rawLegs.sumOf { it.distanceMeters } > 0)
        val legs = connectLegs(rawLegs, origin, destination)
        return RoutingResult.Success(
            legs, duration, itinerary.number("waitingTime")?.longOrNull ?: 0,
            routes.distinctBy { it.id }
        )
    }

    private fun transitTiming(leg: JsonObject, source: JsonObject?): TransitTiming {
        val predictions = source?.obj("tripUpdates")
        val alerts = source?.obj("alerts")
        val start = requireNotNull(leg.obj("start"))
        val end = requireNotNull(leg.obj("end"))
        val departureEstimate = start.obj("estimated")
        val arrivalEstimate = end.obj("estimated")
        val messages = leg.rows("alerts").mapNotNull { alert ->
            listOfNotNull(alert.text("alertHeaderText"), alert.text("alertDescriptionText"))
                .filter { it.isNotBlank() }.distinct().joinToString(" - ").takeIf { it.isNotBlank() }
        }
        return TransitTiming(
            scheduledDeparture = requireNotNull(start.text("scheduledTime")),
            scheduledArrival = requireNotNull(end.text("scheduledTime")),
            estimatedDeparture = departureEstimate?.text("time"),
            estimatedArrival = arrivalEstimate?.text("time"),
            departureDelay = departureEstimate?.text("delay"),
            arrivalDelay = arrivalEstimate?.text("delay"),
            realtimeApplied = leg.number("realTime")?.booleanOrNull == true &&
                predictions?.text("state") == "fresh" && (departureEstimate != null || arrivalEstimate != null),
            receivedAt = Clock.System.now().toEpochMilliseconds(),
            alerts = messages,
            predictionSourceStatus = predictions?.text("state"),
            predictionSourceTimestamp = predictions?.number("sourceTimestamp")?.longOrNull,
            alertSourceStatus = alerts?.text("state"),
            alertSourceTimestamp = alerts?.number("sourceTimestamp")?.longOrNull,
            skippedAlerts = alerts?.number("skippedEntities")?.intOrNull ?: 0,
            predictionMaxAgeSeconds = predictions?.number("maxAgeSeconds")?.longOrNull ?: 180,
            alertMaxAgeSeconds = alerts?.number("maxAgeSeconds")?.longOrNull ?: 600
        )
    }

    private fun connectLegs(legs: List<RouteLeg>, origin: GeoPoint, destination: GeoPoint): List<RouteLeg> = buildList {
        fun connect(from: GeoPoint, to: GeoPoint, mode: TransportId) {
            if (from == to) return
            check(from.distanceMetersTo(to) <= 35.0) { "OTP route geometry is disconnected." }
            add(RouteLeg.straightLine(mode, from, to, durationSeconds = 0))
        }
        connect(origin, legs.first().start, legs.first().transportId)
        legs.forEachIndexed { index, leg ->
            val previous = legs.getOrNull(index - 1)
            if (previous != null && previous.end != leg.start) {
                val walking = OtpMode.Walk.id
                check(previous.transportId == walking || leg.transportId == walking)
                connect(previous.end, leg.start, walking)
            }
            add(leg)
        }
        connect(legs.last().end, destination, legs.last().transportId)
    }

    private companion object {
        val noRouteCodes = setOf(
            "LOCATION_NOT_FOUND", "NO_DIRECT_MODE_CONNECTION", "NO_STOPS_IN_RANGE",
            "NO_TRANSIT_CONNECTION", "NO_TRANSIT_CONNECTION_IN_SEARCH_WINDOW",
            "OUTSIDE_SERVICE_PERIOD", "WALKING_BETTER_THAN_TRANSIT"
        )
    }
}

private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
private fun JsonObject.rows(key: String): List<JsonObject> = (this[key] as? JsonArray).orEmpty().map { it.jsonObject }
private fun JsonObject.number(key: String): JsonPrimitive? = this[key] as? JsonPrimitive
private fun JsonObject.text(key: String): String? = number(key)?.contentOrNull

private fun decodePolyline(encoded: String): List<GeoPoint> {
    var index = 0
    fun readDelta(): Int {
        var bits = 0
        var shift = 0
        var chunk: Int
        do {
            require(index < encoded.length && shift <= 25) { "Invalid OTP polyline." }
            chunk = encoded[index++].code - 63
            require(chunk in 0..63) { "Invalid OTP polyline character." }
            bits = bits or ((chunk and 31) shl shift)
            shift += 5
        } while (chunk >= 32)
        return (bits ushr 1) xor -(bits and 1)
    }
    var latitude = 0
    var longitude = 0
    return buildList {
        while (index < encoded.length) {
            latitude += readDelta()
            longitude += readDelta()
            add(GeoPoint(latitude / 100_000.0, longitude / 100_000.0))
        }
    }
}

private fun otpQuery(direct: Boolean, mixed: Boolean, coordinates: Boolean = false): String {
    val parameters = if (direct || coordinates) {
        "${'$'}originLat: CoordinateValue!, ${'$'}originLon: CoordinateValue!, " +
            "${'$'}destinationLat: CoordinateValue!, ${'$'}destinationLon: CoordinateValue!" +
            if (coordinates) ", ${'$'}departure: OffsetDateTime!" else ""
    } else "${'$'}origin: String!, ${'$'}destination: String!, ${'$'}departure: OffsetDateTime!"
    val locations = if (direct || coordinates) {
        """
        origin: {location: {coordinate: {latitude: ${'$'}originLat, longitude: ${'$'}originLon}}}
        destination: {location: {coordinate: {latitude: ${'$'}destinationLat, longitude: ${'$'}destinationLon}}}
        ${if (coordinates) "dateTime: {earliestDeparture: ${'$'}departure}" else ""}
        """.trimIndent()
    } else {
        """
        origin: {location: {stopLocation: {stopLocationId: ${'$'}origin}}}
        destination: {location: {stopLocation: {stopLocationId: ${'$'}destination}}}
        dateTime: {earliestDeparture: ${'$'}departure}
        """.trimIndent()
    }
    return """
        query TransitTestTrip($parameters, ${'$'}modes: PlanModesInput!, ${'$'}excludeRealtime: Boolean!) {
          planConnection(
            $locations
            modes: ${'$'}modes
            preferences: {transit: {timetable: {excludeRealTimeUpdates: ${'$'}excludeRealtime}}}
            ${if (!direct && mixed) "searchWindow: \"PT18H\" first: 4" else "first: 4"}
          ) {
            routingErrors { code inputField }
            edges { node {
              duration waitingTime
              legs {
                mode distance duration realTime
                route { gtfsId shortName longName }
                start { scheduledTime estimated { time delay } }
                end { scheduledTime estimated { time delay } }
                alerts { alertHash alertHeaderText alertDescriptionText activityPeriods { start end } }
                trip { pattern { code alerts {
                  alertHash alertHeaderText alertDescriptionText activityPeriods { start end }
                } } }
                legGeometry { length points }
              }
            } }
          }
        }
    """.trimIndent()
}
