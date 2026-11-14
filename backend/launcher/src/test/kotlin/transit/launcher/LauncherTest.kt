package transit.launcher

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.TimeUnit

/** Regression checks cover process safety and lifecycle rather than Swing implementation details. */
class LauncherTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun directory(name: String = "settings with spaces") = temporary.newFolder(name).toPath()
    private fun logger(home: Path) = ServiceLog(home.resolve("logs")) {}
    private fun fixtureCommand(): List<String> {
        // Gradle workers use an isolated test classloader; java.class.path alone omits these classes.
        val classpath = listOf(ProcessFixture::class.java, Unit::class.java).map { Path.of(it.protectionDomain.codeSource.location.toURI()).toString() }
            .joinToString(java.io.File.pathSeparator)
        return listOf(Path.of(System.getProperty("java.home"), "bin", if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java").toString(),
            "-cp", classpath, "transit.launcher.ProcessFixture")
    }

    @Test fun `settings round trip Windows paths and blank auth without changing them`() {
        val store = ConfigStore(directory())
        val expected = ServerConfig(Path.of("C:/a folder/data"), apiPort = 19088, otpMemoryMb = 2048)
        store.save(expected)
        assertEquals(expected.copy(dataDirectory = expected.dataDirectory.toAbsolutePath()), store.load())
    }

    @Test fun `malformed port never silently falls back to default`() {
        val store = ConfigStore(directory()); store.load()
        Files.writeString(store.file, "apiPort=banana\n")
        assertThrows(IllegalStateException::class.java) { store.load() }
    }

    @Test fun `duplicate or privileged ports rejected`() {
        assertThrows(IllegalArgumentException::class.java) { ServerConfig(directory(), apiPort = 18085).validate() }
        assertThrows(IllegalArgumentException::class.java) { ServerConfig(directory("other"), apiPort = 80).validate() }
    }

    @Test fun `local mode requires no auth or cloudflare`() { ServerConfig(directory()).validate() }

    @Test fun `tunnel requires auth even though services bind to loopback`() {
        assertThrows(IllegalArgumentException::class.java) { ServerConfig(directory(), tunnelMode = "quick").validate() }
    }

    @Test fun `invalid tunnel mode rejected`() {
        assertThrows(IllegalArgumentException::class.java) { ServerConfig(directory(), tunnelMode = "unknown").validate() }
    }

    @Test fun `named tunnel needs token file and HTTPS address`() {
        val home = directory(); val executable = Files.writeString(home.resolve("cloudflared.exe"), "fixture")
        val config = ServerConfig(home, tunnelMode = "named", cloudflared = executable.toString(), supabaseUrl = "https://project.example", supabaseKey = "publishable")
        assertThrows(IllegalArgumentException::class.java) { config.validate() }
        val token = Files.writeString(home.resolve("token.txt"), "private-token")
        config.copy(tunnelTokenFile = token.toString(), publicAddress = "https://trips.example").validate()
        assertThrows(IllegalArgumentException::class.java) { config.copy(tunnelTokenFile = token.toString(), publicAddress = "http://trips.example").validate() }
    }

    @Test fun `one launcher owns a user directory at a time and releases its lock`() {
        val home = directory()
        InstanceLock.acquire(home)!!.use { assertNull(InstanceLock.acquire(home)) }
        InstanceLock.acquire(home)!!.close()
    }

    @Test fun `port conflict leaves listener untouched`() {
        ServerSocket(0).use { server ->
            assertThrows(IllegalStateException::class.java) { Readiness().requireAvailable(server.localPort) }
            assertFalse(server.isClosed)
        }
    }

    @Test fun `readiness requires expected body and rejects HTTP errors`() {
        val readiness = Readiness { url, body ->
            assertEquals("{ query }", body)
            if (url.endsWith("/ready")) 200 to "ready" else 503 to "ready"
        }
        assertTrue(readiness.check("http://127.0.0.1/ready", "{ query }", valid = { it == "ready" }))
        assertFalse(readiness.check("http://127.0.0.1/ready", "{ query }", valid = { it == "wrong" }))
        assertFalse(readiness.check("http://127.0.0.1/missing", "{ query }"))
        assertFalse(Readiness { _, _ -> error("disconnected") }.check("http://127.0.0.1"))
    }

    @Test fun `readiness cancellation and timeout fail clearly`() {
        assertThrows(IllegalStateException::class.java) { Readiness().await("OTP", 2, { false }, { true }) }
        assertThrows(IllegalStateException::class.java) { Readiness().await("OTP", 0, { true }, { false }) }
    }

    @Test fun `owned process stops without touching unrelated process`() {
        val home = directory(); val owned = OwnedProcesses(home, logger(home))
        val unrelated = ProcessBuilder(fixtureCommand()).start()
        try {
            owned.start("fixture", fixtureCommand(), home)
            assertTrue(owned.alive("fixture"))
            assertThrows(IllegalStateException::class.java) { owned.start("fixture", fixtureCommand(), home) }
            owned.stop()
            assertFalse(owned.alive("fixture")); assertTrue(unrelated.isAlive)
            assertFalse(Files.exists(home.resolve("owned-processes.tsv")))
        } finally { unrelated.destroyForcibly().waitFor(5, TimeUnit.SECONDS); owned.stop() }
    }

    @Test fun `recovery stops only process matching saved creation time and executable`() {
        val home = directory(); val owned = OwnedProcesses(home, logger(home))
        try {
            owned.start("fixture", fixtureCommand(), home)
            OwnedProcesses(home, logger(home)).recover()
            assertFalse(owned.alive("fixture"))
        } finally { owned.stop() }
    }

    @Test fun `reused PID or unverifiable record does not authorize killing`() {
        val home = directory(); val unrelated = ProcessBuilder(fixtureCommand()).start()
        try {
            val executable = unrelated.info().command().orElseThrow()
            Files.writeString(home.resolve("owned-processes.tsv"), "${unrelated.pid()}\t${Instant.EPOCH}\t$executable\n")
            assertThrows(IllegalArgumentException::class.java) { OwnedProcesses(home, logger(home)).recover() }
            assertTrue(unrelated.isAlive)
        } finally { unrelated.destroyForcibly().waitFor(5, TimeUnit.SECONDS) }
    }

    @Test fun `secrets are removed from disk and UI logs`() {
        val home = directory(); val messages = mutableListOf<String>()
        val log = ServiceLog(home, messages::add).apply { secrets = listOf("private-secret") }
        log.write("fixture", "key=private-secret Bearer session-value")
        val text = Files.readString(home.resolve("fixture.log"))
        assertFalse(text.contains("private-secret")); assertFalse(text.contains("session-value"))
        assertFalse(messages.joinToString().contains("private-secret")); assertTrue(text.contains("[redacted]"))
    }

    @Test fun `logs rotate at bounded size`() {
        val home = directory(); Files.writeString(home.resolve("OTP.log"), "x".repeat(2_000_001))
        ServiceLog(home) {}.write("OTP", "next")
        assertTrue(Files.exists(home.resolve("OTP.log.1")))
        assertTrue(Files.size(home.resolve("OTP.log")) < 1000)
    }

    private fun preparedSource(): Path {
        val source = directory("source")
        Files.writeString(source.resolve("graph.obj"), "fixture graph")
        Files.writeString(source.resolve("stm-gtfs.zip"), "fixture zip")
        Files.writeString(source.resolve("stm-adapter-index.json"), """{"routes":{},"stops":{},"directions":{}}""")
        Files.writeString(source.resolve("router-config.json"), """{"updaters":[{"type":"stop-time-updater","feedId":"STM","headers":{"apiKey":"fixture-secret"}}]}""")
        return source
    }

    @Test fun `import preserves originals and excludes unrelated files`() {
        val source = preparedSource(); val original = Files.readString(source.resolve("router-config.json"))
        Files.writeString(source.resolve("unrelated-secret.txt"), "not copied")
        val target = temporary.root.toPath().resolve("new data")
        DataSetup().importData(source, target) {}
        assertEquals(original, Files.readString(source.resolve("router-config.json")))
        assertEquals(original, Files.readString(target.resolve("stm-upstream.json")))
        assertFalse(Files.exists(target.resolve("unrelated-secret.txt")))
        assertTrue(Files.exists(target.resolve(".rndtransit-data")))
    }

    @Test fun `import rejects original folder or nonempty destination`() {
        val source = preparedSource()
        assertThrows(IllegalArgumentException::class.java) { DataSetup().importData(source, source) {} }
        val target = directory("nonempty"); Files.writeString(target.resolve("keep.txt"), "keep")
        assertThrows(IllegalArgumentException::class.java) { DataSetup().importData(source, target) {} }
        assertEquals("keep", Files.readString(target.resolve("keep.txt")))
    }

    @Test fun `missing graph or credentials fails before launching`() {
        val source = preparedSource(); Files.delete(source.resolve("graph.obj"))
        assertThrows(IllegalArgumentException::class.java) { DataSetup().validateData(source, false) }
    }

    @Test fun `router uses private bridge endpoints without copying STM credentials`() {
        val source = preparedSource(); val target = temporary.root.toPath().resolve("data")
        DataSetup().importData(source, target) {}
        DataSetup().prepareRouter(ServerConfig(target, bridgePort = 19086))
        val router = Files.readString(target.resolve("router-config.json"))
        assertFalse(router.contains("fixture-secret")); assertFalse(router.contains("apiKey"))
        assertTrue(router.contains("http://127.0.0.1:19086/trip-updates")); assertTrue(router.contains("real-time-alerts"))
    }

    @Test fun `data must be a launcher import before router can be managed`() {
        assertThrows(IllegalArgumentException::class.java) { DataSetup().validateData(preparedSource()) }
    }

    @Test fun `backend command uses concrete jars and preserves paths with spaces`() {
        val bundle = directory("server package with spaces")
        val libs = Files.createDirectories(bundle.resolve("services/backend/lib"))
        val backend = Files.writeString(libs.resolve("backend.jar"), "fixture")
        val dependency = Files.writeString(libs.resolve("ktor library.jar"), "fixture")
        Files.writeString(libs.resolve("not-a-library.txt"), "ignored")
        Files.createDirectory(libs.resolve("not-a-file.jar"))
        val command = backendCommand(bundle)
        assertEquals(bundle.resolve("runtime/bin/java.exe").toString(), command[0])
        assertEquals("-cp", command[1])
        assertEquals(setOf(backend.toAbsolutePath().toString(), dependency.toAbsolutePath().toString()), command[2].split(java.io.File.pathSeparator).toSet())
        assertFalse(command[2].contains('*'))
        assertEquals("transit.ServerKt", command[3])
    }

    @Test fun `backend command rejects missing or empty libraries before starting services`() {
        val bundle = directory()
        assertThrows(IllegalArgumentException::class.java) { backendCommand(bundle) }
        Files.createDirectories(bundle.resolve("services/backend/lib"))
        assertThrows(IllegalArgumentException::class.java) { backendCommand(bundle) }
    }
}

/** A child fixture with no networking, secret data or dependency on a Java compiler at runtime. */
object ProcessFixture {
    @JvmStatic fun main(args: Array<String>) { println("Fixture ready"); Thread.sleep(120_000) }
}
