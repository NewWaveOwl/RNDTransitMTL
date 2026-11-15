package transit.launcher

import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.time.Instant

enum class FeedConnection(val label: String) {
    LIVE("Live"), STALE("Stale"), UNAVAILABLE("Unavailable"), AUTH_ERROR("Auth failed"),
    NOT_CONFIGURED("Not configured"), WAITING("Waiting"), STOPPED("Stopped"), BRIDGE_UNAVAILABLE("Bridge unavailable")
}

data class FeedReading(val connection: FeedConnection = FeedConnection.STOPPED, val lastSuccess: Instant? = null)
data class RealtimeFeedState(val id: String, val agency: String, val predictions: FeedReading = FeedReading(),
    val alerts: FeedReading = FeedReading(), val credentials: String = "Not checked")

internal fun initialRealtimeFeeds() = listOf(RealtimeFeedState("STM", "STM"), RealtimeFeedState("EXO_TRAINS", "exo trains"))

/** downloadedAtMillis is retained by the bridge on failure: never replace it with the current poll time. */
internal fun parseRealtimeFeeds(status: JsonObject, previous: List<RealtimeFeedState>): List<RealtimeFeedState> {
    val feeds = status["realtimeByFeed"] as? JsonObject ?: error("Bridge feed status is missing.")
    fun reading(value: JsonElement?, old: FeedReading): FeedReading {
        val data = value as? JsonObject
        val error = (data?.get("error") as? JsonPrimitive)?.contentOrNull
        val connection = when {
            error == "not_configured" -> FeedConnection.NOT_CONFIGURED
            error == "not_downloaded" -> FeedConnection.WAITING
            error?.matches(Regex("(?:STM|EXO) HTTP (?:401|403)")) == true -> FeedConnection.AUTH_ERROR
            error != null -> FeedConnection.UNAVAILABLE
            else -> when ((data?.get("state") as? JsonPrimitive)?.contentOrNull) {
                "fresh" -> FeedConnection.LIVE
                "stale" -> FeedConnection.STALE
                else -> FeedConnection.UNAVAILABLE
            }
        }
        val downloaded = (data?.get("downloadedAtMillis") as? JsonPrimitive)?.longOrNull
            ?.takeIf { it > 0 }?.let(Instant::ofEpochMilli)
        return FeedReading(connection, downloaded ?: old.lastSuccess)
    }
    return previous.map { old ->
        val feed = feeds[old.id] as? JsonObject
        old.copy(predictions = reading(feed?.get("tripUpdates"), old.predictions), alerts = reading(feed?.get("alerts"), old.alerts))
    }
}

data class FeedCredentialSummary(val stm: Boolean, val exo: Boolean, val exoPollSeconds: Int)
data class FeedCredentialEdit(val stmKey: String, val exoToken: String, val exoPollSeconds: Int)

/** Edits only the imported private copy. Blank password fields preserve existing keys. */
internal class FeedCredentials(private val directory: Path) {
    private val stmFile = directory.resolve("stm-upstream.json")
    private val exoFile = directory.resolve("exo-realtime.local.json")
    private fun read(file: Path): JsonObject = if (!Files.exists(file)) JsonObject(emptyMap()) else {
        runCatching { Json.parseToJsonElement(Files.readString(file)).jsonObject }
            .getOrElse { throw IllegalArgumentException("Invalid ${file.fileName}. Correct the JSON in Data folder before editing credentials.") }
    }
    private fun stmKey(root: JsonObject) = (root["updaters"] as? JsonArray).orEmpty().mapNotNull { value ->
        val item = value as? JsonObject
        if ((item?.get("feedId") as? JsonPrimitive)?.contentOrNull != "STM") null
        else (((item["headers"] as? JsonObject)?.get("apiKey")) as? JsonPrimitive)?.contentOrNull
    }.lastOrNull { it.isNotBlank() }.orEmpty()
    private fun token(root: JsonObject) = (root["token"] as? JsonPrimitive)?.contentOrNull.orEmpty()

    fun summary(): FeedCredentialSummary {
        val stm = read(stmFile); val exo = read(exoFile)
        return FeedCredentialSummary(stmKey(stm).isNotBlank(), token(exo).isNotBlank(),
            ((exo["pollSeconds"] as? JsonPrimitive)?.intOrNull ?: 60).coerceIn(30, 3600))
    }

    fun save(edit: FeedCredentialEdit) {
        require(Files.isRegularFile(directory.resolve(".rndtransit-data"))) { "Import your prepared data first (Setup / import)." }
        require(edit.exoPollSeconds in 30..3600) { "exo polling interval must be 30-3600 seconds." }
        val stm = read(stmFile); val exo = read(exoFile)
        val key = edit.stmKey.trim().ifBlank { stmKey(stm) }
        val exoKey = edit.exoToken.trim().ifBlank { token(exo) }
        require(key.isNotBlank()) { "Enter an STM API key. A blank field only keeps an existing key." }
        require(!key.contains('\n') && !key.contains('\r') && !exoKey.contains('\n') && !exoKey.contains('\r')) { "API keys must be on one line." }
        if (exoKey.isNotBlank()) require(Files.isRegularFile(directory.resolve("exo-trains-gtfs.zip"))) {
            "Import the matching exo train GTFS archive before enabling its realtime feed."
        }
        val updaters = (stm["updaters"] as? JsonArray).orEmpty().toMutableList()
        var replaced = false
        for (index in updaters.indices) {
            val item = updaters[index] as? JsonObject ?: continue
            if ((item["feedId"] as? JsonPrimitive)?.contentOrNull == "STM") {
                val headers = (item["headers"] as? JsonObject).orEmpty() + ("apiKey" to JsonPrimitive(key))
                updaters[index] = JsonObject(item + ("headers" to JsonObject(headers))); replaced = true
            }
        }
        if (!replaced) updaters += buildJsonObject {
            put("type", "stop-time-updater"); put("feedId", "STM")
            put("headers", buildJsonObject { put("apiKey", key) })
        }
        val newStm = JsonObject(stm + ("updaters" to JsonArray(updaters)))
        val newExo = JsonObject(exo + mapOf("token" to JsonPrimitive(exoKey), "pollSeconds" to JsonPrimitive(edit.exoPollSeconds)))
        write(stmFile, newStm)
        if (exoKey.isNotBlank()) write(exoFile, newExo)
    }

    private fun write(file: Path, value: JsonObject) {
        val temporary = Files.createTempFile(directory, "feed-settings-", ".tmp")
        try {
            Files.writeString(temporary, Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), value))
            Files.move(temporary, file, REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temporary) }
    }
}
