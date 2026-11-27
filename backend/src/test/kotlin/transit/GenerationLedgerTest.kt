package transit

import java.nio.file.Files
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

/** Deterministic policy checks independent of Supabase, OTP and the machine's timezone. */
class GenerationLedgerTest {
    @Test fun invalidStoredQuotaFailsClosed() {
        val file = Files.createTempFile("invalid-quota", ".json")
        try {
            for (text in listOf("invalid json", """{"guest:a":{"day":"2026-11-27","count":-1}}""")) {
                Files.writeString(file, text)
                try {
                    GenerationLedger(file)
                    fail("Corrupt quota must prevent serving new requests")
                } catch (_: IllegalArgumentException) { }
            }
        } finally { Files.delete(file) }
    }
    private val origin = obj("label" to "Origin", "point" to Point(45.5, -73.6).toJson())
    private val result = obj("status" to "success", "destination" to
        obj("label" to "Destination", "point" to Point(45.51, -73.6).toJson()))
    private fun request() = parseRequest(obj("origin" to origin, "plannedMinutes" to 30, "modes" to listOf("walk")))

    @Test fun fiveSuccessesPersistAndResetAtMontrealMidnight() {
        val folder = Files.createTempDirectory("generation-ledger")
        val path = folder.resolve("quota.json")
        var now = Instant.parse("2026-11-28T04:59:00Z") // Nov 27, 23:59 Montréal.
        try {
            val ledger = GenerationLedger(path) { now }
            repeat(5) {
                assertNull(ledger.retryAfter("guest:a"))
                ledger.accepted("guest:a", result)
            }
            assertEquals(60L, ledger.retryAfter("guest:a"))
            val restarted = GenerationLedger(path) { now }
            assertEquals(60L, restarted.retryAfter("guest:a"))
            assertNull(restarted.retryAfter("guest:b"))
            assertNull(restarted.retryAfter("signed-in"))
            now = Instant.parse("2026-11-28T05:00:00Z")
            assertNull(restarted.retryAfter("guest:a"))
            restarted.accepted("guest:a", result)
            assertNull(restarted.retryAfter("guest:a"))
        } finally { Files.deleteIfExists(path); Files.delete(folder) }
    }

    @Test fun localDaysRespectBothDaylightSavingTransitions() {
        for ((start, seconds) in listOf("2026-03-08T05:00:00Z" to 23 * 3600L,
            "2026-11-01T04:00:00Z" to 25 * 3600L)) {
            val ledger = GenerationLedger(now = { Instant.parse(start) })
            repeat(5) { ledger.accepted("guest:a", result) }
            assertEquals(seconds, ledger.retryAfter("guest:a"))
        }
    }

    @Test fun failuresAreFreeAndDestinationTracksAreIndependent() {
        val ledger = GenerationLedger()
        repeat(8) { ledger.accepted("guest:a", obj("status" to "no_trip")) }
        assertNull(ledger.retryAfter("guest:a"))
        ledger.accepted("a", result)
        assertEquals(listOf(Point(45.51, -73.6)), ledger.request("a", request()).excluded)
        assertTrue(ledger.request("b", request()).excluded.isEmpty())
        val fixed = request().copy(destination = result.field("destination") as kotlinx.serialization.json.JsonObject)
        assertEquals(fixed, ledger.request("a", fixed))
    }
}
