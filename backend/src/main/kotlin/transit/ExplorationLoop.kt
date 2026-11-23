package transit

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.time.Instant
import kotlin.math.*
import kotlin.random.Random
import kotlin.time.TimeSource

/** Temporary exploration zones, not a catalogue of verified attractions. All segments are routed. */
internal suspend fun generateExplorationLoop(
    input: Request, routes: List<JsonElement>, zones: FareZones,
    route: suspend (JsonObject) -> JsonObject, departure: Instant,
    sample: (suspend (Request) -> List<Point>)?, timeoutMillis: Long
): JsonObject {
    val timer = TimeSource.Monotonic.markNow()
    val count = input.interestZoneCount
    val result = withTimeoutOrNull(timeoutMillis) {
        val bearing = Random.nextDouble(2 * PI)
        // A regular polygon with the starting point as one vertex forms a loop, not a radial out-and-back.
        val speed = when { input.modes.any { it in listOf("bus", "metro", "train") } -> 5.0
            "bike" in input.modes -> input.bikeSpeedKmh / 3.6; else -> input.speed }
        val initialRadius = input.minutes * 60 * speed * 0.55 /
            (2 * (count + 1) * sin(PI / (count + 1)))
        repeat(3) { attempt ->
            currentCoroutineContext().ensureActive()
            val radius = initialRadius * 0.65.pow(attempt)
            val center = destination(input.start, radius, bearing)
            val points = sample?.invoke(input) ?: (1..count).map { index ->
                destination(center, radius, bearing + PI + 2 * PI * index / (count + 1))
            }
            if (points.size != count || points.distinct().size != count || points.any { p ->
                !inServiceArea(p) || distance(input.start, p) < 30 ||
                    input.excluded.any { distance(it, p) < 30 }
            }) return@repeat
            val stops = points.mapIndexed { index, p ->
                obj("label" to "Exploration zone ${index + 1}", "point" to p.toJson())
            }
            val segments = mutableListOf<JsonObject>()
            var elapsed = 0L
            var buffer = 0L
            var start = input.origin
            for (target in stops + input.origin) {
                val left = input.minutes * 60L - elapsed
                if (left <= 0) return@repeat
                val remaining = timeoutMillis - timer.elapsedNow().inWholeMilliseconds
                if (remaining <= 0) return@withTimeoutOrNull null
                val leg = routeToDestination(input.copy(origin = start, destination = target,
                    roundTrip = false, interestZoneCount = 1, compareModes = false,
                    excluded = emptyList(), minutes = ((left + 59) / 60).toInt()),
                    routes, zones, route, departure.plusSeconds(elapsed), remaining)
                if (leg["status"].text() != "success") return@repeat
                elapsed += requireNotNull(leg["elapsedSeconds"].number()).toLong()
                buffer += requireNotNull(leg["orientationBufferSeconds"].number()).toLong()
                if (elapsed > input.minutes * 60L) return@repeat
                segments += obj("origin" to start, "destination" to target, "otp" to leg["otp"])
                start = target
            }
            return@withTimeoutOrNull obj("status" to "success", "origin" to input.origin,
                "destination" to input.origin, "roundTrip" to true, "turnaround" to stops.first(),
                "waypoints" to stops, "segments" to segments, "elapsedSeconds" to elapsed,
                "travelTimeSeconds" to elapsed - buffer, "orientationBufferSeconds" to buffer,
                "departure" to departure.toString())
        }
        obj("status" to "no_trip", "message" to "No exploration loop fits these choices. Try more time or fewer restrictions.")
    }
    currentCoroutineContext().ensureActive()
    return result ?: obj("status" to "timeout", "message" to "Loop search timed out. Retry or allow more time.")
}
