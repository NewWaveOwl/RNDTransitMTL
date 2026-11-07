package com.example.rnd_transit_mtl

import com.example.rnd_transit_mtl.model.geo.GeoPoint
import com.example.rnd_transit_mtl.model.ids.RouteId
import com.example.rnd_transit_mtl.model.ids.TransportId
import com.example.rnd_transit_mtl.model.ids.TripId
import com.example.rnd_transit_mtl.model.trip.Place
import com.example.rnd_transit_mtl.model.trip.RouteLeg
import com.example.rnd_transit_mtl.model.trip.Trip
import com.example.rnd_transit_mtl.model.trip.TripRouteSnapshot
import com.example.rnd_transit_mtl.model.trip.TripStatus
import com.example.rnd_transit_mtl.model.trip.TripTransportSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlin.math.roundToInt

internal fun stmTripLoader(): suspend () -> Trip = OtpRoutingService(BuildConfig.STM_OTP_ENDPOINT)::loadTrip

/** Loads the verified, fixed STM itinerary for the Android debug flow. */
internal class OtpRoutingService(private val endpoint: String) {
    suspend fun loadTrip(): Trip = withContext(Dispatchers.IO) {
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        val response = try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 5_000
            connection.readTimeout = 10_000
            connection.instanceFollowRedirects = false
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            val request = JSONObject().put("query", stmQuery).toString()
            connection.outputStream.use { it.write(request.toByteArray(Charsets.UTF_8)) }
            check(connection.responseCode in 200..299) { "OTP returned HTTP ${connection.responseCode}" }
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
        } finally {
            connection.disconnect()
        }
        check((response.optJSONArray("errors")?.length() ?: 0) == 0)
        val plan = response.getJSONObject("data").getJSONObject("planConnection")
        check(plan.getJSONArray("routingErrors").length() == 0)
        val itinerary = plan.getJSONArray("edges").getJSONObject(0).getJSONObject("node")
        val legs = itinerary.getJSONArray("legs")
        check(legs.length() == 1) { "Expected a direct metro trip" }
        val subway = legs.getJSONObject(0)
        check(subway.getString("mode") == "SUBWAY")
        val route = subway.getJSONObject("route")
        val routeName = route.getString("longName")
        val routeId = RouteId("STM:${route.getString("shortName")}")
        val metro = TransportId("metro")
        val shape = subway.getJSONObject("legGeometry")
        val geometry = decodePolyline(shape.getString("points"))
        check(geometry.size == shape.getInt("length"))
        val duration = itinerary.getLong("duration")
        val distance = subway.getDouble("distance")
        check(duration > 0 && distance > 0)
        return@withContext Trip(
            id = TripId(UUID.randomUUID().toString()),
            status = TripStatus.Generated,
            title = routeName,
            description = "Scheduled STM trip from Lionel-Groulx to Berri-UQAM.",
            imageUrl = "",
            origin = Place("Lionel-Groulx", geometry.first()),
            destination = Place("Berri-UQAM", geometry.last()),
            legs = listOf(RouteLeg(metro, routeId, geometry, distance, duration, isRouted = true)),
            plannedMinutes = Trip.coercePlannedMinutes(((duration + 299) / 300 * 5).toInt()),
            selectedTransports = listOf(TripTransportSnapshot(metro, "Metro", true)),
            selectedRoutes = listOf(TripRouteSnapshot(routeId, metro, routeName)),
            attractionIntensity = 0f,
            createdAt = System.currentTimeMillis()
        )
    }
}

private fun decodePolyline(encoded: String): List<GeoPoint> {
    var index = 0
    fun readDelta(): Int {
        var bits = 0
        var shift = 0
        var chunk: Int
        do {
            require(index < encoded.length && shift <= 25) { "Invalid OTP polyline" }
            chunk = encoded[index++].code - 63
            require(chunk in 0..63) { "Invalid OTP polyline character" }
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

private val stmQuery = """
    query StmTestTrip {
      planConnection(
        origin: {location: {stopLocation: {stopLocationId: "STM:STATION_M132"}}}
        destination: {location: {stopLocation: {stopLocationId: "STM:STATION_M146"}}}
        dateTime: {earliestDeparture: "2026-11-06T10:00:00-05:00"}
        modes: {transitOnly: true, transit: {transit: [{mode: SUBWAY}]}}
        first: 1
      ) {
        routingErrors { code description }
        edges { node {
          duration
          legs { mode distance route { shortName longName } legGeometry { length points } }
        } }
      }
    }
""".trimIndent()
