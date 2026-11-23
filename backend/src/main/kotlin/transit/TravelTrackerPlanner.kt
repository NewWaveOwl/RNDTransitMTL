package transit

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.*
import java.time.OffsetDateTime
import java.util.concurrent.atomic.AtomicLong

/** Plans between chosen endpoints through OTP, independently of random destination generation.
 * @param fetch Shared bridge/OTP transport; provider results remain behind this public contract.
 */
internal class TravelTrackerPlanner(private val fetch: suspend (JsonObject) -> JsonObject) {
    val requests = AtomicLong()
    private val supported = mapOf("pedestrian" to "WALK", "bike" to "BICYCLE", "car" to "CAR", "bus" to "BUS", "train" to "RAIL")
    private val noRouteCodes = setOf("NO_STOPS_IN_RANGE", "NO_TRANSIT_CONNECTION", "NO_TRANSIT_CONNECTION_IN_SEARCH_WINDOW",
        "OUTSIDE_SERVICE_PERIOD", "LOCATION_NOT_FOUND", "WALKING_BETTER_THAN_TRANSIT", "NO_ROUTES_FOUND")

    /**
     * Validates a selected mode, offset-aware departure and two real points before contacting OTP.
     * @param body Public chosen-route request, never a saved itinerary.
     * @return Route candidates, explicit unsupported mode, or no route for coverage/service dates.
     */
    suspend fun plan(body: JsonObject): JsonObject {
        checkInput(body.keys == setOf("origin", "destination", "mode", "departureTime"), "Supply origin, destination, mode and departureTime only.")
        fun endpoint(name: String): Point {
            val fields = body[name] as? JsonObject ?: throw InputError("Choose valid route endpoints.")
            checkInput(fields.keys == setOf("lat", "lon"), "Each endpoint requires latitude and longitude.")
            return Point(fields["lat"].number() ?: throw InputError("Invalid route endpoint."), fields["lon"].number() ?: throw InputError("Invalid route endpoint."))
        }
        val from = endpoint("origin")
        val to = endpoint("destination")
        val departure = try { OffsetDateTime.parse(body["departureTime"].text()).toInstant() }
            catch (_: Exception) { throw InputError("Choose a departure timestamp with a UTC offset.") }
        val code = body["mode"].text() ?: throw InputError("Choose a transport mode.")
        if (code !in supported) return obj("status" to "unsupported", "itineraries" to emptyList<Any>(), "message" to "This routing mode is not supported.")
        checkInput(distance(from, to) >= 1, "Choose different departure and destination points.")
        val transit = code in listOf("bus", "train")
        val modes = if (transit) obj("direct" to emptyList<String>(), "directOnly" to false, "transitOnly" to true,
            "transit" to obj("access" to listOf("WALK"), "egress" to listOf("WALK"), "transfer" to listOf("WALK"),
                "transit" to listOf(obj("mode" to supported.getValue(code)))))
        else obj("direct" to listOf(supported.getValue(code)), "directOnly" to true, "transitOnly" to false)
        requests.incrementAndGet()
        val response = fetch(obj("query" to planQuery, "variables" to obj(
            "originLat" to from.latitude, "originLon" to from.longitude, "destinationLat" to to.latitude, "destinationLon" to to.longitude,
            "departure" to departure.toString(), "modes" to modes, "filters" to null, "excludeRealtime" to !transit, "walkSpeed" to 1.3333333333),
            "extensions" to obj("realtimeFeedsFromItinerary" to true, "realtimeFeeds" to when (code) {
                "bus" -> listOf("STM"); "train" -> listOf("EXO_TRAINS"); else -> emptyList<String>()
            })))
        if (response["errors"].items().isNotEmpty()) throw ProviderError("Route provider rejected the query.")
        val connection = response.field("data").field("planConnection") as? JsonObject ?: throw ProviderError("Missing route response.")
        val codes = connection["routingErrors"].items().map { it.field("code").text() }
        if (codes.any { it !in noRouteCodes }) throw ProviderError("Route provider unavailable.")
        val edges = connection["edges"] as? JsonArray ?: throw ProviderError("Missing route candidates.")
        val context = currentCoroutineContext()
        val itineraries = edges.take(4).mapIndexed { index, edge ->
            context.ensureActive()
            val node = edge.field("node") as? JsonObject ?: throw ProviderError("Invalid route candidate.")
            val duration = node["duration"].number()?.takeIf { it.isFinite() && it > 0 } ?: throw ProviderError("Invalid route duration.")
            val rawLegs = node["legs"].items()
            if (rawLegs.isEmpty() || rawLegs.size > 100) throw ProviderError("Invalid route legs.")
            var previous = from
            var previousArrival = departure
            var realTime = false
            var hasSelectedMode = false
            var totalDistance = 0.0
            val legs = rawLegs.map { leg ->
                context.ensureActive()
                val mode = leg.field("mode").text() ?: throw ProviderError("Invalid leg mode.")
                if (mode == supported[code]) hasSelectedMode = true
                if (mode != supported[code] && !(transit && mode == "WALK")) throw ProviderError("Unexpected route mode.")
                val meters = leg.field("distance").number()?.takeIf { it.isFinite() && it >= 0 } ?: throw ProviderError("Invalid leg distance.")
                val seconds = leg.field("duration").number()?.takeIf { it.isFinite() && it >= 0 } ?: throw ProviderError("Invalid leg duration.")
                val realtime = leg.field("realTime") == JsonPrimitive(true) &&
                    leg.field("start").field("estimated").field("time").text() != null &&
                    leg.field("end").field("estimated").field("time").text() != null
                realTime = realTime || realtime
                fun timestamp(key: String): String {
                    val time = leg.field(key)
                    return (if (realtime) time.field("estimated").field("time").text() else null)
                        ?: time.field("scheduledTime").text() ?: throw ProviderError("Missing route timing.")
                }
                val starts = timestamp("start")
                val ends = timestamp("end")
                val start = try { OffsetDateTime.parse(starts).toInstant() } catch (_: Exception) { throw ProviderError("Invalid route timing.") }
                val end = try { OffsetDateTime.parse(ends).toInstant() } catch (_: Exception) { throw ProviderError("Invalid route timing.") }
                if (start < previousArrival || end < start) throw ProviderError("Route timing is not connected.")
                previousArrival = end
                val geometry = leg.field("legGeometry").field("points").text() ?: throw ProviderError("Missing route geometry.")
                val points = try { decodePolyline(geometry) { context.ensureActive() } } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: Exception) { throw ProviderError("Invalid route geometry.") }
                if (points.size < 2 || distance(previous, points.first()) > 250) throw ProviderError("Disconnected route geometry.")
                previous = points.last()
                totalDistance += meters
                obj("mode" to mode, "durationSeconds" to seconds, "distanceMeters" to meters, "departureTime" to starts, "arrivalTime" to ends,
                    "geometry" to geometry, "routeName" to (leg.field("route").field("shortName").text() ?: leg.field("route").field("longName").text()),
                    "realtime" to realtime)
            }
            if (!hasSelectedMode || distance(previous, to) > 250) throw ProviderError("Route does not reach the selected destination.")
            obj("id" to "route-${index + 1}", "durationSeconds" to duration, "distanceMeters" to totalDistance,
                "timingBasis" to if (!transit) "estimated" else if (realTime) "realtime" else "scheduled", "legs" to legs)
        }
        return obj("status" to if (itineraries.isEmpty()) "no_route" else "success", "itineraries" to itineraries,
            "message" to if (itineraries.isEmpty()) "No route is available for these endpoints, mode and service date. Keep your manually entered times." else null)
    }
}
