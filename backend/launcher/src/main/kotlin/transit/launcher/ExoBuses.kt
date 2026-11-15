package transit.launcher

import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest

internal const val busCatalogName = "exo-buses.json"
internal const val busCredentialsName = "exo-bus-realtime.local.json"
internal data class BusSector(val agency: String) {
    init { require(Regex("[A-Z][A-Z0-9_]{1,31}").matches(agency) && agency != "TRAINS") { "Invalid exo bus agency code." } }
    val feedId get() = "EXO_BUS_$agency"
    val archive get() = "exo-bus-$agency-gtfs.zip"
}
internal fun privateJson(file: Path): JsonObject = if (!Files.exists(file)) JsonObject(emptyMap()) else
    runCatching { Json.parseToJsonElement(Files.readString(file)).jsonObject }
        .getOrElse { throw IllegalArgumentException("Invalid ${file.fileName}. Check this file in Data folder.") }

internal fun fileSha256(file: Path): String {
    val digest = MessageDigest.getInstance("SHA-256")
    Files.newInputStream(file).use { input ->
        val buffer = ByteArray(65536)
        while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
    }
    return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
}

/** Public graph manifest is generated after a successful build, never by the credential editor. */
internal fun busSectors(directory: Path, verify: Boolean = false): List<BusSector> {
    if (!Files.exists(directory.resolve(busCatalogName))) return emptyList()
    val root = privateJson(directory.resolve(busCatalogName))
    val values = root["agencies"] as? JsonArray ?: error("Missing exo bus agency list. Rebuild using Prepare-ExoBusGraph.ps1.")
    require(values.size <= 16) { "Too many exo bus sectors." }
    val sectors = values.map { BusSector((it as? JsonPrimitive)?.contentOrNull.orEmpty()) }
    require(sectors.distinct().size == sectors.size) { "Duplicate exo bus agency." }
    if (verify) {
        require(fileSha256(directory.resolve("graph.obj")).equals((root["graphSha256"] as? JsonPrimitive)?.contentOrNull, true)) {
            "exo bus graph changed. Rebuild using Prepare-ExoBusGraph.ps1, then import its output."
        }
        val hashes = root["scheduleSha256"] as? JsonObject
        for (sector in sectors) require(fileSha256(directory.resolve(sector.archive)).equals((hashes?.get(sector.agency) as? JsonPrimitive)?.contentOrNull, true)) {
            "exo bus schedule changed. Rebuild the combined graph before starting."
        }
    }
    return sectors
}

internal data class BusCredentialSummary(val sector: BusSector, val saved: Boolean, val enabled: Boolean, val pollSeconds: Int)
data class BusCredentialEdit(val agency: String, val token: String, val enabled: Boolean, val pollSeconds: Int) {
    override fun toString() = "BusCredentialEdit(agency=$agency, token=[redacted], enabled=$enabled, pollSeconds=$pollSeconds)"
}

internal class BusCredentials(private val directory: Path) {
    private val file = directory.resolve(busCredentialsName)
    private fun entries(): List<JsonObject> {
        if (!Files.exists(file)) return emptyList()
        val root = privateJson(file)
        val array = root["feeds"] as? JsonArray ?: error("exo bus credentials require a feeds array.")
        val result = array.map { it as? JsonObject ?: error("Invalid exo bus credential entry.") }
        val agencies = result.map { BusSector((it["agency"] as? JsonPrimitive)?.contentOrNull.orEmpty()).agency }
        require(agencies.distinct().size == agencies.size) { "Duplicate exo bus credentials." }
        require(agencies.all { agency -> busSectors(directory).any { it.agency == agency } }) { "Bus credentials reference a sector outside the imported graph." }
        return result
    }
    fun summaries(): List<BusCredentialSummary> {
        val entries = entries().associateBy { (it["agency"] as JsonPrimitive).content }
        return busSectors(directory).map { sector ->
            val entry = entries[sector.agency]
            BusCredentialSummary(sector, !(entry?.get("token") as? JsonPrimitive)?.contentOrNull.isNullOrBlank(),
                entry != null && (entry["enabled"] as? JsonPrimitive)?.booleanOrNull != false,
                ((entry?.get("pollSeconds") as? JsonPrimitive)?.intOrNull ?: 60).coerceIn(30, 3600))
        }
    }
    fun validate() {
        val sectors = busSectors(directory, verify = true)
        for (entry in entries()) {
            val agency = (entry["agency"] as JsonPrimitive).content
            require(sectors.any { it.agency == agency }) { "Build a graph containing this exo bus sector first." }
            val enabled = (entry["enabled"] as? JsonPrimitive)?.booleanOrNull != false
            require((entry["pollSeconds"] as? JsonPrimitive)?.intOrNull in 30..3600) { "exo bus polling interval must be 30-3600 seconds." }
            require(!enabled || !(entry["token"] as? JsonPrimitive)?.contentOrNull.isNullOrBlank()) { "Enter an exo bus subscription token before enabling realtime." }
        }
    }
    fun save(edit: BusCredentialEdit) {
        require(Files.isRegularFile(directory.resolve(".rndtransit-data"))) { "Import the prepared bus graph first." }
        val sector = BusSector(edit.agency)
        require(busSectors(directory, verify = true).contains(sector)) { "Import a graph built for this bus sector first." }
        require(edit.pollSeconds in 30..3600) { "exo bus polling interval must be 30-3600 seconds." }
        val root = privateJson(file); val entries = entries().toMutableList()
        val index = entries.indexOfFirst { (it["agency"] as? JsonPrimitive)?.contentOrNull == sector.agency }
        val old = entries.getOrNull(index) ?: JsonObject(emptyMap())
        val token = edit.token.trim().ifBlank { (old["token"] as? JsonPrimitive)?.contentOrNull.orEmpty() }
        require(!token.contains('\n') && !token.contains('\r') && (!edit.enabled || token.isNotBlank())) { "Enter a one-line Chrono bus token to enable realtime." }
        val replacement = JsonObject(old + mapOf("agency" to JsonPrimitive(sector.agency), "token" to JsonPrimitive(token),
            "enabled" to JsonPrimitive(edit.enabled), "pollSeconds" to JsonPrimitive(edit.pollSeconds)))
        if (index < 0) entries += replacement else entries[index] = replacement
        val temporary = Files.createTempFile(directory, "exo-bus-settings-", ".tmp")
        try {
            Files.writeString(temporary, Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), JsonObject(root + ("feeds" to JsonArray(entries)))))
            Files.move(temporary, file, REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temporary) }
    }
}
