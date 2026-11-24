package transit

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.time.Instant
import kotlin.math.*

/** Random exploration zones; all visible segments and the invisible return detour are routed. */
internal suspend fun generateExplorationLoop(
    input: Request, departure: Instant, sample: (suspend (Request) -> List<Point>)?, search: LoopSearch
): JsonObject = search.run {
    val count = input.interestZoneCount
    val bearing = random.nextDouble(2 * PI)
    val speed = when { input.modes.any { it in listOf("bus", "metro", "train") } -> 5.0
        "bike" in input.modes -> input.bikeSpeedKmh / 3.6; else -> input.speed }
    val radius = input.minutes * 60 * speed * 0.55 /
        (2 * (count + 1) * sin(PI / (count + 1))) * scale()
    val center = destination(input.start, radius, bearing)
    val points = sample?.invoke(input) ?: (1..count).map { index ->
        destination(center, radius, bearing + PI + 2 * PI * index / (count + 1))
    }
    if (points.size != count || points.distinct().size != count || points.any { p ->
        !inServiceArea(p) || distance(input.start, p) < 30 || input.excluded.any { distance(it, p) < 30 }
    }) return@run
    val stops = points.mapIndexed { index, p -> obj("label" to "Exploration zone ${index + 1}", "point" to p.toJson()) }
    val segments = mutableListOf<JsonObject>()
    val outward = mutableListOf<JsonObject>()
    var elapsed = 0L
    var buffer = 0L
    var start = input.origin
    for (target in stops) {
        val leg = leg(start, target, elapsed) ?: return@run
        elapsed += leg["elapsedSeconds"].number()!!.toLong()
        buffer += leg["orientationBufferSeconds"].number()!!.toLong()
        segments += obj("origin" to start, "destination" to target, "otp" to leg["otp"])
        outward += leg
        start = target
    }
    returnHome(start, elapsed, outward) { inbound ->
        val total = elapsed + inbound["elapsedSeconds"].number()!!.toLong()
        val reserve = buffer + inbound["orientationBufferSeconds"].number()!!.toLong()
        obj("status" to "success", "origin" to input.origin, "destination" to input.origin,
            "roundTrip" to true, "turnaround" to stops.first(), "waypoints" to stops,
            "segments" to segments + obj("origin" to start, "destination" to input.origin, "otp" to inbound["otp"]),
            "elapsedSeconds" to total, "travelTimeSeconds" to total - reserve,
            "orientationBufferSeconds" to reserve, "departure" to departure.toString())
    }
}
