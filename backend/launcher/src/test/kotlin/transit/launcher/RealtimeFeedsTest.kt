package transit.launcher

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

class RealtimeFeedsTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun objectOf(value: String) = Json.parseToJsonElement(value).jsonObject
    private fun data(): Path {
        val path = temporary.newFolder("private imported data").toPath()
        Files.writeString(path.resolve(".rndtransit-data"), "fixture")
        Files.writeString(path.resolve("stm-upstream.json"), """{"keep":"unchanged","updaters":[{"feedId":"STM","type":"stop-time-updater","headers":{"apiKey":"old-stm","other":"preserve"}},{"feedId":"OTHER","url":"https://example.test"}]}""")
        return path
    }

    @Test fun `predictions and alerts have independent connections and successful update times`() {
        val result = parseRealtimeFeeds(objectOf("""{"realtimeByFeed":{
            "STM":{"tripUpdates":{"state":"fresh","downloadedAtMillis":12000},"alerts":{"state":"unavailable","downloadedAtMillis":8000,"error":"STM HTTP 503"}},
            "EXO_TRAINS":{"tripUpdates":{"state":"unavailable","error":"not_configured"},"alerts":{"state":"unavailable","error":"not_configured"}}
        }}"""), initialRealtimeFeeds())
        assertEquals(FeedConnection.LIVE, result[0].predictions.connection)
        assertEquals(FeedConnection.UNAVAILABLE, result[0].alerts.connection)
        assertEquals(Instant.ofEpochMilli(12000), result[0].predictions.lastSuccess)
        assertEquals(Instant.ofEpochMilli(8000), result[0].alerts.lastSuccess)
        assertEquals(FeedConnection.NOT_CONFIGURED, result[1].predictions.connection)
        assertNull(result[1].predictions.lastSuccess)
    }

    @Test fun `failed downloads never manufacture a new successful timestamp`() {
        val previous = initialRealtimeFeeds().map { it.copy(predictions = FeedReading(FeedConnection.LIVE, Instant.ofEpochMilli(4000))) }
        val result = parseRealtimeFeeds(objectOf("""{"checkedAtMillis":99000,"realtimeByFeed":{
            "STM":{"tripUpdates":{"state":"unavailable","downloadedAtMillis":0,"error":"STM HTTP 401"}},
            "EXO_TRAINS":{"tripUpdates":{"state":"unavailable","error":"EXO HTTP 403"}}
        }}"""), previous)
        assertTrue(result.all { it.predictions.connection == FeedConnection.AUTH_ERROR })
        assertTrue(result.all { it.predictions.lastSuccess == Instant.ofEpochMilli(4000) })
    }

    @Test fun `stale missing and error feeds are never reported live`() {
        val result = parseRealtimeFeeds(objectOf("""{"realtimeByFeed":{
            "STM":{"tripUpdates":{"state":"stale","downloadedAtMillis":1000},"alerts":{"state":"fresh","error":"IOException"}},
            "EXO_TRAINS":{"tripUpdates":{"state":"unavailable","error":"not_downloaded"}}
        }}"""), initialRealtimeFeeds())
        assertEquals(FeedConnection.STALE, result[0].predictions.connection)
        assertEquals(FeedConnection.UNAVAILABLE, result[0].alerts.connection)
        assertEquals(FeedConnection.WAITING, result[1].predictions.connection)
        assertEquals(FeedConnection.UNAVAILABLE, result[1].alerts.connection)
        assertThrows(IllegalStateException::class.java) { parseRealtimeFeeds(objectOf("{}"), initialRealtimeFeeds()) }
    }

    @Test fun `replacement credentials preserve unrelated settings and never modify router or server properties`() {
        val path = data()
        Files.writeString(path.resolve("exo-trains-gtfs.zip"), "fixture")
        Files.writeString(path.resolve("exo-realtime.local.json"), """{"token":"old-exo","pollSeconds":60,"keep":true}""")
        Files.writeString(path.resolve("router-config.json"), "private bridge routes")
        Files.writeString(path.resolve("server.properties"), "no feed keys here")
        FeedCredentials(path).save(FeedCredentialEdit("new-stm", "new-exo", 120))
        val stm = objectOf(Files.readString(path.resolve("stm-upstream.json")))
        val exo = objectOf(Files.readString(path.resolve("exo-realtime.local.json")))
        assertEquals("unchanged", stm["keep"]?.jsonPrimitive?.content)
        assertEquals("new-stm", stm["updaters"]!!.jsonArray[0].jsonObject["headers"]!!.jsonObject["apiKey"]?.jsonPrimitive?.content)
        assertEquals("preserve", stm["updaters"]!!.jsonArray[0].jsonObject["headers"]!!.jsonObject["other"]?.jsonPrimitive?.content)
        assertEquals("OTHER", stm["updaters"]!!.jsonArray[1].jsonObject["feedId"]?.jsonPrimitive?.content)
        assertEquals("new-exo", exo["token"]?.jsonPrimitive?.content)
        assertEquals(120, exo["pollSeconds"]?.jsonPrimitive?.int)
        assertEquals(true, exo["keep"]?.jsonPrimitive?.boolean)
        assertEquals("private bridge routes", Files.readString(path.resolve("router-config.json")))
        assertEquals("no feed keys here", Files.readString(path.resolve("server.properties")))
        assertTrue(Files.list(path).use { files -> files.noneMatch { it.fileName.toString().endsWith(".tmp") } })
    }

    @Test fun `blank fields retain keys and support polling changes without displaying secrets in summary`() {
        val path = data()
        Files.writeString(path.resolve("exo-trains-gtfs.zip"), "fixture")
        Files.writeString(path.resolve("exo-realtime.local.json"), """{"token":"old-exo","pollSeconds":60}""")
        val store = FeedCredentials(path)
        store.save(FeedCredentialEdit("", "", 90))
        assertEquals(FeedCredentialSummary(true, true, 90), store.summary())
        assertTrue(Files.readString(path.resolve("stm-upstream.json")).contains("old-stm"))
        assertTrue(Files.readString(path.resolve("exo-realtime.local.json")).contains("old-exo"))
        assertFalse(store.summary().toString().contains("old-exo"))
    }

    @Test fun `STM credential editing leaves optional exo unconfigured`() {
        val path = data(); val store = FeedCredentials(path)
        store.save(FeedCredentialEdit("updated", "", 60))
        assertEquals(FeedCredentialSummary(true, false, 60), store.summary())
        assertFalse(Files.exists(path.resolve("exo-realtime.local.json")))
    }

    @Test fun `invalid credential changes fail before writing and require imported exo schedules`() {
        val path = data(); val store = FeedCredentials(path)
        val original = Files.readString(path.resolve("stm-upstream.json"))
        assertThrows(IllegalArgumentException::class.java) { store.save(FeedCredentialEdit("new-stm", "exo-key", 60)) }
        assertThrows(IllegalArgumentException::class.java) { store.save(FeedCredentialEdit("invalid\nkey", "", 60)) }
        assertThrows(IllegalArgumentException::class.java) { store.save(FeedCredentialEdit("new", "", 1)) }
        assertEquals(original, Files.readString(path.resolve("stm-upstream.json")))
        Files.delete(path.resolve(".rndtransit-data"))
        assertThrows(IllegalArgumentException::class.java) { store.save(FeedCredentialEdit("new", "", 60)) }
        assertEquals(original, Files.readString(path.resolve("stm-upstream.json")))
    }

    @Test fun `malformed private JSON reports no secret fragments or parser cause`() {
        val path = data(); val store = FeedCredentials(path)
        Files.writeString(path.resolve("stm-upstream.json"), """{"secret":"private-sensitive-fragment" invalid}""")
        val error = assertThrows(IllegalArgumentException::class.java) { store.summary() }
        assertFalse(error.toString().contains("private-sensitive-fragment"))
        assertNull(error.cause)
    }

    @Test fun `bridge status JSON reader rejects HTTP errors and invalid response shapes`() {
        assertEquals(objectOf("""{"realtimeByFeed":{}}"""), Readiness { _, _ -> 200 to """{"realtimeByFeed":{}}""" }.jsonObject("http://127.0.0.1/status"))
        val error = assertThrows(IllegalStateException::class.java) { Readiness { _, _ -> 503 to "private-response" }.jsonObject("http://127.0.0.1/status") }
        assertFalse(error.toString().contains("private-response"))
        assertThrows(IllegalStateException::class.java) { Readiness { _, _ -> 200 to "[]" }.jsonObject("http://127.0.0.1/status") }
    }
}
