package transit

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.*
import java.time.Instant

/**
 * Routes to the exact stored destination, independently of random adventure search.
 * Comparison considers direct modes and combined transit, ranking actual arrival time.
 * @param input Validated request with a fixed destination.
 * @param routes Available provider lines.
 * @param zones Fare-zone restrictions.
 * @param route Cancellable OTP transport; geometry is always freshly requested.
 * @param departure Common comparison departure time.
 * @param timeoutMillis Total comparison deadline.
 */
internal suspend fun routeToDestination(
    input: Request,
    routes: List<JsonElement>,
    zones: FareZones,
    route: suspend (JsonObject) -> JsonObject,
    departure: Instant,
    timeoutMillis: Long
): JsonObject {
    val target = requireNotNull(input.destination)
    val end = point(target["point"])
    val alternatives = if (input.compareModes) {
        val transit = input.modes.filter { it !in listOf("walk", "bike") }
        input.modes.map { listOf(it) } + listOf(transit).filter { it.size > 1 }
    } else listOf(input.modes)
    var best: JsonObject? = null
    var bestSeconds = Long.MAX_VALUE
    var failures = 0
    var attempts = 0
    val finished = withTimeoutOrNull(timeoutMillis) {
        for (modes in alternatives) {
            currentCoroutineContext().ensureActive()
            val candidate = input.copy(modes = modes, zones = input.zones.filterKeys { it in modes },
                lines = input.lines.filter { it.substringBefore(':') in modes })
            val allowed = allowedLines(candidate, routes)
            val body = try {
                attempts++
                val seed = route(otpRequest(candidate, end, departure, allowed))
                if (candidate.walkBike) walkBikeResponse(seed, candidate, end, departure, zones) { query ->
                    attempts++
                    route(query)
                } else bikeResponse(seed, candidate)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failures++
                continue
            }
            val plan = body.field("data").field("planConnection") as? JsonObject
            if (body["errors"].items().isNotEmpty() || plan?.get("edges") !is JsonArray) {
                failures++
                continue
            }
            for (edge in plan["edges"].items()) {
                val itinerary = edge.field("node") ?: continue
                val travel = elapsed(itinerary, departure) ?: continue
                val context = currentCoroutineContext()
                if (!accepts(itinerary, candidate, end, zones, allowed) { context.ensureActive() }) continue
                val meters = itinerary.field("legs").items().sumOf { it.field("distance").number() ?: 0.0 }
                val buffer = orientationSeconds(meters)
                val total = travel + buffer
                if (total > input.minutes * 60L || total >= bestSeconds) continue
                bestSeconds = total
                best = obj("status" to "success", "origin" to input.origin, "destination" to target,
                    "elapsedSeconds" to total, "travelTimeSeconds" to travel,
                    "orientationBufferSeconds" to buffer, "departure" to departure.toString(),
                    "otp" to JsonObject(body + ("data" to obj("planConnection" to JsonObject(plan + mapOf(
                        "routingErrors" to JsonArray(emptyList()), "edges" to JsonArray(listOf(edge))))))))
            }
        }
        true
    }
    currentCoroutineContext().ensureActive()
    return best?.let { JsonObject(it + ("attempts" to JsonPrimitive(attempts))) }
        ?: if (finished == null) obj("status" to "timeout", "message" to "Return routing timed out. Retry.")
        else if (failures > 0) throw ProviderError("Return routing is unavailable. Retry.")
        else obj("status" to "no_trip", "message" to "No return route fits these transport choices.")
}
