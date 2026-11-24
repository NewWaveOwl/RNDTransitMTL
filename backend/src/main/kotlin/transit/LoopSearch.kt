package transit

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.time.Instant
import kotlin.math.*
import kotlin.random.Random
import kotlin.time.TimeSource

/** One deadline and random stream for the entire loop, including optional return detours. */
internal class LoopSearch(
    val input: Request, private val routes: List<JsonElement>, private val zones: FareZones,
    private val route: suspend (JsonObject) -> JsonObject, private val departure: Instant,
    private val timeoutMillis: Long, private val requestId: String, private val diagnostic: (String) -> Unit
) {
    private val timer = TimeSource.Monotonic.markNow()
    val seed = input.generationSeed ?: Random.nextInt()
    val random = Random(seed)
    var attempts = 0
    private var best: JsonObject? = null
    private var bestScore = Double.NEGATIVE_INFINITY
    private var completedCandidate = false
    private var providerFailure: ProviderError? = null
    fun remaining() = (timeoutMillis - timer.elapsedNow().inWholeMilliseconds).coerceAtLeast(1)
    fun legTimeout() = remaining().coerceAtMost(4_000)

    suspend fun run(candidate: suspend LoopSearch.() -> Unit): JsonObject {
        diagnostic("request=$requestId search=loop-start seed=$seed budgetMs=$timeoutMillis")
        withTimeoutOrNull(timeoutMillis) {
            while (isActive) {
                attempts++
                try { candidate(); completedCandidate = true }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: ProviderError) { providerFailure = failure }
                // Cooperate even when geometry rejects a candidate without making an HTTP call.
                delay(10)
            }
        }
        currentCoroutineContext().ensureActive()
        diagnostic("request=$requestId search=loop-end seed=$seed attempts=$attempts status=${if (best != null) "success" else "no_trip"}")
        if (best == null && providerFailure != null) throw providerFailure!!
        return JsonObject((best ?: obj("status" to if (completedCandidate) "no_trip" else "timeout",
            "message" to "No loop found within the search time. Retry or adjust your choices.")) +
            mapOf("generationSeed" to JsonPrimitive(seed), "attempts" to JsonPrimitive(attempts)))
    }

    /** Retry different bearings and sizes; avoid shrinking below useful exploration distances. */
    fun scale(): Double = 0.65.pow(((attempts - 1) % 5).toDouble())

    suspend fun leg(start: JsonObject, target: JsonObject, elapsed: Long): JsonObject? {
        val left = input.minutes * 60L - elapsed
        if (left <= 0) return null
        val result = routeToDestination(input.copy(origin = start, destination = target,
            roundTrip = false, interestZoneCount = 1, compareModes = false, excluded = emptyList(),
            minutes = ((left + 59) / 60).toInt()), routes, zones, route,
            departure.plusSeconds(elapsed), legTimeout())
        return result.takeIf { it["status"].text() == "success" &&
            elapsed + it["elapsedSeconds"].number()!! <= input.minutes * 60L }
    }

    /** Save a direct fallback first, then try both sides with progressively smaller offsets. */
    suspend fun returnHome(start: JsonObject, elapsed: Long, outward: List<JsonObject>,
        accept: (JsonObject) -> JsonObject) {
        val direct = leg(start, input.origin, elapsed)
        direct?.let { retain(accept(it), outward, it) }
        val from = point(start["point"])
        val length = distance(from, input.start)
        if (length < 60) return
        val firstSide = if (random.nextBoolean()) 1 else -1
        for (fraction in listOf(0.18, 0.09)) for (side in listOf(firstSide, -firstSide)) {
            currentCoroutineContext().ensureActive()
            val p = returnOffset(from, input.start, length * fraction * side)
            if (!inServiceArea(p)) continue
            val hidden = obj("label" to "Return routing point", "point" to p.toJson())
            val first = leg(start, hidden, elapsed) ?: continue
            val second = leg(hidden, input.origin, elapsed + first["elapsedSeconds"].number()!!.toLong()) ?: continue
            val combined = combineReturn(first, second, start, input.origin)
            retain(accept(combined), outward, combined)
        }
    }

    private suspend fun retain(result: JsonObject, outward: List<JsonObject>, inbound: JsonObject) {
        // Retain the first complete loop even if its overlap scoring reaches the deadline.
        if (best == null) best = result
        val ratio = result["elapsedSeconds"].number()!! / (input.minutes * 60.0)
        val overlap = returnOverlap(outward, inbound)
        // Prefer useful journey length and less retracing, while keeping every valid fallback.
        val score = ratio * 0.65 + (1 - overlap) * 0.35
        if (score > bestScore) { best = result; bestScore = score }
    }
}

/** Local equirectangular projection: midpoint plus a perpendicular offset, all distances in metres. */
internal fun returnOffset(from: Point, home: Point, offset: Double): Point {
    val lat = (from.latitude + home.latitude) / 2
    val lon = (from.longitude + home.longitude) / 2
    val metresPerDegree = PI * 6_371_008.8 / 180
    val x = (home.longitude - from.longitude) * metresPerDegree * cos(Math.toRadians(lat))
    val y = (home.latitude - from.latitude) * metresPerDegree
    val length = hypot(x, y)
    require(length > 0)
    return Point(lat + x / length * offset / metresPerDegree,
        lon - y / length * offset / (metresPerDegree * cos(Math.toRadians(lat))))
}

/** Keep provider legs/times intact; the intermediate point is not an exploration stop or flag. */
private fun combineReturn(first: JsonObject, second: JsonObject, start: JsonObject, home: JsonObject): JsonObject {
    fun node(leg: JsonObject) = leg["otp"].field("data").field("planConnection").field("edges").items().single().field("node")!!.jsonObject
    val a = node(first); val b = node(second)
    val total = first["elapsedSeconds"].number()!!.toLong() + second["elapsedSeconds"].number()!!.toLong()
    val buffer = first["orientationBufferSeconds"].number()!!.toLong() + second["orientationBufferSeconds"].number()!!.toLong()
    val combined = JsonObject(b + mapOf("legs" to JsonArray(a["legs"].items() + b["legs"].items()),
        "duration" to JsonPrimitive(total - buffer),
        "waitingTime" to JsonPrimitive((a["waitingTime"].number() ?: 0.0).toLong() + (b["waitingTime"].number() ?: 0.0).toLong())))
    return obj("status" to "success", "origin" to start, "destination" to home,
        "elapsedSeconds" to total, "orientationBufferSeconds" to buffer, "travelTimeSeconds" to total - buffer,
        "otp" to JsonObject(second["otp"]!!.jsonObject + mapOf(
            "extensions" to JsonObject(first["otp"].field("extensions").let { (it as? JsonObject).orEmpty() } +
                second["otp"].field("extensions").let { (it as? JsonObject).orEmpty() }),
            "data" to obj("planConnection" to obj("routingErrors" to emptyList<Any>(),
                "edges" to listOf(obj("node" to combined)))))))
}

private fun routeLines(leg: JsonObject): List<List<Point>> = leg["otp"].field("data").field("planConnection")
    .field("edges").items().single().field("node").field("legs").items().map {
        decodePolyline(it.field("legGeometry").field("points").text()!!)
    }

/** Length-weighted return samples within 30m of outbound geometry count as retracing. */
internal suspend fun returnOverlap(outward: List<JsonObject>, inbound: JsonObject): Double {
    val context = currentCoroutineContext()
    val lines = outward.flatMap(::routeLines).flatMap { it.zipWithNext() }
    var total = 0.0; var shared = 0.0
    for (line in routeLines(inbound)) for ((a, b) in line.zipWithNext()) {
        val length = distance(a, b)
        val samples = ceil(length / 25).toInt().coerceIn(1, 400)
        for (i in 0 until samples) {
            context.ensureActive()
            if (i % 64 == 0) yield()
            val t = (i + 0.5) / samples
            val p = Point(a.latitude + (b.latitude - a.latitude) * t, a.longitude + (b.longitude - a.longitude) * t)
            total += length / samples
            if (lines.any { (c, d) -> pointSegmentDistance(p, c, d) < 30 }) shared += length / samples
        }
    }
    return if (total == 0.0) 1.0 else shared / total
}

private fun pointSegmentDistance(p: Point, a: Point, b: Point): Double {
    val factor = cos(Math.toRadians(p.latitude))
    val ax = (a.longitude - p.longitude) * factor; val ay = a.latitude - p.latitude
    val bx = (b.longitude - p.longitude) * factor; val by = b.latitude - p.latitude
    val dx = bx - ax; val dy = by - ay
    val square = dx * dx + dy * dy
    val t = if (square == 0.0) 0.0 else (-(ax * dx + ay * dy) / square).coerceIn(0.0, 1.0)
    return hypot(ax + t * dx, ay + t * dy) * PI * 6_371_008.8 / 180
}
