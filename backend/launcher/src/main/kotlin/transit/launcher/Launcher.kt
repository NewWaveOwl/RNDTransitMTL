package transit.launcher

import java.awt.*
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.swing.*
import javax.swing.border.EmptyBorder
import kotlin.system.exitProcess

/** Runs the portable GUI. --self-check renders the UI and verifies packaged paths without starting services. */
fun main(args: Array<String>) {
    val selfCheck = args.firstOrNull() == "--self-check"
    val checkOutput = if (selfCheck) Path.of(args.getOrElse(1) { "self-check.png" }).toAbsolutePath() else null
    val home = System.getProperty("rnd.server.home")?.let(Path::of)
        ?: checkOutput?.parent?.resolve("self-check-state")
        ?: Path.of(System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"), "RNDTransitServer")
    val bundle = System.getProperty("rnd.server.bundle")?.let(Path::of)
        ?: System.getProperty("jpackage.app-path")?.let { Path.of(it).parent }
        ?: Path.of(".").toAbsolutePath()
    val lock = InstanceLock.acquire(home)
    if (lock == null) {
        JOptionPane.showMessageDialog(null, "RNDTransit Server is already running. Open its window or the system tray icon.")
        return
    }
    Runtime.getRuntime().addShutdownHook(Thread { runCatching { lock.close() } })
    SwingUtilities.invokeLater {
        runCatching {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
            val window = ServerWindow(ConfigStore(home), bundle)
            window.isVisible = true
            if (selfCheck) {
                val output = checkOutput!!
                Timer(1000) {
                    Files.createDirectories(output.parent)
                    val image = BufferedImage(window.width, window.height, BufferedImage.TYPE_INT_RGB)
                    image.createGraphics().also { window.paint(it); it.dispose() }
                    ImageIO.write(image, "png", output.toFile())
                    val required = listOf("runtime/bin/java.exe", "services/otp-runtime/bin/java.exe", "services/otp/otp.jar",
                        "services/bridge/StmRealtimeAdapter.class", "services/backend/lib")
                    val missing = required.filterNot { Files.exists(bundle.resolve(it)) }
                    Files.writeString(output.resolveSibling("self-check.txt"), "GUI rendered.\nBundle: $bundle\nMissing: ${missing.joinToString()}\n")
                    window.dispose()
                    exitProcess(if (missing.isEmpty()) 0 else 1)
                }.apply { isRepeats = false; start() }
            }
        }.onFailure {
            JOptionPane.showMessageDialog(null, "Cannot open RNDTransit Server: ${it.javaClass.simpleName}. Check server.properties.", "Startup error", JOptionPane.ERROR_MESSAGE)
            exitProcess(1)
        }
    }
}

/** Swing displays immutable controller state; startup and filesystem work never block the UI thread. */
class ServerWindow(private val store: ConfigStore, bundle: Path) : JFrame("RNDTransit Server") {
    private val statusLabels = listOf("Ktor", "OTP", "Bridge", "Tunnel").associateWith { JLabel("Stopped") }
    private val message = JTextArea("Import a prepared OTP stack, then press Start.", 2, 50)
    private val local = JTextField("Local address appears after startup")
    private val public = JTextField("Public access is off")
    private val logs = JTextArea()
    private val controller: ServerController
    private val realtimePanel: RealtimeFeedsPanel
    private var tray: TrayIcon? = null

    init {
        defaultCloseOperation = DO_NOTHING_ON_CLOSE
        val workArea = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
        minimumSize = Dimension(minOf(920, workArea.width), minOf(720, workArea.height))
        size = Dimension(minOf(1040, workArea.width), minOf(880, workArea.height))
        setLocation(workArea.x + (workArea.width - width) / 2, workArea.y + (workArea.height - height) / 2)
        val outer = JPanel(BorderLayout(0, 16)).apply { border = EmptyBorder(22, 24, 20, 24) }
        contentPane = outer
        val header = JPanel(BorderLayout()).apply {
            add(JLabel("RNDTransit Server").apply { font = font.deriveFont(Font.BOLD, 26f) }, BorderLayout.NORTH)
            add(JLabel("Trip generation · OpenTripPlanner · STM / exo realtime").apply { border = EmptyBorder(8, 0, 0, 0) }, BorderLayout.SOUTH)
        }
        outer.add(header, BorderLayout.NORTH)
        val body = JPanel(BorderLayout(0, 16))
        outer.add(body, BorderLayout.CENTER)
        val information = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
        val services = JPanel(GridLayout(4, 2, 12, 10)).apply {
            border = BorderFactory.createTitledBorder("Services — private routing, optional public API")
            statusLabels.forEach { (name, label) -> add(JLabel(name)); add(label) }
            maximumSize = Dimension(Int.MAX_VALUE, 155)
        }
        information.add(services)
        information.add(Box.createVerticalStrut(12))
        local.isEditable = false; public.isEditable = false
        information.add(addressRow("Local API", local)); information.add(addressRow("Public HTTPS", public))
        message.isEditable = false; message.lineWrap = true; message.wrapStyleWord = true; message.isOpaque = false
        message.border = EmptyBorder(12, 0, 4, 0)
        information.add(message)
        body.add(information, BorderLayout.NORTH)
        logs.isEditable = false; logs.font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        body.add(JScrollPane(logs).apply { border = BorderFactory.createTitledBorder("Recent logs — full rotated logs are saved locally") }, BorderLayout.CENTER)
        val logger = ServiceLog(store.home.resolve("logs")) { line -> SwingUtilities.invokeLater {
            logs.append("$line\n")
            if (logs.document.length > 60_000) logs.document.remove(0, logs.document.length - 45_000)
            logs.caretPosition = logs.document.length
        } }
        controller = ServerController(store, bundle, logger) { state -> SwingUtilities.invokeLater { render(state) } }
        realtimePanel = RealtimeFeedsPanel(controller::refreshFeeds, ::editFeedCredentials, ::editBusCredentials)
        information.add(Box.createVerticalStrut(10)); information.add(realtimePanel)
        val actions = JPanel(GridLayout(2, 1, 0, 8))
        actions.add(JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
            add(button("Start server") { controller.start() })
            add(button("Stop server") { controller.stop() })
            add(button("Restart / retry") { controller.restart() })
            add(button("Setup / import") { importData() })
        })
        actions.add(JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
            add(button("Configuration") { openFile(store.file) })
            add(button("Tunnel settings") {
                if (controller.active()) JOptionPane.showMessageDialog(this@ServerWindow, "Stop the server before changing tunnel settings.")
                else TunnelSettings(this@ServerWindow, store, controller::setupMicrosoft).isVisible = true
            })
            add(button("Data folder") { openFile(store.load().dataDirectory, true) })
            add(button("Full logs") { openFile(store.home.resolve("logs"), true) })
            add(button("Guide") { openFile(bundle.resolve("SERVER_GUIDE.md")) })
        })
        outer.add(actions, BorderLayout.SOUTH)
        store.load() // Creates editable defaults on first run, outside the packaged application.
        addWindowListener(object : WindowAdapter() { override fun windowClosing(event: WindowEvent) { closeRequested() } })
    }

    /** @param state Immutable service state published by the controller. */
    private fun render(state: ServerState) {
        statusLabels.forEach { (name, label) ->
            val status = state.services.getValue(name)
            label.text = status
            label.foreground = if (status.startsWith("Ready") || status.startsWith("Connected")) Color(15, 110, 75) else Color.DARK_GRAY
        }
        message.text = state.message
        if (state.localAddress.isNotBlank()) local.text = state.localAddress
        public.text = state.publicAddress.ifBlank { "Public access is off / not connected" }
        realtimePanel.render(state.realtimeFeeds)
    }

    private fun editFeedCredentials() {
        if (controller.active()) { JOptionPane.showMessageDialog(this, "Stop the server before changing feed credentials."); return }
        runCatching {
            val summary = FeedCredentials(store.load().dataDirectory).summary()
            FeedCredentialsDialog(this, summary, controller::saveFeedCredentials).isVisible = true
        }.onFailure { JOptionPane.showMessageDialog(this, it.message ?: "Cannot read feed credential settings.") }
    }

    private fun editBusCredentials() {
        if (controller.active()) { JOptionPane.showMessageDialog(this, "Stop the server before changing bus credentials."); return }
        runCatching {
            val summaries = BusCredentials(store.load().dataDirectory).summaries()
            if (summaries.isEmpty()) JOptionPane.showMessageDialog(this,
                "Import a combined graph containing exo bus sectors first. See Guide: exo bus setup and Prepare-ExoBusGraph.ps1.")
            else BusCredentialsDialog(this, summaries, controller::saveBusCredentials).isVisible = true
        }.onFailure { JOptionPane.showMessageDialog(this, it.message ?: "Cannot read bus settings.") }
    }

    private fun importData() {
        if (controller.active()) { JOptionPane.showMessageDialog(this, "Stop the server before importing data."); return }
        val source = chooseDirectory("Select your existing prepared stm-scheduled folder") ?: return
        val destination = chooseDirectory("Choose an EMPTY folder for the launcher's COPY", store.load().dataDirectory) ?: return
        controller.importData(source, destination)
    }

    /** @param title Folder selection purpose. @param initial Suggested new/import folder. */
    private fun chooseDirectory(title: String, initial: Path? = null): Path? {
        val chooser = JFileChooser().apply { dialogTitle = title; fileSelectionMode = JFileChooser.DIRECTORIES_ONLY; initial?.let { selectedFile = it.toFile() } }
        return if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) chooser.selectedFile.toPath() else null
    }

    /** @param path File/folder to open. @param directory Whether a missing directory should be created. */
    private fun openFile(path: Path, directory: Boolean = false) {
        runCatching {
            if (directory) Files.createDirectories(path)
            if (Files.isDirectory(path)) Desktop.getDesktop().open(path.toFile())
            else if (path.toString().endsWith(".properties") || path.toString().endsWith(".md")) ProcessBuilder("notepad.exe", path.toString()).start()
            else Desktop.getDesktop().open(path.toFile())
        }.onFailure { JOptionPane.showMessageDialog(this, "Cannot open $path: ${it.javaClass.simpleName}") }
    }

    private fun closeRequested() {
        if (!controller.active()) { controller.exit { exitProcess(0) }; return }
        val options = arrayOf("Stop services and exit", "Keep running in tray", "Cancel")
        when (JOptionPane.showOptionDialog(this, "Your phone needs these services to plan trips. What should happen?", "Close server window",
            JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[2])) {
            0 -> controller.exit { exitProcess(0) }
            1 -> hideToTray()
        }
    }

    private fun hideToTray() {
        if (!SystemTray.isSupported()) {
            JOptionPane.showMessageDialog(this, "System tray is unavailable. The launcher will stay minimized while supervising services.")
            extendedState = ICONIFIED
            return
        }
        if (tray == null) {
            val image = BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB)
            image.createGraphics().also { it.color = Color(15, 110, 75); it.fillOval(1, 1, 30, 30); it.color = Color.WHITE; it.font = Font(Font.SANS_SERIF, Font.BOLD, 22); it.drawString("R", 8, 24); it.dispose() }
            val menu = PopupMenu().apply {
                add(MenuItem("Open RNDTransit Server").apply { addActionListener { restore() } })
                add(MenuItem("Stop services and exit").apply { addActionListener { controller.exit { exitProcess(0) } } })
            }
            val icon = TrayIcon(image, "RNDTransit Server — services still running", menu).apply { isImageAutoSize = true; addActionListener { restore() } }
            runCatching { SystemTray.getSystemTray().add(icon); tray = icon }.onFailure {
                JOptionPane.showMessageDialog(this, "Cannot add tray icon; keeping the window open."); return
            }
        }
        isVisible = false
    }

    private fun restore() { SwingUtilities.invokeLater { isVisible = true; extendedState = NORMAL; toFront() } }

    /** @param label Address label. @param field Read-only, copyable address. */
    private fun addressRow(label: String, field: JTextField) = JPanel(BorderLayout(12, 0)).apply {
        border = EmptyBorder(3, 0, 3, 0); add(JLabel(label).apply { preferredSize = Dimension(110, 24) }, BorderLayout.WEST)
        add(field, BorderLayout.CENTER); maximumSize = Dimension(Int.MAX_VALUE, 32)
    }

    /** @param label Button text. @param action User event sent to the state owner. */
    private fun button(label: String, action: () -> Unit) = JButton(label).apply {
        addActionListener { runCatching(action).onFailure { JOptionPane.showMessageDialog(this@ServerWindow, "${it.message}") } }
    }
}
