package transit.launcher

import kotlinx.serialization.json.*

/** Aggregate counters contain no addresses, coordinates, tokens or traveller identifiers. */
internal data class TravelTrackerStatus(
    val addressProvider: String,
    val addressRequests: Long,
    val addressCacheHits: Long,
    val addressQueued: Long,
    val routeRequests: Long,
) {
    companion object {
        /**
         * Rejects malformed counters instead of displaying an unavailable service as healthy.
         * @param body The bounded JSON response from the private shared backend.
         * @return A validated aggregate status, without making an upstream address or routing request.
         */
        fun parse(body: JsonObject): TravelTrackerStatus {
            check(body["status"]?.jsonPrimitive?.content == "ready") { "TravelTracker backend is not ready." }
            val provider = body["addressProvider"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() && it.length <= 100 }
                ?: error("Address provider status is unavailable.")
            fun count(name: String): Long = body[name]?.jsonPrimitive?.longOrNull?.takeIf { it >= 0 }
                ?: error("TravelTracker counters are unavailable.")
            return TravelTrackerStatus(provider, count("addressRequests"), count("addressCacheHits"), count("addressQueued"), count("routeRequests"))
        }
    }
}
