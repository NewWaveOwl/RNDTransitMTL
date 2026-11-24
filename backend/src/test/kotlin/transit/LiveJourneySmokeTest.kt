package transit

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in check against the running OTP graph, e.g. through an SSH local port forward.
 * RND_LIVE_OTP_URL must point to /otp/gtfs/v1. No user coordinates or credentials are used.
 */
class LiveJourneySmokeTest {
    @Test fun walkingReturnsAndRepeatedLoopsUseRealProviderGeometry() = runBlocking {
        val endpoint = System.getenv("RND_LIVE_OTP_URL")
        assumeTrue("Set RND_LIVE_OTP_URL to run the live graph check", !endpoint.isNullOrBlank())
        val zones = FareZones(obj("type" to "FeatureCollection", "features" to listOf("A", "B", "C", "D").map {
            obj("properties" to obj("zone" to it), "geometry" to obj("type" to "Polygon", "coordinates" to
                listOf(listOf(listOf(-74,45),listOf(-73,45),listOf(-73,46),listOf(-74,46),listOf(-74,45)))))
        }))
        val home = obj("label" to "Montreal smoke start", "point" to Point(45.5019, -73.5673).toJson())
        val away = obj("label" to "Montreal smoke return", "point" to Point(45.505, -73.57).toJson())
        Upstream().use { upstream ->
            val catalogue = upstream.json(endpoint!!, obj("query" to
                "query SmokeLines { routes { gtfsId mode shortName longName } }"))
            val generator = Generator(catalogue.field("data").field("routes").items(), zones,
                { upstream.json(endpoint, it) })
            for (fastest in listOf(false, true)) {
                val input = parseRequest(obj("origin" to away, "destination" to home, "plannedMinutes" to 360,
                    "modes" to if (fastest) listOf("bus", "metro", "walk", "bike") else listOf("walk"),
                    "zonesByTransport" to if (fastest) mapOf("bus" to listOf("A", "B", "C", "D"),
                        "metro" to listOf("A", "B", "C", "D")) else emptyMap<String, List<String>>(),
                    "compareModes" to fastest))
                val result = generator.generate(input)
                assertEquals(result.toString(), "success", result["status"].text())
                assertEquals(home, result["destination"])
            }
            for (count in listOf(1, 3)) {
                val input = parseRequest(obj("origin" to home, "plannedMinutes" to 30,
                    "modes" to listOf("walk"), "roundTrip" to true, "interestZoneCount" to count,
                    "generationSeed" to 24001))
                val result = generator.generate(input)
                assertEquals(result.toString(), "success", result["status"].text())
                assertEquals(home, result["destination"])
                assertEquals(value(true), result["roundTrip"])
                assertTrue(result["elapsedSeconds"].number()!! <= 1800)
            }
        }
    }
}
