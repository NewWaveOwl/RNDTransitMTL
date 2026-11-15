package transit.launcher

import java.nio.file.Files
import java.nio.file.Path

/** Resolves the installed CLI even when the launcher predates winget's PATH update. */
internal fun devTunnelExecutable(configured: String, path: String = System.getenv("PATH").orEmpty(),
    localAppData: String? = System.getenv("LOCALAPPDATA")): String {
    if (configured.isNotBlank()) {
        val file = Path.of(configured)
        require(file.isAbsolute && Files.isRegularFile(file)) { "Choose the full path to an existing devtunnel.exe in Tunnel settings." }
        return file.toString()
    }
    val directories = path.split(java.io.File.pathSeparator).filter(String::isNotBlank).map { it.trim('"') } +
        listOfNotNull(localAppData?.let { "$it/Microsoft/WinGet/Links" })
    return directories.mapNotNull { runCatching { Path.of(it).resolve("devtunnel.exe") }.getOrNull() }
        .firstOrNull { Files.isRegularFile(it) }?.toAbsolutePath()?.toString()
        ?: error("Microsoft Dev Tunnels is not installed. Open Tunnel settings to download it or choose devtunnel.exe.")
}

internal fun requireDevTunnelId(id: String) {
    require(Regex("[A-Za-z0-9][A-Za-z0-9-]{0,59}(?:\\.[A-Za-z0-9-]+)?").matches(id)) {
        "Enter a persistent Microsoft tunnel ID, for example rndtransit or rndtransit.use."
    }
}

/** Keeps the forwarding URL for our API port, excluding inspect links and other ports. */
internal fun devTunnelAddress(line: String, apiPort: Int): String? =
    Regex("https://[a-z0-9-]+-$apiPort(?:\\.[a-z0-9-]+)?\\.devtunnels\\.ms(?=[:/\\s]|$)", RegexOption.IGNORE_CASE)
        .find(line)?.value
