package transit.launcher

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URI
import java.net.HttpURLConnection
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.*
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Prevents two launcher instances managing the same user directory. The OS releases the lock on crash. */
class InstanceLock private constructor(private val channel: FileChannel, private val lock: FileLock) : AutoCloseable {
    override fun close() { lock.release(); channel.close() }

    companion object {
        /** @param home Per-user configuration directory. Returns null if another launcher owns it. */
        fun acquire(home: Path): InstanceLock? {
            Files.createDirectories(home)
            val channel = FileChannel.open(home.resolve("launcher.lock"), CREATE, WRITE)
            val lock = runCatching { channel.tryLock() }.getOrNull()
            if (lock == null) { channel.close(); return null }
            return InstanceLock(channel, lock)
        }
    }
}

/** Keeps bounded per-component logs, redacting configured secrets before disk and UI output. */
class ServiceLog(private val directory: Path, private val display: (String) -> Unit) {
    @Volatile var secrets: List<String> = emptyList()

    /** @param message Raw diagnostic to sanitize for either file logs or an error label. */
    fun redact(message: String): String {
        var line = message
        for (secret in secrets.filter { it.length >= 4 }.sortedByDescending(String::length)) line = line.replace(secret, "[redacted]")
        return line.replace(Regex("(?i)(Bearer\\s+)[^\\s\"']+"), "$1[redacted]")
    }

    /** @param component Safe service name. @param message Raw process line, never displayed before redaction. */
    @Synchronized fun write(component: String, message: String) {
        Files.createDirectories(directory)
        val file = directory.resolve("$component.log")
        if (Files.exists(file) && Files.size(file) > 2_000_000) {
            for (index in 2 downTo 1) {
                val old = directory.resolve("$component.log.$index")
                if (Files.exists(old)) Files.move(old, directory.resolve("$component.log.${index + 1}"), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }
            Files.move(file, directory.resolve("$component.log.1"), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
        val line = redact(message)
        val formatted = "${Instant.now()} [$component] $line"
        Files.writeString(file, "$formatted\n", CREATE, APPEND)
        display(formatted)
    }
}

/** A persisted identity includes process creation time and executable, preventing PID-reuse termination. */
data class OwnedIdentity(val pid: Long, val started: Instant, val executable: String) {
    /** @param handle Candidate process from a saved PID. Missing OS metadata never authorizes stopping. */
    fun matches(handle: ProcessHandle): Boolean = handle.pid() == pid && handle.info().startInstant().orElse(null) == started &&
        handle.info().command().orElse(null)?.let { runCatching { Path.of(it).toRealPath() == Path.of(executable).toRealPath() }.getOrDefault(false) } == true
}

/** Tracks only directly launched services; never scans or kills processes by name or occupied port. */
class OwnedProcesses(private val home: Path, private val log: ServiceLog) {
    private val children = linkedMapOf<String, Process>()
    private val ledger = home.resolve("owned-processes.tsv")

    /** Reclaims verified children from a previous crashed launcher after obtaining its exclusive lock. */
    fun recover() {
        if (!Files.exists(ledger)) return
        for (line in Files.readAllLines(ledger)) {
            val identity = runCatching {
                val fields = line.split('\t')
                OwnedIdentity(fields[0].toLong(), Instant.parse(fields[1]), fields[2])
            }.getOrNull() ?: error("Saved process ownership record is malformed. Inspect owned-processes.tsv before retrying; no unverified process was stopped.")
            ProcessHandle.of(identity.pid).orElse(null)?.let { handle ->
                require(!handle.isAlive || identity.matches(handle)) { "A saved service PID cannot be verified. Check previous services manually; no process was stopped." }
                if (handle.isAlive) {
                    log.write("launcher", "Stopping verified service left by the previous launcher (${identity.pid}).")
                    handle.destroy()
                    runCatching { handle.onExit().get(8, TimeUnit.SECONDS) }
                    if (handle.isAlive && identity.matches(handle)) handle.destroyForcibly()
                    runCatching { handle.onExit().get(5, TimeUnit.SECONDS) }
                    check(!handle.isAlive) { "Previous managed service did not stop. Retry after checking its process." }
                }
            }
        }
        Files.deleteIfExists(ledger)
    }

    /**
     * Starts an argument-vector process, avoiding shell quoting and exposing no tokens in its command line.
     * @param name Service label.
     * @param command Executable and separate arguments.
     * @param directory Working directory.
     * @param environment Explicit environment, including public API authentication settings.
     * @param observe Optional raw line observer for the non-secret temporary tunnel hostname.
     */
    @Synchronized fun start(name: String, command: List<String>, directory: Path, environment: Map<String, String> = emptyMap(), observe: (String) -> Unit = {}) {
        check(children[name]?.isAlive != true) { "$name is already running." }
        val process = ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).apply {
            // Do not accidentally inherit a developer's tunnel token or an unsafe API bind address.
            environment().remove("TUNNEL_TOKEN"); environment().remove("TUNNEL_TOKEN_FILE")
            environment().putAll(environment)
        }.start()
        children[name] = process
        persist()
        thread(name = "$name-output", isDaemon = true) {
            runCatching { process.inputStream.bufferedReader().useLines { lines -> lines.forEach { line -> log.write(name, line); observe(line) } } }
                .onFailure { log.write("launcher", "$name log stream closed: ${it.javaClass.simpleName}") }
        }
        log.write("launcher", "Started $name (PID ${process.pid()}).")
    }

    /** @param name A directly owned service label. */
    @Synchronized fun alive(name: String): Boolean = children[name]?.isAlive == true

    /** Stops in reverse dependency order. Descendants and unrelated processes are never targeted. */
    @Synchronized fun stop() {
        // An unsuccessful recovery must retain its ledger for manual inspection.
        if (children.isEmpty()) return
        for ((name, process) in children.entries.toList().asReversed()) {
            if (process.isAlive) {
                process.destroy()
                if (!process.waitFor(8, TimeUnit.SECONDS)) { process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS) }
                check(!process.isAlive) { "Could not stop managed $name. Retry Stop." }
            }
            log.write("launcher", "Stopped $name.")
        }
        children.clear()
        Files.deleteIfExists(ledger)
    }

    /** Saves creation time and executable only; command arguments and credentials are omitted. */
    private fun persist() {
        Files.createDirectories(home)
        val identities = children.values.filter(Process::isAlive).map { process ->
            val info = process.info()
            "${process.pid()}\t${info.startInstant().orElseThrow()}\t${info.command().orElseThrow()}"
        }
        val temporary = ledger.resolveSibling("owned-processes.tsv.tmp")
        Files.write(temporary, identities)
        Files.move(temporary, ledger, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }
}

/** Readiness uses bounded loopback HTTP requests rather than saved PID files or console messages. */
class Readiness(private val request: (String, String?) -> Pair<Int, String> = ::readHealth) {

    /** @param port Preferred loopback port. A conflict is reported without terminating its owner. */
    fun requireAvailable(port: Int) {
        try { ServerSocket().use { socket -> socket.reuseAddress = false; socket.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), port)) } }
        catch (error: Exception) { throw IllegalStateException("Port $port is occupied. Stop its current server or change server.properties; the launcher will not kill it.", error) }
    }

    /** @param url Private readiness endpoint. @param body Optional GraphQL query for actual graph readiness. */
    fun check(url: String, body: String? = null, valid: (String) -> Boolean = { true }): Boolean = runCatching {
        val (status, text) = request(url, body)
        status == 200 && valid(text)
    }.getOrDefault(false)

    /**
     * @param name Component for actionable timeout/crash errors.
     * @param seconds Startup deadline.
     * @param alive Checks service ownership and startup cancellation on each poll.
     * @param ready Checks application readiness, allowing scheduled fallback for stale agency feeds.
     */
    fun await(name: String, seconds: Long, alive: () -> Boolean, ready: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds)
        while (System.nanoTime() < deadline) {
            check(alive()) { "$name stopped or startup was cancelled. See its log and retry." }
            if (ready()) return
            Thread.sleep(500)
        }
        error("$name did not become ready within $seconds seconds. Open logs, correct setup and retry.")
    }
}

/** Blocking loopback probe with bounded time and response size; no client threads are created at GUI startup. */
private fun readHealth(url: String, body: String?): Pair<Int, String> {
    val connection = URI(url).toURL().openConnection(java.net.Proxy.NO_PROXY) as HttpURLConnection
    connection.connectTimeout = 2000; connection.readTimeout = 3000
    try {
        if (body != null) {
            connection.requestMethod = "POST"; connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        val status = connection.responseCode
        val text = if (status == 200) connection.inputStream.use { it.readNBytes(262_144).toString(Charsets.UTF_8) } else ""
        return status to text
    } finally { connection.disconnect() }
}
