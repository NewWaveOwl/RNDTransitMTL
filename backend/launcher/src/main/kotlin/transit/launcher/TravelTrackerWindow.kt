package transit.launcher

import java.awt.*
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.concurrent.Executors
import java.util.concurrent.Future
import javax.swing.*
import javax.swing.border.EmptyBorder

/**
 * Observes the existing service owner; opening or closing this window never creates or stops a service.
 * @param owner The existing RNDTransit window.
 * @param store Public operator settings stored outside the portable application.
 * @param controller The one process owner also used by the RNDTransit window.
 */
internal class TravelTrackerWindow(owner: JFrame, private val store: ConfigStore, private val controller: ServerController) :
    JFrame("TravelTracker Server") {
    private val services = listOf("Ktor", "OTP", "Bridge", "Tunnel").associateWith { JLabel("Stopped") }
    private val endpoint = JTextField("Start the shared server to obtain its API address").apply { isEditable = false }
    private val provider = JLabel("Public Nominatim (external; no local database)")
    private val counters = JLabel("Start the shared server to view request counters.")
    private val message = JTextArea("Uses the existing server process owner.", 2, 40)
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "traveltracker-status").apply { isDaemon = true } }
    private val readiness = Readiness()
    private var inFlight: Future<*>? = null
    private var sharedState = ServerState()
    @Volatile private var generation = 0L
    private val timer = Timer(5000) { refresh() }

    init {
        defaultCloseOperation = HIDE_ON_CLOSE
        size = Dimension(840, 560); minimumSize = Dimension(680, 500); setLocationRelativeTo(owner)
        val body = JPanel(BorderLayout(0, 16)).apply { border = EmptyBorder(22, 24, 20, 24) }
        contentPane = body
        body.add(JPanel(BorderLayout(0, 8)).apply {
            add(JLabel("TravelTracker Server").apply { font = font.deriveFont(Font.BOLD, 26f) }, BorderLayout.NORTH)
            add(JLabel("Shared OTP routing and Public Nominatim address lookup"), BorderLayout.SOUTH)
        }, BorderLayout.NORTH)
        body.add(JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(JPanel(GridLayout(4, 2, 12, 8)).apply {
                border = BorderFactory.createTitledBorder("Shared services — also used by RNDTransit")
                services.forEach { (name, status) -> add(JLabel(name)); add(status) }
                maximumSize = Dimension(Int.MAX_VALUE, 155)
            })
            add(Box.createVerticalStrut(12))
            add(JLabel("Shared API base URL — copy this into TravelTracker's Services:Routing setting"))
            add(endpoint.apply { maximumSize = Dimension(Int.MAX_VALUE, 30) })
            add(Box.createVerticalStrut(14)); add(provider); add(Box.createVerticalStrut(8)); add(counters)
            message.isEditable = false; message.isOpaque = false; message.lineWrap = true; message.wrapStyleWord = true
            message.font = UIManager.getFont("Label.font")
            message.border = EmptyBorder(14, 0, 0, 0); add(message)
            add(JTextArea("Public Nominatim requests are queued at one lookup per second and cached. " +
                "Its availability is independent of OTP. Counters only confirm backend activity, not provider or route availability. " +
                "Closing this window hides it and leaves both apps' services running.").apply {
                isEditable = false; isOpaque = false; font = UIManager.getFont("Label.font"); lineWrap = true; wrapStyleWord = true
                border = EmptyBorder(12, 0, 0, 0)
            })
            components.filterIsInstance<JComponent>().forEach { it.alignmentX = Component.LEFT_ALIGNMENT }
        }, BorderLayout.CENTER)
        body.add(JPanel(GridLayout(2, 1, 0, 8)).apply {
            add(JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
                add(action("Start shared server") { controller.start() })
                add(action("Stop shared server") { if (confirmSharedStop("Stop")) controller.stop() })
                add(action("Restart shared server") { if (confirmSharedStop("Restart")) controller.restart() })
            })
            add(JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
                add(action("TravelTracker settings") {
                    check(!controller.active()) { "Stop the shared server before changing TravelTracker settings." }
                    TravelTrackerSettingsDialog(this@TravelTrackerWindow, store, controller::active).isVisible = true
                })
                add(action("Refresh counters") { refresh() })
                add(action("Hide window") { isVisible = false })
            })
        }, BorderLayout.SOUTH)
        addComponentListener(object : ComponentAdapter() {
            override fun componentShown(event: ComponentEvent) { timer.start(); refresh() }
            override fun componentHidden(event: ComponentEvent) { stopPolling() }
        })
        addWindowListener(object : WindowAdapter() { override fun windowClosed(event: WindowEvent) { stopPolling(); worker.shutdownNow() } })
    }

    /**
     * Displays the same snapshot as the main window, without acquiring another process controller.
     * @param snapshot Immutable shared service and tunnel state.
     */
    fun render(snapshot: ServerState) {
        val changedRun = sharedState.localAddress != snapshot.localAddress || sharedState.services["Ktor"] != snapshot.services["Ktor"]
        sharedState = snapshot
        services.forEach { (name, label) -> label.text = snapshot.services.getValue(name) }
        endpoint.text = snapshot.publicAddress.ifBlank { snapshot.localAddress.ifBlank { "Start the shared server to obtain its API address" } }
        message.text = snapshot.message
        if (changedRun) { generation++; inFlight?.cancel(true); inFlight = null }
        if (!backendReady()) counters.text = "Counters unavailable while the shared backend is stopped or starting."
        else if (isVisible && changedRun) refresh()
    }

    private fun backendReady(): Boolean = sharedState.services["Ktor"]?.startsWith("Ready") == true

    private fun refresh() {
        if (!isVisible || !backendReady() || inFlight?.isDone == false) return
        val currentGeneration = generation
        val port = runCatching { store.load().apiPort }.getOrElse { counters.text = "Cannot read the local API port."; return }
        inFlight = worker.submit {
            val status = runCatching { TravelTrackerStatus.parse(readiness.jsonObject("http://127.0.0.1:$port/health/traveltracker")) }
            SwingUtilities.invokeLater {
                if (!isVisible || generation != currentGeneration || !backendReady()) return@invokeLater
                status.onSuccess {
                    provider.text = "Address provider: ${it.addressProvider} (external; no local database)"
                    counters.text = "Address calls: ${it.addressRequests}   Cached: ${it.addressCacheHits}   Queued: ${it.addressQueued}   Route calls: ${it.routeRequests}"
                }.onFailure { counters.text = "TravelTracker counters unavailable; shared routing services remain running." }
            }
        }
    }

    private fun stopPolling() { generation++; timer.stop(); inFlight?.cancel(true); inFlight = null }

    private fun confirmSharedStop(operation: String): Boolean = !controller.active() || JOptionPane.showConfirmDialog(this,
        "$operation affects routing and address requests for BOTH TravelTracker and RNDTransit. Continue?",
        "$operation shared server", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION

    private fun action(label: String, callback: () -> Unit) = JButton(label).apply {
        addActionListener { runCatching(callback).onFailure { JOptionPane.showMessageDialog(this@TravelTrackerWindow, it.message ?: "Cannot complete server action.") } }
    }
}
