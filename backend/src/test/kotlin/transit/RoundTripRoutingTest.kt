package transit

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

/** Both directions must be freshly routed, budgeted and returned together. */
class RoundTripRoutingTest {
    private val home = Point(45.5, -73.6)
    private val away = Point(45.505, -73.6)
    private val now = Instant.parse("2026-11-22T15:00:00Z")
    private val input = parseRequest(obj("origin" to obj("label" to "Original pin", "point" to home.toJson()),
        "plannedMinutes" to 30, "modes" to listOf("walk"), "roundTrip" to true))
    private fun zones() = FareZones(obj("type" to "FeatureCollection", "features" to listOf("A", "B", "C", "D").map {
        obj("properties" to obj("zone" to it), "geometry" to obj("type" to "Polygon", "coordinates" to
            listOf(listOf(listOf(-74, 45), listOf(-73, 45), listOf(-73, 46), listOf(-74, 46), listOf(-74, 45)))))
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
    private fun response(query: JsonObject, seconds: Long): JsonObject {
        val vars = query.field("variables")
        val start = Point(vars.field("originLat").number()!!, vars.field("originLon").number()!!)
        val end = Point(vars.field("destinationLat").number()!!, vars.field("destinationLon").number()!!)
        val departure = Instant.parse(vars.field("departure").text())
        val bend = Point(45.5025, if (start == home) -73.601 else -73.602)
        return obj("data" to obj("planConnection" to obj("routingErrors" to emptyList<Any>(),
            "edges" to listOf(obj("node" to obj("duration" to seconds, "waitingTime" to 0,
                "legs" to listOf(obj("mode" to "WALK", "distance" to 700, "duration" to seconds,
                    "end" to obj("scheduledTime" to departure.plusSeconds(seconds).toString()),
                    "legGeometry" to obj("length" to 3, "points" to encode(listOf(start, bend, end)))))))))))
    }

    @Test fun independentlyRoutesBackAtTheCorrectDepartureAndKeepsBothGeometries() = runBlocking {
        val queries = mutableListOf<JsonObject>()
        val result = Generator(emptyList(), zones(), { query -> queries += query; response(query, 600) },
            { now }, sample = { listOf(away) }, maxAttempts = 1).generate(input)
        assertEquals("success", result["status"].text())
        assertEquals(2, queries.size)
        assertEquals(input.origin, result["origin"])
        assertEquals(input.origin, result["destination"])
        assertEquals(away, point(result.field("turnaround").field("point")))
        assertEquals(1284.0, result["elapsedSeconds"].number()!!, 0.0)
        val back = queries.last().field("variables")
        assertEquals(away.latitude, back.field("originLat").number()!!, 0.0)
        assertEquals(home.latitude, back.field("destinationLat").number()!!, 0.0)
        assertEquals(now.plusSeconds(642).toString(), back.field("departure").text())
        fun shape(key: String) = result.field(key).field("data").field("planConnection").field("edges").items()
            .single().field("node").field("legs").items().single().field("legGeometry").field("points").text()!!
        assertNotEquals(decodePolyline(shape("otp")).reversed(), decodePolyline(shape("returnOtp")))
    }

    @Test fun outboundCanUseMoreThanHalfOfTheCombinedBudget() = runBlocking {
        val queries = mutableListOf<JsonObject>()
        val result = Generator(emptyList(), zones(), { query ->
            queries += query
            response(query, if (query.field("variables").field("originLat").number() == home.latitude) 1140 else 480)
        }, { now }, sample = { listOf(away) }, maxAttempts = 1).generate(input)
        assertEquals("success", result["status"].text())
        assertEquals(2, queries.size)
        assertEquals(1704.0, result["elapsedSeconds"].number()!!, 0.0)
        assertEquals(now.plusSeconds(1182).toString(), queries.last().field("variables").field("departure").text())
        assertNotNull(result["otp"])
        assertNotNull(result["returnOtp"])
    }
    @Test fun unavailableOrOverBudgetInboundNeverProducesAOneWaySuccess() = runBlocking {
        for (overBudget in listOf(false, true)) {
            val result = Generator(emptyList(), zones(), { query ->
                if (query.field("variables").field("originLat").number() == home.latitude) response(query, 600)
                else if (overBudget) response(query, 1800) else emptyPlanningResponse()
            }, { now }, sample = { listOf(away) }, maxAttempts = 1).generate(input)
            assertNotEquals("success", result["status"].text())
            assertNull(result["returnOtp"])
        }
    }

    @Test fun cancellationAndInvalidTypeDoNotBecomePartialTrips() = runBlocking {
        try {
            Generator(emptyList(), zones(), { throw CancellationException("cancel loop") }, { now },
                sample = { listOf(away) }).generate(input)
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
        try { parseRequest(JsonObject(obj("origin" to input.origin, "plannedMinutes" to 30,
            "modes" to listOf("walk"), "roundTrip" to "yes"))); fail("Invalid trip type") }
        catch (_: InputError) { }
    }
}
