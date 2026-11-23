package transit

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.time.Instant
import kotlin.time.TimeSource

/**
 * Plans an adventure and a fresh route back, sharing one time budget and deadline.
 * Each direction retains its accepted OTP response, including asymmetric streets and transit times.
 * A failed inbound route never becomes a successful one-way proposal.
 */
internal suspend fun generateRoundTrip(
    input: Request, routes: List<JsonElement>, zones: FareZones,
    route: suspend (JsonObject) -> JsonObject, departure: Instant,
    sample: (suspend (Request) -> List<Point>)?, timeoutMillis: Long,
    maxAttempts: Int, dispatcher: CoroutineDispatcher
): JsonObject {
    val timer = TimeSource.Monotonic.markNow()
    val excluded = input.excluded.toMutableList()
    val result = withTimeoutOrNull(timeoutMillis) {
        repeat(3) {
            currentCoroutineContext().ensureActive()
            val remaining = timeoutMillis - timer.elapsedNow().inWholeMilliseconds
            if (remaining <= 1) return@withTimeoutOrNull null
            val outward = Generator(routes, zones, route, { departure }, sample,
                (remaining / 2).coerceAtLeast(1), maxAttempts.coerceAtMost(24),
                preparationDispatcher = dispatcher).generate(input.copy(roundTrip = false,
                    minutes = (input.minutes / 2).coerceAtLeast(1), excluded = excluded.toList()))
            if (outward["status"].text() != "success") return@withTimeoutOrNull outward
            val turnaround = requireNotNull(outward["destination"] as? JsonObject)
            excluded += point(turnaround["point"])
            val outwardSeconds = requireNotNull(outward["elapsedSeconds"].number()).toLong()
            val secondsLeft = input.minutes * 60L - outwardSeconds
            if (secondsLeft <= 0) return@repeat
            val inbound = routeToDestination(input.copy(origin = turnaround, destination = input.origin,
                roundTrip = false, excluded = emptyList(), minutes = ((secondsLeft + 59) / 60).toInt()),
                routes, zones, route, departure.plusSeconds(outwardSeconds),
                (timeoutMillis - timer.elapsedNow().inWholeMilliseconds).coerceAtLeast(1))
            if (inbound["status"].text() != "success") return@repeat
            val total = outwardSeconds + requireNotNull(inbound["elapsedSeconds"].number()).toLong()
            if (total > input.minutes * 60L) return@repeat
            val buffer = requireNotNull(outward["orientationBufferSeconds"].number()).toLong() +
                requireNotNull(inbound["orientationBufferSeconds"].number()).toLong()
            return@withTimeoutOrNull obj("status" to "success", "origin" to input.origin,
                "destination" to input.origin, "roundTrip" to true, "turnaround" to turnaround,
                "elapsedSeconds" to total, "travelTimeSeconds" to total - buffer,
                "orientationBufferSeconds" to buffer, "departure" to departure.toString(),
                "otp" to outward["otp"], "returnOtp" to inbound["otp"])
        }
        obj("status" to "no_trip", "message" to "No round trip fits this time and transport selection. Try more time or different modes.")
    }
    currentCoroutineContext().ensureActive()
    return result ?: obj("status" to "timeout", "message" to "Round-trip search timed out. Retry or allow more time.")
}
