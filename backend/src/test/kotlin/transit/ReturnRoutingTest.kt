package transit

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

/** Fixed-target requests must route afresh and rank actual arrival times. */
class ReturnRoutingTest {
    private val away = Point(45.51, -73.6)
    private val home = Point(45.5, -73.6)
    private val now = Instant.parse("2026-11-22T15:00:00Z")
    private fun request(compare: Boolean = true) = parseRequest(obj(
        "origin" to obj("label" to "Current location", "point" to away.toJson()),
        "destination" to obj("label" to "Original start", "point" to home.toJson()),
        "plannedMinutes" to 360, "modes" to listOf("walk", "bike"), "compareModes" to compare))
    private fun zones() = FareZones(obj("type" to "FeatureCollection", "features" to listOf("A","B","C","D").map { zone ->
        obj("properties" to obj("zone" to zone), "geometry" to obj("type" to "Polygon", "coordinates" to
            listOf(listOf(listOf(-74,45),listOf(-73,45),listOf(-73,46),listOf(-74,46),listOf(-74,45)))))
    }))
    private fun encode(points: List<Point>): String {
        var lat = 0; var lon = 0
        fun delta(number: Int): String {
            var value = if (number < 0) (number shl 1).inv() else number shl 1
            return buildString {
                while (value >= 32) { append(((32 or (value and 31)) + 63).toChar()); value = value ushr 5 }
                append((value + 63).toChar())
            }
        }
        return points.joinToString("") {
            val a = kotlin.math.round(it.latitude * 1e5).toInt(); val b = kotlin.math.round(it.longitude * 1e5).toInt()
            val text = delta(a - lat) + delta(b - lon); lat = a; lon = b; text
        }
    }
    private fun response(seconds: Long, bend: Point) = obj("data" to obj("planConnection" to obj(
        "routingErrors" to emptyList<Any>(), "edges" to listOf(obj("node" to obj(
            "duration" to seconds, "waitingTime" to 0, "legs" to listOf(obj(
                "mode" to "WALK", "distance" to 1200, "duration" to seconds,
                "end" to obj("scheduledTime" to now.plusSeconds(seconds).toString()),
                "legGeometry" to obj("length" to 3, "points" to encode(listOf(away, bend, home)))))))))))

    @Test fun fixedTargetUsesFreshQueriesAndSelectsTheFasterMode() = runBlocking {
        val queries = mutableListOf<JsonObject>()
        val result = Generator(emptyList(), zones(), { query ->
            queries += query
            val bike = query.field("variables").field("walkSpeed").number()!! > 2
            response(if (bike) 600 else 900, Point(45.505, if (bike) -73.602 else -73.601))
        }, { now }, sample = { error("Return routing must not sample a random destination") }).generate(request())
        assertEquals(2, queries.size)
        queries.forEach {
            assertEquals(away.latitude, it.field("variables").field("originLat").number()!!, 0.0)
            assertEquals(home.latitude, it.field("variables").field("destinationLat").number()!!, 0.0)
        }
        assertEquals("success", result["status"].text())
        assertEquals(request().destination, result["destination"])
        assertEquals(672.0, result["elapsedSeconds"].number()!!, 0.0)
        val leg = result.field("otp").field("data").field("planConnection").field("edges").items().single()
            .field("node").field("legs").items().single()
        assertEquals("BICYCLE", leg.field("mode").text())
        assertEquals(Point(45.505, -73.602), decodePolyline(leg.field("legGeometry").field("points").text()!!)[1])
    }

    @Test fun noRouteDoesNotSubstituteRandomGeometry() = runBlocking {
        val result = Generator(emptyList(), zones(), { emptyPlanningResponse() }, { now },
            sample = { error("Unexpected random search") }).generate(request())
        assertEquals("no_trip", result["status"].text())
        assertNull(result["otp"])
    }

    @Test fun cancellationEscapesInsteadOfBecomingARetryError() = runBlocking {
        try {
            Generator(emptyList(), zones(), { throw CancellationException("cancel return") }, { now }).generate(request())
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
    }
}
