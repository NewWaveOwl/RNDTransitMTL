package transit

import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.ZoneId

/** Successful guest generations survive restarts; recent destinations belong to verified identities.
 * One backend process owns [file]. Missing files start empty; corrupt/unreadable files fail closed.
 * @param file Private, writable quota file, or null for isolated tests.
 * @param now Wall clock used for America/Toronto calendar days (including DST).
 */
internal class GenerationLedger(private val file: Path? = null, private val now: () -> Instant = Instant::now) {
    private val zone = ZoneId.of("America/Toronto")
    private var counts: JsonObject = file?.takeIf(Files::exists)?.let {
        json.parseToJsonElement(Files.readString(it)).jsonObject
    } ?: JsonObject(emptyMap())
    private val destinations = linkedMapOf<String, List<Point>>()

    init {
        require(counts.all { (user, entry) ->
            val count = entry.field("count").number()
            user.startsWith("guest:") && runCatching {
                java.time.LocalDate.parse(entry.field("day").text())
            }.isSuccess && count != null && count in 0.0..5.0 && count % 1 == 0.0
        }) { "Invalid guest generation quota file." }
    }

    /** Seconds until local midnight when five successful proposals have been returned.
     * @param user Verified identity; anonymous identities have a server-assigned guest prefix.
     */
    @Synchronized fun retryAfter(user: String): Long? {
        if (!user.startsWith("guest:")) return null
        val time = now().atZone(zone)
        val entry = counts[user]
        if (entry.field("day").text() != time.toLocalDate().toString() ||
            (entry.field("count").number() ?: 0.0) < 5) return null
        return java.time.Duration.between(time.toInstant(), time.toLocalDate().plusDays(1).atStartOfDay(zone).toInstant())
            .seconds.coerceAtLeast(1)
    }

    /** Adds only this user's accepted destinations to random searches; fixed return destinations stay valid.
     * @param user Verified identity.
     * @param input Validated client preferences.
     */
    @Synchronized fun request(user: String, input: Request): Request =
        if (input.destination != null) input else input.copy(
            excluded = (input.excluded + destinations[user].orEmpty()).distinct().takeLast(20))

    /** Commits quota before returning a successful proposal; errors and no-route results cost nothing.
     * @param user Verified identity admitted by the existing per-user active-request guard.
     * @param result Accepted generation response, never a request body or token.
     */
    @Synchronized fun accepted(user: String, result: JsonObject) {
        if (result.field("status").text() != "success") return
        val destination = result.field("turnaround")?.takeUnless { it == JsonNull } ?: result.field("destination")
        val point = point(destination.field("point"))
        if (user.startsWith("guest:")) {
            val day = now().atZone(zone).toLocalDate().toString()
            val previous = counts[user]
            val count = if (previous.field("day").text() == day) previous.field("count").number()?.toInt() ?: 0 else 0
            val updated = JsonObject(counts.filterValues { it.field("day").text() == day } +
                (user to obj("day" to day, "count" to count + 1)))
            file?.let { target ->
                Files.createDirectories(target.toAbsolutePath().parent)
                val temporary = Files.createTempFile(target.toAbsolutePath().parent, "guest-quota-", ".tmp")
                try {
                    Files.writeString(temporary, updated.toString())
                    Files.move(temporary, target.toAbsolutePath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                } finally { Files.deleteIfExists(temporary) }
            }
            counts = updated
        }
        destinations[user] = (destinations.remove(user).orEmpty() + point).takeLast(10)
        if (destinations.size > 10_000) destinations.remove(destinations.keys.first())
    }
}
