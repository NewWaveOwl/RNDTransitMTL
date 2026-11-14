package transit.launcher

import java.awt.*
import java.net.URI
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel

/** Displays bridge freshness independently of service/process readiness. */
internal class RealtimeFeedsPanel(refresh: () -> Unit, credentials: () -> Unit) : JPanel(BorderLayout(0, 6)) {
    private var feeds = initialRealtimeFeeds()
    private val model = object : DefaultTableModel(arrayOf("Agency", "Predictions", "Alerts", "Last predictions", "Last alerts", "Credentials"), 0) {
        override fun isCellEditable(row: Int, column: Int) = false
    }
    private val table = JTable(model).apply {
        rowHeight = 26; fillsViewportHeight = true; setRowSelectionAllowed(false)
        tableHeader.reorderingAllowed = false
        columnModel.getColumn(0).preferredWidth = 75
        columnModel.getColumn(1).preferredWidth = 120; columnModel.getColumn(2).preferredWidth = 120
        columnModel.getColumn(3).preferredWidth = 155; columnModel.getColumn(4).preferredWidth = 155
        columnModel.getColumn(5).preferredWidth = 75
        setDefaultRenderer(Any::class.java, object : DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(table: JTable, value: Any?, selected: Boolean, focus: Boolean, row: Int, column: Int): Component {
                val label = super.getTableCellRendererComponent(table, value, selected, focus, row, column)
                foreground = Color.DARK_GRAY
                val feed = feeds.getOrNull(row)
                val status = when (column) { 1 -> feed?.predictions?.connection; 2 -> feed?.alerts?.connection; else -> null }
                foreground = when (status) {
                    FeedConnection.LIVE -> Color(15, 110, 75)
                    FeedConnection.STALE, FeedConnection.WAITING -> Color(145, 90, 0)
                    FeedConnection.AUTH_ERROR, FeedConnection.UNAVAILABLE, FeedConnection.BRIDGE_UNAVAILABLE -> Color(170, 40, 30)
                    else -> Color.DARK_GRAY
                }
                toolTipText = value?.toString()
                return label
            }
        })
    }
    private val time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())

    init {
        border = BorderFactory.createTitledBorder("Realtime feeds")
        maximumSize = Dimension(Int.MAX_VALUE, 160)
        add(JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
            add(JButton("Refresh").apply { addActionListener { refresh() } })
            add(JButton("Feed credentials...").apply { addActionListener { credentials() } })
            add(JLabel("Refreshes automatically while the server runs"))
        }, BorderLayout.NORTH)
        add(JScrollPane(table).apply { preferredSize = Dimension(800, 77) }, BorderLayout.CENTER)
        add(JLabel("Times show the last successful downloads in local time. Stale/unavailable predictions use scheduled routing."), BorderLayout.SOUTH)
        render(feeds)
    }

    fun render(value: List<RealtimeFeedState>) {
        feeds = value; model.rowCount = 0
        for (feed in feeds) model.addRow(arrayOf(feed.agency, feed.predictions.connection.label, feed.alerts.connection.label,
            feed.predictions.lastSuccess?.let(time::format) ?: "Never", feed.alerts.lastSuccess?.let(time::format) ?: "Never", feed.credentials))
    }
}

/** Existing secret values are never placed in fields; new values only replace them on explicit Save. */
internal class FeedCredentialsDialog(owner: JFrame, summary: FeedCredentialSummary, save: (FeedCredentialEdit) -> Unit) :
    JDialog(owner, "Realtime feed credentials", true) {
    init {
        val stm = JPasswordField(); val exo = JPasswordField()
        val interval = JSpinner(SpinnerNumberModel(summary.exoPollSeconds, 30, 3600, 30))
        val body = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS); border = EmptyBorder(18, 18, 18, 18) }
        contentPane = body
        body.add(JLabel("STM API key: ${if (summary.stm) "saved" else "missing"}. exo train token: ${if (summary.exo) "saved" else "missing"}."))
        fun row(label: String, field: JComponent) = JPanel(BorderLayout(12, 0)).apply {
            border = EmptyBorder(8, 0, 8, 0); maximumSize = Dimension(Int.MAX_VALUE, 46)
            add(JLabel(label).apply { preferredSize = Dimension(200, 24) }, BorderLayout.WEST); add(field, BorderLayout.CENTER)
        }
        body.add(row("Replace STM API key", stm)); body.add(row("Replace exo train token", exo))
        body.add(row("exo refresh interval (seconds)", interval))
        body.add(JTextArea("Leave a key field blank to keep its saved value. Credentials stay in your imported data folder.\n\n" +
            "Press Start server after saving to connect. A saved key does not confirm API access; the feeds panel shows the result.\n\n" +
            "exo bus feeds are not connected in this build.").apply {
            font = UIManager.getFont("Label.font"); isEditable = false; isOpaque = false; lineWrap = true; wrapStyleWord = true
            border = EmptyBorder(12, 0, 12, 0)
        })
        body.add(JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
            add(JButton("Chrono developer portal").apply { addActionListener {
                runCatching { Desktop.getDesktop().browse(URI("https://portail-developpeur.chrono-saeiv.com/")) }
                    .onFailure { JOptionPane.showMessageDialog(this@FeedCredentialsDialog, "Open https://portail-developpeur.chrono-saeiv.com/ in your browser.") }
            } })
            add(JButton("Cancel").apply { addActionListener { dispose() } })
            add(JButton("Save").apply { addActionListener {
                val stmChars = stm.password; val exoChars = exo.password
                try {
                    interval.commitEdit()
                    save(FeedCredentialEdit(String(stmChars), String(exoChars), (interval.value as Number).toInt()))
                    stm.text = ""; exo.text = ""; dispose()
                } catch (_: Exception) { JOptionPane.showMessageDialog(this@FeedCredentialsDialog, "Choose a refresh interval from 30 to 3600 seconds.") }
                finally { stmChars.fill('\u0000'); exoChars.fill('\u0000') }
            } })
        })
        size = Dimension(730, 410); setLocationRelativeTo(owner)
    }
}
