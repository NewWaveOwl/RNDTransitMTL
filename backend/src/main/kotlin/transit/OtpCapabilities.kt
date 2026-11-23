package transit

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.time.Instant
import java.time.ZoneId

/**
 * Reads the running router's schema and loaded feeds without claiming a path exists for every endpoint.
 * @param fetch Uses the configured private GTFS GraphQL endpoint.
 * @param serverInfo Uses that router's private Transmodel GraphQL endpoint, when available.
 * @param nanoTime Monotonic clock for a short shared metadata cache.
 */
internal class OtpCapabilities(
    private val fetch: suspend (JsonObject) -> JsonObject,
    private val serverInfo: suspend (JsonObject) -> JsonObject,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    private data class Cached(val at: Long, val body: JsonObject)
    @Volatile private var cached: Cached? = null
    private val gate = Mutex()

    /**
     * Reports current router support and the global union of loaded service dates.
     * @return Public metadata; absent version, bounds and per-feed service dates remain unknown.
     */
    suspend fun read(): JsonObject = gate.withLock {
        cached?.takeIf { nanoTime() - it.at in 0 until 30_000_000_000L }?.let { return@withLock it.body }
        val response = fetch(obj("query" to metadataQuery))
        if (response["errors"].items().isNotEmpty()) throw ProviderError("Router capability metadata is unavailable.")
        val data = response["data"] as? JsonObject ?: throw ProviderError("Missing router capability metadata.")
        val rawFeeds = data["feeds"] as? JsonArray ?: throw ProviderError("Missing loaded feed metadata.")
        val info = try {
            withTimeoutOrNull(1000) { serverInfo(obj("query" to serverInfoQuery)) }
                ?.takeIf { it["errors"].items().isEmpty() }?.field("data")?.field("serverInfo")
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
        val zoneName = info.field("internalTransitModelTimeZone").text()
        val zone = zoneName?.let { runCatching { ZoneId.of(it) }.getOrNull() }
        fun epoch(name: String): Instant? = data["serviceTimeRange"].field(name).number()
            ?.takeIf { it.isFinite() && it % 1.0 == 0.0 }?.toLong()
            ?.let { runCatching { Instant.ofEpochSecond(it) }.getOrNull() }
        val first = epoch("start")
        val endExclusive = epoch("end")
        val validRange = first != null && endExclusive != null && endExclusive > first
        val feeds = rawFeeds.map { feed ->
            val id = feed.field("feedId").text()?.takeIf { it.isNotBlank() && it.length <= 128 }
                ?: throw ProviderError("Invalid loaded feed metadata.")
            val modes = feed.field("agencies").items().flatMap { agency ->
                agency.field("routes").items().mapNotNull { it.field("mode").text() }
            }.distinct().sorted()
            obj("id" to id, "modes" to modes, "version" to feed.field("version").text(),
                "publisher" to feed.field("publisher").field("name").text(),
                "startDate" to null, "endDate" to null)
        }
        if (feeds.map { it["id"] }.distinct().size != feeds.size) throw ProviderError("Duplicate loaded feed metadata.")
        val loadedModesKnown = rawFeeds.all { feed ->
            (feed.field("agencies") as? JsonArray)?.all { agency ->
                (agency.field("routes") as? JsonArray)?.all { it.field("mode").text() != null } == true
            } == true
        }
        val loadedModes = feeds.flatMap { it["modes"].items().mapNotNull { mode -> mode.text() } }.toSet()
        val directValues = data["directModes"].field("enumValues") as? JsonArray
        val directModes = directValues?.takeIf { it.isNotEmpty() && it.all { mode -> mode.field("name").text() != null } }
            ?.mapNotNull { it.field("name").text() }?.toSet()
        val modes = listOf("pedestrian" to "WALK", "bike" to "BICYCLE", "car" to "CAR", "bus" to "BUS", "train" to "RAIL").map { (code, mode) ->
            val transit = code in listOf("bus", "train")
            val known = if (transit) loadedModesKnown else directModes != null
            val supported = if (transit) mode in loadedModes else directModes?.contains(mode) == true
            obj("code" to code, "status" to if (!known) "unknown" else if (supported) "available" else "unsupported",
                "basis" to if (!known) "unknown" else if (transit) "loaded_routes" else "router_schema",
                "reason" to if (!known) "mode_support_unavailable" else if (supported) null else if (transit) "mode_not_in_dataset" else "unsupported_mode")
        }
        val coverage = obj("timeZone" to zone?.id,
            "startDate" to if (validRange && zone != null) first.atZone(zone).toLocalDate().toString() else null,
            "endDate" to if (validRange && zone != null) endExclusive.minusNanos(1).atZone(zone).toLocalDate().toString() else null,
            "serviceStart" to if (validRange) first.toString() else null,
            "serviceEndExclusive" to if (validRange) endExclusive.toString() else null,
            "serviceRangeBasis" to "global_union", "feeds" to feeds, "bounds" to null)
        obj("status" to "success", "router" to obj("name" to "OpenTripPlanner", "version" to info.field("version").text()),
            "coverage" to coverage, "modes" to modes,
            "attribution" to "© OpenStreetMap contributors; transit schedules belong to the listed feed publishers.",
            "notice" to "Mode support and loaded routes do not guarantee a journey for every endpoint or service date. Per-feed service dates and street bounds are unknown.")
            .also { cached = Cached(nanoTime(), it) }
    }

    companion object {
        internal const val metadataQuery = """query RoutingCapabilities {
          directModes:__type(name:"PlanDirectMode") { enumValues { name } }
          serviceTimeRange { start end }
          feeds { feedId version publisher { name } agencies { routes { mode } } }
        }"""
        internal const val serverInfoQuery = """query RoutingServerInfo { serverInfo { version internalTransitModelTimeZone } }"""
    }
}
