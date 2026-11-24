package transit

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.time.Instant

/** Both directions share the full trip budget and one search deadline. */
internal suspend fun generateRoundTrip(
    input: Request, routes: List<JsonElement>, zones: FareZones,
    route: suspend (JsonObject) -> JsonObject, departure: Instant,
    sample: (suspend (Request) -> List<Point>)?, timeoutMillis: Long,
    maxAttempts: Int, dispatcher: CoroutineDispatcher,
    requestId: String, diagnostic: (String) -> Unit
): JsonObject {
    val search = LoopSearch(input, routes, zones, route, departure, timeoutMillis, requestId, diagnostic)
    if (input.interestZoneCount > 1) return generateExplorationLoop(input, departure, sample, search)
    return search.run {
        val turnaroundSample = sample ?: { request: Request ->
            val nearby = request.copy(minutes = (input.minutes * 0.5 * scale()).toInt().coerceAtLeast(1))
            destinationCandidates(nearby, routes, zones, allowedLines(nearby, routes),
                dispatcher = dispatcher, random = random)
        }
        val outward = Generator(routes, zones, route, { departure }, turnaroundSample,
            (remaining() / 3).coerceIn(1, 4_000), maxAttempts.coerceAtMost(24),
            diagnostic = {}, preparationDispatcher = dispatcher).generate(input.copy(roundTrip = false))
        if (outward["status"].text() != "success") return@run
        val turnaround = requireNotNull(outward["destination"] as? JsonObject)
        val elapsed = outward["elapsedSeconds"].number()!!.toLong()
        returnHome(turnaround, elapsed, listOf(outward)) { inbound ->
            val total = elapsed + inbound["elapsedSeconds"].number()!!.toLong()
            val buffer = outward["orientationBufferSeconds"].number()!!.toLong() +
                inbound["orientationBufferSeconds"].number()!!.toLong()
            obj("status" to "success", "origin" to input.origin, "destination" to input.origin,
                "roundTrip" to true, "turnaround" to turnaround, "elapsedSeconds" to total,
                "travelTimeSeconds" to total - buffer, "orientationBufferSeconds" to buffer,
                "departure" to departure.toString(), "otp" to outward["otp"], "returnOtp" to inbound["otp"])
        }
    }
}
