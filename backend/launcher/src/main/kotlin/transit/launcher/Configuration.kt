package transit.launcher

import kotlinx.serialization.json.*
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

/** User-editable settings contain paths and public auth identifiers, never feed or tunnel tokens. */
data class ServerConfig(
    val dataDirectory: Path,
    val apiPort: Int = 18088,
    val otpPort: Int = 18085,
    val bridgePort: Int = 18086,
    val otpMemoryMb: Int = 4096,
    val tunnelMode: String = "off",
    val cloudflared: String = "",
    val tunnelTokenFile: String = "",
    val publicAddress: String = "",
    val supabaseUrl: String = "",
    val supabaseKey: String = "",
    val devtunnel: String = "",
    val devTunnelId: String = "rndtransit",
) {
    /** Validates before any service is launched, including loopback tunnel authentication. */
    fun validate() {
        val ports = listOf(apiPort, otpPort, bridgePort) + if (tunnelMode in listOf("quick", "named")) listOf(metricsPort()) else emptyList()
        require(ports.all { it in 1024..65535 } && ports.distinct().size == ports.size) {
            "API, OTP, bridge and tunnel metrics ports must be different, between 1024 and 65535."
        }
        require(otpMemoryMb in 512..65536) { "OTP memory must be 512–65536 MB." }
        require(tunnelMode in listOf("off", "quick", "named", "microsoft")) { "Tunnel mode must be off, quick, named or microsoft." }
        if (supabaseUrl.isNotBlank() || supabaseKey.isNotBlank() || tunnelMode != "off") {
            require(runCatching { URI(supabaseUrl).let { it.scheme == "https" && !it.host.isNullOrBlank() && it.userInfo == null } }.getOrDefault(false)
                && supabaseKey.isNotBlank()) { "Configure Supabase HTTPS URL and publishable key before using a public tunnel." }
        }
        if (tunnelMode in listOf("quick", "named")) require(Files.isRegularFile(Path.of(cloudflared))) { "Choose an existing cloudflared.exe." }
        if (tunnelMode == "microsoft") {
            devTunnelExecutable(devtunnel)
            requireDevTunnelId(devTunnelId)
        }
        if (tunnelMode == "named") {
            require(tunnelTokenFile.isNotBlank() && Files.isRegularFile(Path.of(tunnelTokenFile)) && Files.size(Path.of(tunnelTokenFile)) > 0) {
                "Named tunnel requires a nonempty token file. Store the token there, never in server.properties."
            }
            require(runCatching { URI(publicAddress).let { it.scheme == "https" && !it.host.isNullOrBlank() && it.userInfo == null && it.rawQuery == null } }.getOrDefault(false)) {
                "Named tunnel requires its public HTTPS address."
            }
        }
    }

    /** A fixed private port makes tunnel readiness independently observable. */
    fun metricsPort(): Int = 18089
}

/** Saves settings outside the application directory so copied packages remain read-only. */
class ConfigStore(val home: Path) {
    val file: Path = home.resolve("server.properties")

    /** Loads defaults on first run, rejecting malformed values instead of silently changing ports. */
    fun load(): ServerConfig {
        Files.createDirectories(home)
        if (!Files.exists(file)) save(ServerConfig(home.resolve("data")))
        val values = Properties().apply { Files.newBufferedReader(file).use { load(it) } }
        fun value(key: String, default: String = "") = values.getProperty(key, default).trim()
        fun number(key: String, default: Int) = value(key, "$default").toIntOrNull()
            ?: error("$key must be a whole number in server.properties.")
        return ServerConfig(Path.of(value("dataDirectory", home.resolve("data").toString())).toAbsolutePath(),
            number("apiPort", 18088), number("otpPort", 18085), number("bridgePort", 18086), number("otpMemoryMb", 4096),
            value("tunnelMode", "off"), value("cloudflared"), value("tunnelTokenFile"), value("publicAddress"),
            value("supabaseUrl"), value("supabaseKey"), value("devtunnel"), value("devTunnelId", "rndtransit"))
    }

    /** @param config Settings to replace atomically on the next Start/Restart. */
    fun save(config: ServerConfig) {
        Files.createDirectories(home)
        val values = Properties().apply {
            setProperty("dataDirectory", config.dataDirectory.toString())
            setProperty("apiPort", "${config.apiPort}"); setProperty("otpPort", "${config.otpPort}")
            setProperty("bridgePort", "${config.bridgePort}"); setProperty("otpMemoryMb", "${config.otpMemoryMb}")
            setProperty("tunnelMode", config.tunnelMode); setProperty("cloudflared", config.cloudflared)
            setProperty("tunnelTokenFile", config.tunnelTokenFile); setProperty("publicAddress", config.publicAddress)
            setProperty("supabaseUrl", config.supabaseUrl); setProperty("supabaseKey", config.supabaseKey)
            setProperty("devtunnel", config.devtunnel); setProperty("devTunnelId", config.devTunnelId)
        }
        val temporary = file.resolveSibling("server.properties.tmp")
        Files.newBufferedWriter(temporary).use { values.store(it, "RNDTransit Server. Restart after edits. See SERVER_GUIDE.md for tunnel setup.") }
        Files.move(temporary, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }
}

/** Imports an existing prepared graph without changing the source checkout or copying unrelated files. */
class DataSetup {
    private val files = listOf("graph.obj", "stm-adapter-index.json", "stm-upstream.json", "exo-realtime.local.json",
        "stm-gtfs.zip", "exo-trains-gtfs.zip", "build-config.json")

    /**
     * Copies prepared data into a new dedicated directory; existing imports are never overwritten.
     * @param source Existing stm-scheduled directory with graph and credentials.
     * @param target Empty destination selected in the launcher.
     * @param progress Receives filenames only; file contents are never logged.
     */
    fun importData(source: Path, target: Path, progress: (String) -> Unit) {
        require(source.toRealPath() != target.toAbsolutePath().normalize()) { "Choose a separate, empty destination; the original stack is preserved." }
        require(!Files.exists(target) || Files.list(target).use { it.findAny().isEmpty }) { "Import destination must be empty. Choose a new folder." }
        validateData(source, requireMarker = false)
        Files.createDirectories(target)
        for (name in files) {
            val input = source.resolve(name)
            if (Files.isRegularFile(input)) {
                progress("Importing $name")
                Files.copy(input, target.resolve(name))
            }
        }
        if (!Files.exists(target.resolve("stm-upstream.json"))) {
            Files.copy(source.resolve("router-config.json"), target.resolve("stm-upstream.json"))
        }
        Files.writeString(target.resolve(".rndtransit-data"), "Launcher-managed copy; contains private feed credentials. Do not distribute.\n")
        validateData(target)
    }

    /**
     * Rejects missing or malformed inputs before launching Java processes.
     * @param data Prepared stack directory.
     * @param requireMarker Whether this must be an imported launcher-owned copy.
     */
    fun validateData(data: Path, requireMarker: Boolean = true) {
        if (requireMarker) require(Files.exists(data.resolve(".rndtransit-data"))) { "Import your prepared OTP stack first (Setup / import)." }
        for (name in listOf("graph.obj", "stm-adapter-index.json", "stm-gtfs.zip")) {
            require(Files.isRegularFile(data.resolve(name)) && Files.size(data.resolve(name)) > 0) { "Missing $name. Prepare the graph/index in the development checkout, then import it." }
        }
        val upstream = data.resolve("stm-upstream.json").takeIf(Files::exists) ?: data.resolve("router-config.json")
        require(Files.isRegularFile(upstream)) { "Missing STM configuration. Configure the STM API key before importing." }
        val root = Json.parseToJsonElement(Files.readString(upstream)).jsonObject
        require(root["updaters"]?.jsonArray.orEmpty().any {
            it.jsonObject["feedId"]?.jsonPrimitive?.content == "STM" &&
                !it.jsonObject["headers"]?.jsonObject?.get("apiKey")?.jsonPrimitive?.content.isNullOrBlank()
        }) { "Missing STM apiKey. Run Connect-StmRealtime.ps1 in your existing stack." }
        val index = Json.parseToJsonElement(Files.readString(data.resolve("stm-adapter-index.json"))).jsonObject
        require(listOf("routes", "stops", "directions").all { index[it] is JsonObject }) { "Invalid STM index. Run prepare-stm-index.py in your existing stack." }
        val exo = data.resolve("exo-realtime.local.json")
        if (Files.exists(exo)) {
            val values = Json.parseToJsonElement(Files.readString(exo)).jsonObject
            require(!values["token"]?.jsonPrimitive?.content.isNullOrBlank() && values["pollSeconds"]?.jsonPrimitive?.intOrNull in 30..3600) { "Invalid exo token or polling interval (30–3600 seconds)." }
            require(Files.isRegularFile(data.resolve("exo-trains-gtfs.zip"))) { "exo realtime requires the exo train GTFS archive used to build the combined graph." }
        }
    }

    /**
     * Creates OTP's private upstream configuration in the imported copy, stripping external credentials.
     * @param config Current loopback ports and data directory.
     */
    fun prepareRouter(config: ServerConfig) {
        val root = Json.parseToJsonElement(Files.readString(config.dataDirectory.resolve("stm-upstream.json"))).jsonObject
        val others = root["updaters"]?.jsonArray.orEmpty().filterNot {
            it.jsonObject["feedId"]?.jsonPrimitive?.content in listOf("STM", "EXO_TRAINS") &&
                it.jsonObject["type"]?.jsonPrimitive?.content in listOf("stop-time-updater", "real-time-alerts")
        }
        // Unknown credential-bearing updaters could echo their keys in OTP logs; require explicit development setup instead.
        require(others.none { it.jsonObject.containsKey("headers") }) { "Unsupported credential-bearing updater. Use STM/exo bridge updaters only in the launcher." }
        val updates = mutableListOf<JsonElement>()
        fun updater(feed: String, prefix: String, seconds: Int) {
            listOf("stop-time-updater" to "trip-updates", "real-time-alerts" to "alerts").forEach { (type, path) ->
                updates += buildJsonObject {
                    put("type", type); put("feedId", feed); put("url", "http://127.0.0.1:${config.bridgePort}/$prefix$path")
                    put("frequency", "PT${seconds}S")
                }
            }
        }
        updater("STM", "", 60)
        val exo = config.dataDirectory.resolve("exo-realtime.local.json")
        if (Files.exists(exo)) updater("EXO_TRAINS", "exo/", Json.parseToJsonElement(Files.readString(exo)).jsonObject.getValue("pollSeconds").jsonPrimitive.int)
        val router = JsonObject(root + ("updaters" to JsonArray(others + updates)))
        Files.writeString(config.dataDirectory.resolve("router-config.json"), Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), router))
    }
}
