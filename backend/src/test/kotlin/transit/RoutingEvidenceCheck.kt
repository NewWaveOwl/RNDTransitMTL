package transit

import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*

/**
 * Replays opt-in real-router artifacts without adding machine-specific fixtures or skipped tests to normal CI.
 * @param args One explicitly selected evidence directory, produced by Test-TravelTrackerRouting.ps1.
 */
fun main(args: Array<String>) = runBlocking {
    require(args.size == 1) { "Choose one routing evidence directory." }
    val directory = Path.of(args.single()).toAbsolutePath().normalize()
    fun document(name: String) = json.parseToJsonElement(Files.readString(directory.resolve(name))).jsonObject
    val report = document("mode-verification.json")
    val metadata = document("router-metadata.json")
    val info = document("server-info.json")
    val source = OtpCapabilities({ metadata }, { info })
    val mapping = mapOf("WALK" to "pedestrian", "BICYCLE" to "bike", "CAR" to "car", "BUS" to "bus", "RAIL" to "train")
    var verified = 0
    for (sample in report["samples"].items()) {
        val mode = sample.field("mode").text() ?: error("Evidence sample has no mode.")
        val code = mapping[mode] ?: error("Unsupported evidence mode.")
        val request = obj("origin" to sample.field("origin"), "destination" to sample.field("destination"),
            "mode" to code, "departureTime" to sample.field("departureTime"))
        val status = sample.field("status").text()
        val recorded = if (status == "unsupported") null else document("$mode-request.json")
        val response = if (status == "unsupported") null else document("$mode-response.json")
        val planner = TravelTrackerPlanner(source) { planned ->
            check(recorded != null && response != null) { "An unsupported mode unexpectedly contacted the router." }
            fun normalized(value: String?) = value?.replace(Regex("\\s+"), " ")?.trim()
            check(normalized(planned["query"].text()) == normalized(recorded["query"].text())) {
                "Recorded query does not match the current TravelTracker query; capture new evidence."
            }
            for (key in listOf("originLat", "originLon", "destinationLat", "destinationLon", "departure", "modes", "filters", "walkSpeed")) {
                check(planned["variables"].field(key) == recorded["variables"].field(key)) { "Recorded chosen route differs at $key." }
            }
            // The recorded run explicitly excludes realtime updates; this check proves scheduled normalization, not live predictions.
            check(recorded["variables"].field("excludeRealtime") == JsonPrimitive(true))
            response
        }
        val result = planner.plan(request)
        when (status) {
            "unsupported" -> check(result["status"].text() == "unsupported") { "Unsupported dataset mode was not preserved." }
            "no_route" -> check(result["status"].text() == "no_route") { "Recorded no-route status changed." }
            "verified_sample" -> {
                check(result["status"].text() == "success") { "Verified sample could not be normalized." }
                val candidates = result["itineraries"].items()
                check(candidates.size == response.field("data").field("planConnection").field("edges").items().take(4).size)
                for (candidate in candidates) {
                    val legs = candidate.field("legs").items()
                    check(legs.any { it.field("mode").text() == mode })
                    check(candidate.field("transferCount").number() != null)
                    check(candidate.field("elapsedFromRequestedDepartureSeconds").number()!! >= candidate.field("durationSeconds").number()!! - 1.0)
                    for (leg in legs) {
                        check(leg.field("from").field("lat").number() != null && leg.field("to").field("lon").number() != null)
                        if (leg.field("mode").text() in listOf("BUS", "RAIL")) {
                            check(!leg.field("from").field("name").text().isNullOrBlank() && !leg.field("to").field("name").text().isNullOrBlank())
                            check(!leg.field("from").field("stopId").text().isNullOrBlank() && !leg.field("to").field("stopId").text().isNullOrBlank())
                            check(leg.field("timingBasis").text() == "scheduled")
                        }
                    }
                }
                verified++
                println(mode + ": " + candidates.size + " normalized real candidates, first " + candidates.first().field("durationSeconds").number() + " seconds.")
            }
            else -> error("Unknown evidence status.")
        }
    }
    check(verified > 0) { "No successful real route sample was verified." }
    println("$verified modes normalized from recorded OTP evidence; no network, public geocoder or target-server operation performed.")
}
