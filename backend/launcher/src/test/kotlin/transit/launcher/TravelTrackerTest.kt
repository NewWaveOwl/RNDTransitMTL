package transit.launcher

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.util.Base64

/** Proves settings migration, separation from RNDTransit and aggregate-only diagnostics. */
class TravelTrackerTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun config() = ServerConfig(temporary.newFolder().toPath())

    @Test fun `old properties retain routing settings and gain safe address defaults`() {
        val home = temporary.newFolder().toPath()
        Files.writeString(home.resolve("server.properties"), "apiPort=19088\notpMemoryMb=2048\nsupabaseUrl=https://rnd.example\nsupabaseKey=existing-key\n")
        val restored = ConfigStore(home).load()
        assertEquals(19088, restored.apiPort)
        assertEquals(2048, restored.otpMemoryMb)
        assertEquals(defaultNominatimUrl, restored.nominatimUrl)
        assertEquals(defaultNominatimUserAgent, restored.nominatimUserAgent)
        assertEquals("", restored.travelTrackerSupabaseUrl)
        assertEquals("existing-key", restored.supabaseKey)
    }

    @Test fun `TravelTracker config round trips without replacing RNDTransit project or tunnel`() {
        val settings = config().copy(supabaseUrl = "https://rnd.example", supabaseKey = "rnd-key", devTunnelId = "rndtransit.use",
            travelTrackerSupabaseUrl = "https://travel.example", travelTrackerSupabaseKey = "sb_publishable_travel",
            nominatimUrl = "https://addresses.example", nominatimUserAgent = "TravelTrackerDemo/1.0 (+https://example.org/contact)")
        settings.validateTravelTrackerSettings()
        val store = ConfigStore(settings.dataDirectory)
        store.save(settings)
        assertEquals(settings, store.load())
    }

    @Test fun `blank TravelTracker project intentionally reuses existing verified project`() {
        val settings = config().copy(supabaseUrl = "https://rnd.example", supabaseKey = "rnd-key")
        val environment = settings.travelTrackerEnvironment()
        assertEquals("https://rnd.example", environment["TRAVELTRACKER_SUPABASE_URL"])
        assertEquals("rnd-key", environment["TRAVELTRACKER_SUPABASE_KEY"])
        assertEquals(defaultNominatimUrl, environment["NOMINATIM_URL"])
    }

    @Test fun `distinct TravelTracker auth reaches only the TravelTracker environment`() {
        val settings = config().copy(supabaseUrl = "https://rnd.example", supabaseKey = "rnd-key",
            travelTrackerSupabaseUrl = "https://travel.example", travelTrackerSupabaseKey = "sb_publishable_travel")
        assertEquals("https://travel.example", settings.travelTrackerEnvironment()["TRAVELTRACKER_SUPABASE_URL"])
        assertEquals("sb_publishable_travel", settings.travelTrackerEnvironment()["TRAVELTRACKER_SUPABASE_KEY"])
        assertFalse(settings.travelTrackerEnvironment().containsKey("SUPABASE_URL"))
        assertEquals("https://rnd.example", settings.supabaseUrl)
    }

    @Test fun `half configured TravelTracker auth never silently mixes projects`() {
        assertThrows(IllegalArgumentException::class.java) {
            config().copy(travelTrackerSupabaseUrl = "https://travel.example").validateTravelTrackerSettings()
        }
    }

    @Test fun `TravelTracker rejects secret keys before saving or starting a server`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            config().copy(travelTrackerSupabaseUrl = "https://travel.example", travelTrackerSupabaseKey = "sb_secret_sensitive").validateTravelTrackerSettings()
        }
        assertFalse(error.message!!.contains("sensitive"))
    }

    @Test fun `legacy service role keys cannot become TravelTracker public identifiers`() {
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString("""{"role":"service_role"}""".toByteArray())
        assertThrows(IllegalArgumentException::class.java) {
            config().copy(travelTrackerSupabaseUrl = "https://travel.example", travelTrackerSupabaseKey = "header.$payload.signature").validateTravelTrackerSettings()
        }
    }

    @Test fun `legacy anon key remains compatible with TravelTracker authentication`() {
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString("""{"role":"anon"}""".toByteArray())
        config().copy(travelTrackerSupabaseUrl = "https://travel.example", travelTrackerSupabaseKey = "header.$payload.signature").validateTravelTrackerSettings()
    }

    @Test fun `remote address provider must use HTTPS without credentials or query secrets`() {
        assertThrows(IllegalArgumentException::class.java) { config().copy(nominatimUrl = "http://remote.example").validateTravelTrackerSettings() }
        assertThrows(IllegalArgumentException::class.java) { config().copy(nominatimUrl = "https://user:password@remote.example").validateTravelTrackerSettings() }
        assertThrows(IllegalArgumentException::class.java) { config().copy(nominatimUrl = "https://remote.example?token=private").validateTravelTrackerSettings() }
    }

    @Test fun `operator can switch to a local Nominatim instance without rebuilding clients`() {
        val settings = config().copy(nominatimUrl = "http://127.0.0.1:18090")
        settings.validateTravelTrackerSettings()
        assertEquals("http://127.0.0.1:18090", settings.travelTrackerEnvironment()["NOMINATIM_URL"])
    }

    @Test fun `address request identification rejects empty stock or injected headers`() {
        assertThrows(IllegalArgumentException::class.java) { config().copy(nominatimUserAgent = "").validateTravelTrackerSettings() }
        assertThrows(IllegalArgumentException::class.java) { config().copy(nominatimUserAgent = "Java/21.0.6").validateTravelTrackerSettings() }
        assertThrows(IllegalArgumentException::class.java) { config().copy(nominatimUserAgent = "TravelTracker/1.0\r\nAuthorization: secret").validateTravelTrackerSettings() }
    }

    @Test fun `launcher rejects identifiers that the shared backend cannot accept`() {
        assertThrows(IllegalArgumentException::class.java) { config().copy(nominatimUserAgent = "AnotherApplication/1.0").validateTravelTrackerSettings() }
        assertThrows(IllegalArgumentException::class.java) { config().copy(nominatimUserAgent = "TravelTracker/" + "a".repeat(201)).validateTravelTrackerSettings() }
    }

    @Test fun `aggregate counters distinguish cache hits and queued address work from routing`() {
        val status = TravelTrackerStatus.parse(Json.parseToJsonElement("""{"status":"ready","addressProvider":"Public Nominatim","addressRequests":9,"addressCacheHits":4,"addressQueued":2,"routeRequests":3}""").jsonObject)
        assertEquals("Public Nominatim", status.addressProvider)
        assertEquals(9L, status.addressRequests)
        assertEquals(4L, status.addressCacheHits)
        assertEquals(2L, status.addressQueued)
        assertEquals(3L, status.routeRequests)
    }

    @Test fun `missing counters do not masquerade as a healthy zero request service`() {
        assertThrows(IllegalStateException::class.java) {
            TravelTrackerStatus.parse(Json.parseToJsonElement("""{"status":"ready","addressProvider":"Public Nominatim"}""").jsonObject)
        }
    }

    @Test fun `negative counters and unready backend are rejected`() {
        assertThrows(IllegalStateException::class.java) {
            TravelTrackerStatus.parse(Json.parseToJsonElement("""{"status":"ready","addressProvider":"Public Nominatim","addressRequests":-1,"addressCacheHits":0,"addressQueued":0,"routeRequests":0}""").jsonObject)
        }
        assertThrows(IllegalStateException::class.java) {
            TravelTrackerStatus.parse(Json.parseToJsonElement("""{"status":"starting","addressProvider":"Public Nominatim","addressRequests":0,"addressCacheHits":0,"addressQueued":0,"routeRequests":0}""").jsonObject)
        }
    }
}
