package transit.launcher

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path

class ExoBusesTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun fixture(): Path {
        val dir = temporary.newFolder("combined graph").toPath()
        for (file in listOf("graph.obj", "stm-gtfs.zip", ".rndtransit-data")) Files.writeString(dir.resolve(file), "fixture")
        Files.writeString(dir.resolve("stm-upstream.json"), """{"updaters":[{"type":"stop-time-updater","feedId":"STM","headers":{"apiKey":"stm-fixture-secret"}}]}""")
        Files.writeString(dir.resolve("stm-adapter-index.json"), """{"routes":{},"stops":{},"directions":{}}""")
        val agencies = listOf("CITSO", "CITLA")
        for (agency in agencies) Files.writeString(dir.resolve(BusSector(agency).archive), "schedule $agency")
        Files.writeString(dir.resolve(busCatalogName), buildJsonObject {
            put("agencies", JsonArray(agencies.map(::JsonPrimitive)))
            put("graphSha256", fileSha256(dir.resolve("graph.obj")))
            put("scheduleSha256", buildJsonObject { for (agency in agencies) put(agency, fileSha256(dir.resolve(BusSector(agency).archive))) })
        }.toString())
        return dir
    }
    @Test fun `bus manifest binds unique safe sectors and exact schedules to the prepared graph`() {
        val dir = fixture()
        assertEquals(listOf("EXO_BUS_CITSO", "EXO_BUS_CITLA"), busSectors(dir, true).map { it.feedId })
        assertThrows(IllegalArgumentException::class.java) { BusSector("../CITSO") }
        assertThrows(IllegalArgumentException::class.java) { BusSector("TRAINS") }
        Files.writeString(dir.resolve("graph.obj"), "changed graph")
        assertThrows(IllegalArgumentException::class.java) { busSectors(dir, true) }
    }
    @Test fun `changing a schedule after graph build is rejected before any service starts`() {
        val dir = fixture()
        Files.writeString(dir.resolve(BusSector("CITSO").archive), "new schedules")
        assertThrows(IllegalArgumentException::class.java) { DataSetup().validateData(dir) }
    }
    @Test fun `bus tokens remain per sector and blank edits preserve existing credentials`() {
        val dir = fixture(); val store = BusCredentials(dir)
        store.save(BusCredentialEdit("CITSO", "so-secret", true, 60))
        store.save(BusCredentialEdit("CITLA", "la-secret", true, 90))
        store.save(BusCredentialEdit("CITSO", "", false, 120))
        store.validate()
        val summaries = store.summaries().associateBy { it.sector.agency }
        assertEquals(BusCredentialSummary(BusSector("CITSO"), true, false, 120), summaries["CITSO"])
        assertEquals(BusCredentialSummary(BusSector("CITLA"), true, true, 90), summaries["CITLA"])
        val content = Files.readString(dir.resolve(busCredentialsName))
        assertTrue(content.contains("so-secret")); assertTrue(content.contains("la-secret"))
        assertFalse(BusCredentialEdit("CITSO", "so-secret", true, 60).toString().contains("so-secret"))
        assertFalse(summaries.toString().contains("secret"))
    }
    @Test fun `bus credentials require an imported matching graph valid polling and a token when enabled`() {
        val dir = fixture(); val store = BusCredentials(dir)
        assertThrows(IllegalArgumentException::class.java) { store.save(BusCredentialEdit("CITSO", "", true, 60)) }
        assertThrows(IllegalArgumentException::class.java) { store.save(BusCredentialEdit("UNKNOWN", "fixture", true, 60)) }
        assertThrows(IllegalArgumentException::class.java) { store.save(BusCredentialEdit("CITSO", "fixture", true, 1)) }
        assertFalse(Files.exists(dir.resolve(busCredentialsName)))
        Files.delete(dir.resolve(".rndtransit-data"))
        assertThrows(IllegalArgumentException::class.java) { store.save(BusCredentialEdit("CITSO", "fixture", true, 60)) }
    }
    @Test fun `import whitelists bus inputs and keeps credentials out of generated router endpoints`() {
        val source = fixture(); val bus = BusCredentials(source)
        bus.save(BusCredentialEdit("CITSO", "private-bus-key", true, 90))
        bus.save(BusCredentialEdit("CITLA", "private-la-key", false, 60))
        Files.writeString(source.resolve("unrelated-secret.txt"), "not imported")
        val target = temporary.root.toPath().resolve("new import")
        DataSetup().importData(source, target) {}
        DataSetup().prepareRouter(ServerConfig(target, bridgePort = 19086))
        assertTrue(Files.exists(target.resolve(busCatalogName))); assertTrue(Files.exists(target.resolve(busCredentialsName)))
        assertTrue(Files.exists(target.resolve(BusSector("CITSO").archive)))
        assertFalse(Files.exists(target.resolve("unrelated-secret.txt")))
        val router = Files.readString(target.resolve("router-config.json"))
        assertTrue(router.contains("EXO_BUS_CITSO")); assertTrue(router.contains("/exo/bus/CITSO/trip-updates"))
        assertTrue(router.contains("PT90S")); assertFalse(router.contains("EXO_BUS_CITLA"))
        assertFalse(router.contains("private-bus-key")); assertFalse(router.contains("apiKey")); assertFalse(router.contains("token"))
        assertFalse(Files.exists(source.resolve("router-config.json")))
    }
    @Test fun `bus prediction and alert freshness is isolated from STM and other sectors`() {
        val initial = initialRealtimeFeeds() + listOf("CITSO","CITLA").map { RealtimeFeedState(BusSector(it).feedId, it) }
        val status = Json.parseToJsonElement("""{"realtimeByFeed":{
          "STM":{"tripUpdates":{"state":"fresh"},"alerts":{"state":"fresh"}},
          "EXO_BUS_CITSO":{"tripUpdates":{"state":"unavailable","error":"EXO HTTP 401"},"alerts":{"state":"unavailable"}},
          "EXO_BUS_CITLA":{"tripUpdates":{"state":"fresh","downloadedAtMillis":1000},"alerts":{"state":"stale"}}
        }}""").jsonObject
        val feeds = parseRealtimeFeeds(status, initial).associateBy { it.id }
        assertEquals(FeedConnection.LIVE, feeds["STM"]!!.predictions.connection)
        assertEquals(FeedConnection.AUTH_ERROR, feeds["EXO_BUS_CITSO"]!!.predictions.connection)
        assertEquals(FeedConnection.LIVE, feeds["EXO_BUS_CITLA"]!!.predictions.connection)
        assertEquals(FeedConnection.STALE, feeds["EXO_BUS_CITLA"]!!.alerts.connection)
    }
    @Test fun `malformed bus credential diagnostics never include secret fragments`() {
        val dir = fixture()
        Files.writeString(dir.resolve(busCredentialsName), """{"feeds":[{"token":"private-fragment"} BROKEN]}""")
        val error = assertThrows(IllegalArgumentException::class.java) { BusCredentials(dir).summaries() }
        assertFalse(error.toString().contains("private-fragment")); assertNull(error.cause)
    }
}
