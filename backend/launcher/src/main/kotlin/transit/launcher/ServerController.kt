package transit.launcher

import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Immutable display state, published by the service owner and rendered only on Swing's UI thread. */
data class ServerState(val services: Map<String, String> = listOf("Ktor", "OTP", "Bridge", "Tunnel").associateWith { "Stopped" },
    val message: String = "Import a prepared OTP stack, then press Start.", val localAddress: String = "", val publicAddress: String = "",
    val realtimeFeeds: List<RealtimeFeedState> = initialRealtimeFeeds())

/**
 * Builds Java's argument vector from concrete JAR paths; Windows Path rejects wildcard characters.
 * @param bundle Portable application root, including paths containing spaces.
 * @return Java executable, classpath and backend entry point without shell quoting.
 */
internal fun backendCommand(bundle: Path): List<String> {
    val libraryDirectory = bundle.resolve("services/backend/lib")
    require(Files.isDirectory(libraryDirectory)) { "Missing packaged Ktor libraries." }
    val jars = Files.list(libraryDirectory).use { files ->
        files.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".jar", ignoreCase = true) }
            .sorted().map { it.toAbsolutePath().toString() }.toList()
    }
    require(jars.isNotEmpty()) { "Packaged Ktor library directory has no JAR files. Copy the entire server package." }
    return listOf(bundle.resolve("runtime/bin/java.exe").toString(), "-cp", jars.joinToString(java.io.File.pathSeparator), "transit.ServerKt")
}

/** Supervises the packaged services without Gradle, Python, PowerShell, or a system Java installation. */
class ServerController(
    private val store: ConfigStore,
    private val bundle: Path,
    private val log: ServiceLog,
    private val publish: (ServerState) -> Unit,
) {
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "server-actions").apply { isDaemon = true } }
    private val monitor = Executors.newSingleThreadScheduledExecutor { Thread(it, "server-monitor").apply { isDaemon = true } }
    private val cancelled = AtomicBoolean(false)
    private val busy = AtomicBoolean(false)
    private val processes = OwnedProcesses(store.home, log)
    private val readiness = Readiness()
    @Volatile private var running = false
    @Volatile private var state = ServerState()
    @Volatile private var config: ServerConfig? = null

    init {
        worker.execute {
            runCatching { processes.recover() }.onFailure { update(message = "Recovery needs attention: ${it.message}") }
            runCatching { update(realtimeFeeds = credentialFeeds(store.load())) }
                .onFailure { update(message = "Realtime credential settings need attention. Open Feed credentials or Data folder.") }
        }
        monitor.scheduleWithFixedDelay({ monitorServices() }, 5, 5, TimeUnit.SECONDS)
    }

    /** @return Whether startup, shutdown, import or a running server still needs supervision. */
    fun active(): Boolean = busy.get() || running

    /** Starts once. Repeated clicks cannot create duplicate service processes. */
    fun start() {
        if (running || !busy.compareAndSet(false, true)) return
        cancelled.set(false)
        worker.execute { try { startServices() } finally { busy.set(false) } }
    }

    /** Cancels readiness immediately and queues reverse-order shutdown on the service worker. */
    fun stop() {
        cancelled.set(true)
        worker.execute {
            busy.set(true)
            try { stopServices(); update(message = "All managed services stopped.") }
            catch (error: Exception) { fail(error) }
            finally { busy.set(false) }
        }
    }

    /** Stops the owned stack and reloads configuration before retrying startup. */
    fun restart() {
        if (!busy.compareAndSet(false, true)) return
        cancelled.set(true)
        worker.execute {
            try { stopServices(); cancelled.set(false); startServices() }
            catch (error: Exception) { fail(error) }
            finally { busy.set(false) }
        }
    }

    /**
     * Imports only when stopped; the current graph and credentials are copied, never removed.
     * @param source Existing prepared stack.
     * @param destination Empty launcher data folder.
     */
    fun importData(source: Path, destination: Path) {
        if (running || !busy.compareAndSet(false, true)) { update(message = "Stop the server before importing data."); return }
        worker.execute {
            try {
                DataSetup().importData(source, destination) { update(message = it) }
                store.save(store.load().copy(dataDirectory = destination.toAbsolutePath()))
                update(message = "Import complete. Original stack unchanged. Press Start.", realtimeFeeds = credentialFeeds(store.load()))
            } catch (error: Exception) { fail(error) }
            finally { busy.set(false) }
        }
    }

    /** Saves private feed credentials on the service worker, only while no managed service/setup is active. */
    fun saveFeedCredentials(edit: FeedCredentialEdit) {
        if (running || !busy.compareAndSet(false, true)) { update(message = "Stop the server before changing feed credentials."); return }
        worker.execute {
            try {
                val settings = store.load()
                log.secrets = log.secrets + listOf(edit.stmKey, edit.exoToken).filter(String::isNotBlank)
                FeedCredentials(settings.dataDirectory).save(edit)
                update(message = "Feed credentials saved locally. Press Start server to connect and verify them.", realtimeFeeds = credentialFeeds(settings))
            } catch (error: Exception) { fail(error) }
            finally { busy.set(false) }
        }
    }

    /** Rechecks bridge status without making upstream API calls or blocking Swing. */
    fun refreshFeeds() { monitor.execute { refreshRealtimeFeeds() } }

    /** Edits one bus sector without replacing another sector's token or publishing credentials. */
    fun saveBusCredentials(edit: BusCredentialEdit) {
        if (running || !busy.compareAndSet(false, true)) { update(message = "Stop the server before changing bus credentials."); return }
        worker.execute {
            try {
                val settings = store.load()
                if (edit.token.isNotBlank()) log.secrets = log.secrets + edit.token
                BusCredentials(settings.dataDirectory).save(edit)
                update(message = "exo bus settings saved. Press Start server to connect.", realtimeFeeds = credentialFeeds(settings))
            } catch (error: Exception) { fail(error) }
            finally { busy.set(false) }
        }
    }

    private fun credentialFeeds(settings: ServerConfig): List<RealtimeFeedState> {
        val summary = FeedCredentials(settings.dataDirectory).summary()
        return initialRealtimeFeeds().map { it.copy(credentials = if (if (it.id == "STM") summary.stm else summary.exo) "Saved" else "Missing") } +
            BusCredentials(settings.dataDirectory).summaries().map {
                RealtimeFeedState(it.sector.feedId, "exo ${it.sector.agency}", credentials = if (it.saved) (if (it.enabled) "Saved" else "Saved (off)") else "Missing")
            }
    }

    private fun refreshRealtimeFeeds() {
        val settings = config ?: return
        if (cancelled.get() || !processes.alive("Bridge")) return
        val feeds = runCatching {
            parseRealtimeFeeds(readiness.jsonObject("http://127.0.0.1:${settings.bridgePort}/status"), state.realtimeFeeds)
        }.getOrElse {
            state.realtimeFeeds.map { row -> row.copy(predictions = row.predictions.copy(connection = FeedConnection.BRIDGE_UNAVAILABLE),
                alerts = row.alerts.copy(connection = FeedConnection.BRIDGE_UNAVAILABLE)) }
        }
        // A late HTTP response from an old run must not overwrite Stopped or a new import/start.
        publishRealtimeFeeds(settings, feeds)
    }

    @Synchronized private fun publishRealtimeFeeds(settings: ServerConfig, feeds: List<RealtimeFeedState>) {
        if (!cancelled.get() && config === settings && processes.alive("Bridge")) update(realtimeFeeds = feeds)
    }

    /** One-time Microsoft setup is explicit; regular starts only host the saved persistent tunnel. */
    fun setupMicrosoft(settings: ServerConfig, signIn: Boolean) {
        if (running || !busy.compareAndSet(false, true)) { update(message = "Stop the server before Microsoft tunnel setup."); return }
        cancelled.set(false)
        worker.execute {
            try {
                processes.recover()
                val executable = devTunnelExecutable(settings.devtunnel)
                if (signIn) {
                    update(message = "Complete Microsoft sign-in in your browser. Stop server cancels setup.")
                    processes.run("Microsoft-setup", listOf(executable, "user", "login"), store.home, 180, cancelled::get)
                    update(message = "Microsoft sign-in complete. Save your existing tunnel ID, or Create a new tunnel in Tunnel settings.")
                } else {
                    requireDevTunnelId(settings.devTunnelId)
                    require(settings.apiPort in 1024..65535) { "API port must be between 1024 and 65535." }
                    update(message = "Creating Microsoft tunnel for the API port only...")
                    processes.run("Microsoft-setup", listOf(executable, "create", settings.devTunnelId, "-a"), store.home, 60, cancelled::get)
                    processes.run("Microsoft-setup", listOf(executable, "port", "create", settings.devTunnelId, "-p", "${settings.apiPort}", "--protocol", "http"), store.home, 60, cancelled::get)
                    update(message = "Microsoft tunnel created. Press Start server. If setup was partial, see the Guide before retrying Create.")
                }
            } catch (error: Exception) { fail(error) }
            finally { runCatching { processes.stop() }.onFailure { log.write("launcher", "Setup shutdown needs attention: ${it.message}") }; busy.set(false) }
        }
    }

    /** Shuts down before a user-requested application exit. @param completed Runs after verified shutdown. */
    fun exit(completed: () -> Unit) {
        cancelled.set(true)
        worker.execute {
            try { stopServices(); completed() }
            catch (error: Exception) { fail(error) }
        }
    }

    private fun startServices() {
        try {
            check(!cancelled.get()) { "Startup cancelled." }
            processes.recover()
            val settings = store.load().also { it.validate() }
            config = settings
            configureRedaction(settings)
            update(message = "Checking runtimes, graph, feed credentials and ports…", localAddress = "http://127.0.0.1:${settings.apiPort}", publicAddress = "",
                realtimeFeeds = credentialFeeds(settings).map { it.copy(predictions = FeedReading(FeedConnection.WAITING), alerts = FeedReading(FeedConnection.WAITING)) })
            DataSetup().validateData(settings.dataDirectory)
            val java21 = bundle.resolve("runtime/bin/java.exe")
            val java25 = bundle.resolve("services/otp-runtime/bin/java.exe")
            val otpJar = bundle.resolve("services/otp/otp.jar")
            for (file in listOf(java21, java25, otpJar, bundle.resolve("services/bridge/StmRealtimeAdapter.class"))) {
                require(Files.isRegularFile(file)) { "Package is incomplete: ${file.fileName}. Rebuild with Package-Server.ps1 and copy the entire package." }
            }
            val ktorCommand = backendCommand(bundle)
            for (port in listOf(settings.apiPort, settings.otpPort, settings.bridgePort) + if (settings.tunnelMode in listOf("quick", "named")) listOf(settings.metricsPort()) else emptyList()) readiness.requireAvailable(port)
            DataSetup().prepareRouter(settings)
            // The bridge starts first because OTP's configured updaters poll its private endpoints.
            service("Bridge", "Starting — live feeds may fall back to schedules")
            processes.start("Bridge", listOf(java25.toString(), "-cp", "$otpJar;${bundle.resolve("services/bridge")}", "StmRealtimeAdapter",
                settings.dataDirectory.toString(), "${settings.bridgePort}", "${settings.otpPort}"), settings.dataDirectory)
            await("Bridge", 90) { readiness.check("http://127.0.0.1:${settings.bridgePort}/status", valid = { Json.parseToJsonElement(it).jsonObject.containsKey("realtimeByFeed") }) }
            service("Bridge", "Ready - see Realtime feeds")
            refreshRealtimeFeeds()
            service("OTP", "Loading graph…")
            processes.start("OTP", listOf(java25.toString(), "-Xmx${settings.otpMemoryMb}M", "-jar", otpJar.toString(),
                "--load", "--serve", "--bindAddress", "127.0.0.1", "--port", "${settings.otpPort}", settings.dataDirectory.toString()), settings.dataDirectory)
            await("OTP", 300) { otpReady(settings) }
            service("OTP", "Ready")
            service("Ktor", "Loading routes and fare zones…")
            val environment = mapOf("HOST" to "127.0.0.1", "PORT" to "${settings.apiPort}",
                "OTP_URL" to "http://127.0.0.1:${settings.bridgePort}/otp/gtfs/v1",
                "OTP_CATALOGUE_URL" to "http://127.0.0.1:${settings.otpPort}/otp/gtfs/v1",
                "FARE_ZONES_FILE" to store.home.resolve("cache/fare-zones-2026.geojson").toString(),
                "SUPABASE_URL" to settings.supabaseUrl, "SUPABASE_PUBLISHABLE_KEY" to settings.supabaseKey,
                "PUBLIC_API" to "${settings.tunnelMode != "off"}")
            processes.start("Ktor", ktorCommand, store.home, environment)
            await("Ktor", 90) { readiness.check("http://127.0.0.1:${settings.apiPort}/health", valid = { Json.parseToJsonElement(it).jsonObject["status"]?.jsonPrimitive?.content == "ready" }) }
            service("Ktor", "Ready")
            if (settings.tunnelMode != "off") startTunnel(settings) else service("Tunnel", "Off — local access only")
            check(!cancelled.get()) { "Startup cancelled." }
            running = true
            update(message = "Server ready. Trip planning uses the actual backend. Keep this PC powered on.")
        } catch (error: Exception) {
            runCatching { stopServices() }.onFailure { log.write("launcher", "Shutdown needs attention: ${it.message}") }
            fail(error)
        }
    }

    private fun startTunnel(settings: ServerConfig) {
        if (settings.tunnelMode == "microsoft") { startMicrosoftTunnel(settings); return }
        service("Tunnel", "Connecting…")
        val args = mutableListOf(settings.cloudflared, "tunnel", "--no-autoupdate", "--metrics", "127.0.0.1:${settings.metricsPort()}")
        val environment = if (settings.tunnelMode == "quick") {
            args += listOf("--url", "http://127.0.0.1:${settings.apiPort}"); emptyMap()
        } else {
            args += "run"
            mapOf("TUNNEL_TOKEN_FILE" to Path.of(settings.tunnelTokenFile).toAbsolutePath().toString())
        }
        processes.start("Tunnel", args, store.home, environment) { line ->
            if (settings.tunnelMode == "quick") Regex("https://[a-z0-9-]+\\.trycloudflare\\.com").find(line)?.value?.let { update(publicAddress = it) }
        }
        await("Tunnel", 90) { readiness.check("http://127.0.0.1:${settings.metricsPort()}/ready") }
        if (settings.tunnelMode == "named") update(publicAddress = settings.publicAddress.trimEnd('/'))
        service("Tunnel", if (settings.tunnelMode == "quick") "Connected — temporary URL" else "Connected — verify hostname ingress in Cloudflare")
    }

    private fun startMicrosoftTunnel(settings: ServerConfig) {
        service("Tunnel", "Connecting - Microsoft Dev Tunnels")
        val address = AtomicReference<String?>(null)
        processes.start("Tunnel", listOf(devTunnelExecutable(settings.devtunnel), "host", settings.devTunnelId), store.home) { line ->
            devTunnelAddress(line, settings.apiPort)?.let { address.set(it) }
        }
        // A printed URL is not enough: check HTTPS reaches the backend after its local readiness.
        await("Tunnel", 90) { address.get()?.let { publicTunnelReady(it) } == true }
        update(publicAddress = address.get()!!)
        service("Tunnel", "Connected - Microsoft Dev Tunnels")
    }

    private fun publicTunnelReady(address: String) = readiness.check("$address/health", valid = {
        Json.parseToJsonElement(it).jsonObject["status"]?.jsonPrimitive?.content == "ready"
    })

    private fun configureRedaction(settings: ServerConfig) {
        val secrets = mutableListOf(settings.supabaseKey)
        for (name in listOf("stm-upstream.json", "exo-realtime.local.json", busCredentialsName)) {
            val file = settings.dataDirectory.resolve(name)
            if (Files.isRegularFile(file)) {
                // Extract before JSON validation so parse diagnostics cannot disclose a partly configured key.
                Regex("\"(?:apiKey|token)\"\\s*:\\s*\"([^\"]+)\"").findAll(Files.readString(file)).forEach { secrets += it.groupValues[1] }
            }
        }
        if (settings.tunnelMode == "named" && Files.isRegularFile(Path.of(settings.tunnelTokenFile))) secrets += Files.readString(Path.of(settings.tunnelTokenFile)).trim()
        log.secrets = secrets.filter(String::isNotBlank)
    }

    private fun await(name: String, seconds: Long, ready: () -> Boolean) {
        readiness.await(name, seconds, { !cancelled.get() && processes.alive(name) }, ready)
    }

    private fun otpReady(settings: ServerConfig) = readiness.check("http://127.0.0.1:${settings.otpPort}/otp/gtfs/v1",
        """{"query":"{ routes { gtfsId } }"}""") {
        val result = Json.parseToJsonElement(it).jsonObject
        result["errors"] == null && result["data"]?.jsonObject?.get("routes") is JsonArray
    }

    private fun stopServices() {
        running = false
        processes.stop()
        update(services = state.services.mapValues { "Stopped" }, publicAddress = "")
        update(realtimeFeeds = state.realtimeFeeds.map { it.copy(predictions = it.predictions.copy(connection = FeedConnection.STOPPED),
            alerts = it.alerts.copy(connection = FeedConnection.STOPPED)) })
    }

    private fun monitorServices() {
        refreshRealtimeFeeds()
        if (!running || busy.get()) return
        val settings = config ?: return
        val expected = listOf("Bridge", "OTP", "Ktor") + if (settings.tunnelMode == "off") emptyList() else listOf("Tunnel")
        val failed = expected.firstOrNull { !processes.alive(it) }
        if (failed != null) {
            running = false
            worker.execute {
                runCatching { stopServices() }
                update(message = "$failed exited. Managed services stopped. Open logs, then press Start to retry.")
            }
        } else {
            val healthy = otpReady(settings) && readiness.check("http://127.0.0.1:${settings.apiPort}/health")
            val tunnelHealthy = settings.tunnelMode != "microsoft" || publicTunnelReady(state.publicAddress)
            if (!running) return
            if (settings.tunnelMode == "microsoft") service("Tunnel", if (tunnelHealthy) "Connected - Microsoft Dev Tunnels" else "Reconnecting - Microsoft public endpoint unavailable")
            update(message = if (!tunnelHealthy) "Local server is running; Microsoft tunnel is not responding. Check internet, sign-in and Tunnel logs."
                else if (healthy) "Server ready. Agency outages use scheduled fallback."
                else "A service is running but not responding. Inspect logs or press Restart.")
        }
    }

    private fun fail(error: Exception) {
        val diagnostic = if (error is kotlinx.serialization.SerializationException) "Invalid JSON configuration. Check local feed/config files."
            else error.message ?: error.javaClass.simpleName
        log.write("launcher", "${error.javaClass.simpleName}: $diagnostic")
        // Parser errors can contain input fragments; the UI points at the redacted logs instead.
        val summary = if (error is kotlinx.serialization.SerializationException) "Invalid JSON configuration"
            else if (error is IllegalArgumentException || error is IllegalStateException) error.message?.substringBefore('\n') ?: error.javaClass.simpleName
            else error.javaClass.simpleName
        update(message = log.redact("$summary. Open logs, correct setup and retry."))
    }

    private fun service(name: String, status: String) {
        if (state.services[name] == status) return
        update(services = state.services + (name to status)); log.write("launcher", "$name: $status")
    }

    @Synchronized private fun update(services: Map<String, String>? = null, message: String? = null,
        localAddress: String? = null, publicAddress: String? = null,
        realtimeFeeds: List<RealtimeFeedState>? = null) {
        state = ServerState(services ?: state.services, message ?: state.message, localAddress ?: state.localAddress,
            publicAddress ?: state.publicAddress, realtimeFeeds ?: state.realtimeFeeds)
        publish(state)
    }
}
