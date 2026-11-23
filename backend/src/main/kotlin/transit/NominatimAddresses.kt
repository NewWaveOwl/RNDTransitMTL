package transit

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** One process-wide, bounded queue and cache for explicitly requested address lookups.
 * @param fetch Performs one identified request without session tokens or automatic retries.
 * @param clock Monotonic milliseconds, injectable for deterministic rate-limit tests.
 * @param pause Suspends a queued caller until the shared provider slot is available.
 */
internal class NominatimAddresses(
    private val fetch: suspend (String, Map<String, String>) -> Response,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {
    data class Response(val status: Int, val body: String, val retryAfter: String? = null)
    private data class Cached(val until: Long, val value: JsonObject)
    private val cache = LinkedHashMap<String, Cached>(512, 0.75f, true)
    private val gate = Mutex()
    private val admitted = AtomicInteger()
    private var nextStart = Long.MIN_VALUE
    private var unavailableUntil = Long.MIN_VALUE
    val requests = AtomicLong()
    val cacheHits = AtomicLong()
    val queued: Int get() = admitted.get()

    /**
     * Searches only complete queries submitted by a user, never autocomplete or background scans.
     * @param query Public address or place query from the explicit Search action.
     * @return Address candidates without saving any catalogue or trip data.
     */
    suspend fun search(query: String): Pair<Int, JsonObject> {
        checkInput(query.length <= 200 && query.none(Char::isISOControl), "Enter an address of 3 to 200 characters.")
        val text = query.trim().replace(Regex("\\s+"), " ")
        checkInput(text.length in 3..200, "Enter an address of 3 to 200 characters.")
        return lookup("search:${text.lowercase(Locale.ROOT)}", "search", mapOf("q" to text, "limit" to "5"))
    }

    /**
     * Finds an editable address suggestion without changing the chosen point.
     * @param lat User-selected latitude.
     * @param lon User-selected longitude.
     * @return A candidate, or an explicit no-match/unavailable result.
     */
    suspend fun reverse(lat: Double, lon: Double): Pair<Int, JsonObject> {
        Point(lat, lon)
        return lookup("reverse:$lat:$lon", "reverse", mapOf("lat" to lat.toString(), "lon" to lon.toString(), "zoom" to "18"))
    }

    private fun cached(key: String): JsonObject? = synchronized(cache) {
        cache[key]?.takeIf { it.until > clock() }?.value?.also { cacheHits.incrementAndGet() }
    }

    private suspend fun lookup(key: String, path: String, parameters: Map<String, String>): Pair<Int, JsonObject> {
        cached(key)?.let { return 200 to it }
        if (admitted.incrementAndGet() > 4) {
            admitted.decrementAndGet()
            return busy("Address lookup is busy. Try again shortly.", 1)
        }
        try {
            return gate.withLock {
                currentCoroutineContext().ensureActive()
                cached(key)?.let { return@withLock 200 to it }
                if (clock() < unavailableUntil) return@withLock busy("Address provider is temporarily unavailable. Enter the address manually.",
                    ((unavailableUntil - clock() + 999) / 1000).coerceAtLeast(1))
                val now = clock()
                val wait = if (nextStart > now) nextStart - now else 0
                if (wait > 0) pause(wait)
                currentCoroutineContext().ensureActive()
                // Set before I/O: failures and cancellation must also consume a provider slot.
                nextStart = clock() + 1000
                requests.incrementAndGet()
                val response = fetch(path, parameters + mapOf("format" to "jsonv2", "addressdetails" to "1", "accept-language" to "en,fr"))
                if (response.status == 429 || response.status == 503) {
                    val retry = retrySeconds(response.retryAfter)
                    unavailableUntil = clock() + retry * 1000
                    return@withLock busy("Address provider is temporarily unavailable. Enter the address manually.", retry)
                }
                if (response.status != 200 && !(path == "reverse" && response.status == 404))
                    throw ProviderError("Address provider unavailable.")
                val raw = if (response.status == 404) JsonNull else json.parseToJsonElement(response.body)
                val records = when {
                    raw == JsonNull -> emptyList()
                    path == "search" && raw is JsonArray -> raw.toList()
                    path == "reverse" && raw is JsonObject && raw["error"] != null -> emptyList()
                    path == "reverse" && raw is JsonObject -> listOf(raw)
                    else -> throw ProviderError("Invalid address response.")
                }
                val candidates = records.take(5).map { record ->
                    val label = record.field("display_name").text()?.trim()?.takeIf { it.isNotBlank() && it.length <= 2000 }
                        ?: throw ProviderError("Invalid address label.")
                    val lat = (record.field("lat") as? JsonPrimitive)?.content?.toDoubleOrNull()
                        ?: throw ProviderError("Invalid address coordinates.")
                    val lon = (record.field("lon") as? JsonPrimitive)?.content?.toDoubleOrNull()
                        ?: throw ProviderError("Invalid address coordinates.")
                    try { Point(lat, lon) } catch (_: InputError) { throw ProviderError("Invalid address coordinates.") }
                    obj("label" to label, "lat" to lat, "lon" to lon, "source" to "OpenStreetMap Nominatim")
                }
                val result = obj("status" to if (candidates.isEmpty()) "no_match" else "success", "candidates" to candidates,
                    "attribution" to "© OpenStreetMap contributors")
                synchronized(cache) {
                    cache[key] = Cached(clock() + if (candidates.isEmpty()) 300_000 else 86_400_000, result)
                    while (cache.size > 512) cache.remove(cache.keys.first())
                }
                200 to result
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        finally { admitted.decrementAndGet() }
    }

    private fun busy(message: String, retry: Long): Pair<Int, JsonObject> = 503 to obj(
        "status" to "unavailable", "message" to message, "retryAfterSeconds" to retry,
        "candidates" to emptyList<Any>(), "attribution" to "© OpenStreetMap contributors")

    private fun retrySeconds(value: String?): Long = value?.toLongOrNull()?.coerceIn(1, 3600)
        ?: runCatching { ((ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() -
            System.currentTimeMillis() + 999) / 1000).coerceIn(1, 3600) }.getOrDefault(60)
}
