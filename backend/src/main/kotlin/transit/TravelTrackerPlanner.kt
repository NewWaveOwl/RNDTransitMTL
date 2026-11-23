package transit

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.*
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

/** Plans between chosen endpoints through OTP, independently of random destination generation.
 * @param capabilitySource Live router metadata; absent in isolated route-only fixtures.
 * @param fetch Shared bridge/OTP transport; provider results remain behind this public contract.
 */
internal class TravelTrackerPlanner(
    private val capabilitySource: OtpCapabilities? = null,
    private val fetch: suspend (JsonObject) -> JsonObject,
) {
    val requests = AtomicLong()
    private val supported = mapOf("pedestrian" to "WALK", "bike" to "BICYCLE", "car" to "CAR", "bus" to "BUS", "train" to "RAIL")
    private val noRouteCodes = setOf("NO_STOPS_IN_RANGE", "NO_TRANSIT_CONNECTION", "NO_TRANSIT_CONNECTION_IN_SEARCH_WINDOW",
        "OUTSIDE_SERVICE_PERIOD", "LOCATION_NOT_FOUND", "WALKING_BETTER_THAN_TRANSIT", "NO_ROUTES_FOUND")

    /** @return Authenticated, live router metadata, independent of address lookup and saved trips. */
    suspend fun capabilities(): JsonObject = capabilitySource?.read() ?: throw ProviderError("Router capability metadata is unavailable.")

    /**
     * Validates a selected mode, offset-aware departure and two real points before contacting OTP.
     * @param body Public chosen-route request, never a saved itinerary.
     * @return Route candidates, explicit unsupported mode, or no route for coverage/service dates.
     */
    suspend fun plan(body: JsonObject): JsonObject {
        val required = setOf("origin", "destination", "mode", "departureTime")
        checkInput(body.keys.containsAll(required) && body.keys.all { it in required || it == "timeZone" },
            "Supply origin, destination, mode, departureTime and optional timeZone only.")
        fun endpoint(name: String): Point {
            val fields = body[name] as? JsonObject ?: throw InputError("Choose valid route endpoints.")
            checkInput(fields.keys == setOf("lat", "lon"), "Each endpoint requires latitude and longitude.")
            return Point(fields["lat"].number() ?: throw InputError("Invalid route endpoint."), fields["lon"].number() ?: throw InputError("Invalid route endpoint."))
        }
        val from = endpoint("origin")
        val to = endpoint("destination")
        val requestedDeparture = try { OffsetDateTime.parse(body["departureTime"].text()) }
            catch (_: Exception) { throw InputError("Choose a departure timestamp with a UTC offset.") }
        val timeZone = when (val requestedZone = body["timeZone"]) {
            null, JsonNull -> null
            else -> {
                val name = requestedZone.text()?.takeIf { it.isNotBlank() } ?: throw InputError("Choose a valid IANA time zone.")
                val zone = try { ZoneId.of(name) } catch (_: Exception) { throw InputError("Choose a valid IANA time zone.") }
                checkInput(name in ZoneId.getAvailableZoneIds(), "Choose a valid IANA time zone.")
                checkInput(requestedDeparture.offset in zone.rules.getValidOffsets(requestedDeparture.toLocalDateTime()),
                    "Departure offset does not match the selected time zone on this date.")
                zone
            }
        }
        val departure = requestedDeparture.toInstant()
        var coverage: JsonElement = JsonNull
        fun result(fields: JsonObject) = JsonObject(fields + mapOf("timeZone" to value(timeZone?.id),
            "requestedDepartureTime" to value(requestedDeparture.toString()), "coverage" to coverage,
            "attribution" to value("© OpenStreetMap contributors; transit schedules belong to the listed feed publishers.")))
        val code = body["mode"].text() ?: throw InputError("Choose a transport mode.")
        if (code !in supported) return result(obj("status" to "unsupported", "reason" to "unsupported_mode", "itineraries" to emptyList<Any>(), "message" to "This routing mode is not supported."))
        checkInput(distance(from, to) >= 1, "Choose different departure and destination points.")
        val transit = code in listOf("bus", "train")
        val metadata = try { withTimeoutOrNull(1500) { capabilitySource?.read() } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
        coverage = metadata?.get("coverage") ?: JsonNull
        if (metadata?.get("modes").items().any { it.field("code").text() == code && it.field("status").text() == "unsupported" }) {
            val reason = if (transit) "mode_not_in_dataset" else "unsupported_mode"
            return result(obj("status" to "unsupported", "reason" to reason, "itineraries" to emptyList<Any>(),
                "message" to if (transit) "The loaded transit data does not contain this transport mode. Keep your manual plan."
                    else "The configured router does not support this mode. Keep your manual plan."))
        }
        val modes = if (transit) obj("direct" to emptyList<String>(), "directOnly" to false, "transitOnly" to true,
            "transit" to obj("access" to listOf("WALK"), "egress" to listOf("WALK"), "transfer" to listOf("WALK"),
                "transit" to listOf(obj("mode" to supported.getValue(code)))))
        else obj("direct" to listOf(supported.getValue(code)), "directOnly" to true, "transitOnly" to false)
        requests.incrementAndGet()
        val response = fetch(obj("query" to routeQuery, "variables" to obj(
            "originLat" to from.latitude, "originLon" to from.longitude, "destinationLat" to to.latitude, "destinationLon" to to.longitude,
            "departure" to departure.toString(), "modes" to modes, "filters" to null, "excludeRealtime" to !transit, "walkSpeed" to 1.3333333333),
            "extensions" to obj("realtimeFeedsFromItinerary" to true, "realtimeFeeds" to when (code) {
                "bus" -> listOf("STM"); "train" -> listOf("EXO_TRAINS"); else -> emptyList<String>()
            })))
        if (response["errors"].items().isNotEmpty()) throw ProviderError("Route provider rejected the query.")
        val connection = response.field("data").field("planConnection") as? JsonObject ?: throw ProviderError("Missing route response.")
        val routingErrors = connection["routingErrors"] as? JsonArray ?: throw ProviderError("Missing routing error status.")
        val codes = routingErrors.map { it.field("code").text() }
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
            var itineraryStart: java.time.Instant? = null
            var realTime = false
            var hasSelectedMode = false
            var totalDistance = 0.0
            var geometryCharacters = 0
            var intermediateStopCount = 0
            val legs = rawLegs.map { leg ->
                context.ensureActive()
                val mode = leg.field("mode").text() ?: throw ProviderError("Invalid leg mode.")
                if (mode == supported[code]) hasSelectedMode = true
                if (mode != supported[code] && !(transit && mode == "WALK")) throw ProviderError("Unexpected route mode.")
                val meters = leg.field("distance").number()?.takeIf { it.isFinite() && it >= 0 } ?: throw ProviderError("Invalid leg distance.")
                val seconds = leg.field("duration").number()?.takeIf { it.isFinite() && it >= 0 } ?: throw ProviderError("Invalid leg duration.")
                if (!transit && (meters <= 0 || seconds <= 0)) throw ProviderError("Invalid direct route estimate.")
                val realtime = transit && mode != "WALK" && leg.field("realTime") == JsonPrimitive(true)
                if (realtime && (leg.field("start").field("estimated").field("time").text() == null ||
                    leg.field("end").field("estimated").field("time").text() == null)) throw ProviderError("Incomplete realtime route timing.")
                realTime = realTime || realtime
                val scheduledStart = leg.field("start").field("scheduledTime").text() ?: throw ProviderError("Missing scheduled departure.")
                val scheduledEnd = leg.field("end").field("scheduledTime").text() ?: throw ProviderError("Missing scheduled arrival.")
                if (parseTimestamp(scheduledEnd) < parseTimestamp(scheduledStart)) throw ProviderError("Scheduled route timing is not connected.")
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
                val span = Duration.between(start, end).let { it.seconds.toDouble() + it.nano / 1_000_000_000.0 }
                if (abs(span - seconds) > 1.0) throw ProviderError("Route duration does not match its timestamps.")
                if (itineraryStart == null) itineraryStart = start
                previousArrival = end
                val geometry = leg.field("legGeometry").field("points").text() ?: throw ProviderError("Missing route geometry.")
                geometryCharacters += geometry.length
                if (geometryCharacters > 1_000_000) throw ProviderError("Route geometry is too large.")
                val points = try { decodePolyline(geometry) { context.ensureActive() } } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: Exception) { throw ProviderError("Invalid route geometry.") }
                if (points.size < 2 || points.size.toDouble() != leg.field("legGeometry").field("length").number() ||
                    distance(previous, points.first()) > 250) throw ProviderError("Disconnected route geometry.")
                previous = points.last()
                totalDistance += meters
                if (!totalDistance.isFinite()) throw ProviderError("Invalid route distance.")
                val intermediateStops = when (val reported = leg.field("intermediateStops")) {
                    null, JsonNull -> null
                    is JsonArray -> {
                        if (reported.size > 1000) throw ProviderError("Too many intermediate stops in one leg.")
                        intermediateStopCount += reported.size
                        if (intermediateStopCount > 2000) throw ProviderError("Too many route stops.")
                        reported.map { stop(it) }
                    }
                    else -> throw ProviderError("Invalid intermediate route stops.")
                }
                obj("mode" to mode, "durationSeconds" to seconds, "distanceMeters" to meters, "departureTime" to starts, "arrivalTime" to ends,
                    "geometry" to geometry, "routeName" to (leg.field("route").field("shortName").text() ?: leg.field("route").field("longName").text()),
                    "routeId" to leg.field("route").field("gtfsId").text(), "headsign" to leg.field("headsign").text(),
                    "realtime" to realtime, "timingBasis" to if (mode == "WALK" || !transit) "estimated" else if (realtime) "realtime" else "scheduled",
                    "scheduledDepartureTime" to scheduledStart, "scheduledArrivalTime" to scheduledEnd,
                    "from" to stop(leg.field("from"), points.first()), "to" to stop(leg.field("to"), points.last()),
                    "intermediateStops" to intermediateStops)
            }
            if (!hasSelectedMode || distance(previous, to) > 250) throw ProviderError("Route does not reach the selected destination.")
            val span = Duration.between(itineraryStart, previousArrival).let { it.seconds.toDouble() + it.nano / 1_000_000_000.0 }
            if (abs(span - duration) > 1.0) throw ProviderError("Itinerary duration does not match its timestamps.")
            val transitLegs = if (transit) rawLegs.filter { it.field("mode").text() != "WALK" } else emptyList()
            val transferCount = when (val reported = node["numberOfTransfers"]) {
                null, JsonNull -> (transitLegs.withIndex().count { (index, leg) ->
                    index == 0 || leg.field("interlineWithPreviousLeg") != JsonPrimitive(true)
                } - 1).coerceAtLeast(0)
                else -> reported.number()?.takeIf { it.isFinite() && it % 1.0 == 0.0 && it >= 0 &&
                    it <= (transitLegs.size - 1).coerceAtLeast(0) }?.toInt() ?: throw ProviderError("Invalid route transfer count.")
            }
            val elapsed = Duration.between(departure, previousArrival).let { it.seconds.toDouble() + it.nano / 1_000_000_000.0 }
            obj("id" to "route-${index + 1}", "durationSeconds" to duration, "distanceMeters" to totalDistance,
                "departureTime" to legs.first()["departureTime"], "arrivalTime" to legs.last()["arrivalTime"],
                "transferCount" to transferCount, "initialWaitSeconds" to (elapsed - span).coerceAtLeast(0.0),
                "elapsedFromRequestedDepartureSeconds" to elapsed,
                "timingBasis" to if (!transit) "estimated" else if (realTime) "realtime" else "scheduled", "legs" to legs)
        }
        val reason = if (itineraries.isNotEmpty()) null else when {
            "OUTSIDE_SERVICE_PERIOD" in codes -> "outside_service_period"
            "LOCATION_NOT_FOUND" in codes -> "location_not_found"
            "NO_STOPS_IN_RANGE" in codes -> "no_stops_in_range"
            else -> "no_route"
        }
        return result(obj("status" to if (itineraries.isEmpty()) "no_route" else "success", "reason" to reason, "itineraries" to itineraries,
            "message" to if (itineraries.isEmpty()) "No route is available for these endpoints, mode and service date. Keep your manually entered times." else null))
    }

    /** @param value Provider ISO timestamp. @return Valid instant without exposing malformed provider text. */
    private fun parseTimestamp(value: String) = try { OffsetDateTime.parse(value).toInstant() }
        catch (_: Exception) { throw ProviderError("Invalid route timing.") }

    /**
     * Preserves a real provider place, or its already verified geometry endpoint when old responses omit coordinates.
     * @param reported Provider stop/place fields.
     * @param anchor Verified geometry endpoint; intermediate stops require their own provider coordinates.
     * @return Public location metadata without changing a user-selected point.
     */
    private fun stop(reported: JsonElement?, anchor: Point? = null): JsonObject {
        val latField = reported.field("lat")
        val lonField = reported.field("lon")
        val point = if ((latField == null || latField == JsonNull) && (lonField == null || lonField == JsonNull)) {
            anchor ?: throw ProviderError("Missing route stop coordinates.")
        } else {
            val lat = latField.number()?.takeIf { it.isFinite() && it in -90.0..90.0 }
                ?: throw ProviderError("Invalid route stop coordinates.")
            val lon = lonField.number()?.takeIf { it.isFinite() && it in -180.0..180.0 }
                ?: throw ProviderError("Invalid route stop coordinates.")
            Point(lat, lon)
        }
        if (anchor != null && distance(anchor, point) > 250) throw ProviderError("Route stop does not match its geometry.")
        return obj("name" to reported.field("name").text()?.takeIf { it.isNotBlank() }, "lat" to point.latitude,
            "lon" to point.longitude, "stopId" to (reported.field("stop").field("gtfsId").text() ?: reported.field("gtfsId").text()))
    }

    companion object {
        private val routeQuery = checkNotNull(TravelTrackerPlanner::class.java.getResource("/traveltracker-plan.graphql")).readText()
    }
}
