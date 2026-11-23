package transit.launcher

import java.awt.*
import javax.swing.*
import javax.swing.border.EmptyBorder

/** Edits only public identifiers and the address provider while the shared stack is stopped. */
internal class TravelTrackerSettingsDialog(owner: JFrame, store: ConfigStore, active: () -> Boolean) :
    JDialog(owner, "TravelTracker server settings", true) {
    init {
        val settings = store.load()
        val project = JTextField(settings.travelTrackerSupabaseUrl)
        val key = JTextField(settings.travelTrackerSupabaseKey)
        val provider = JTextField(settings.nominatimUrl)
        val userAgent = JTextField(settings.nominatimUserAgent)
        val body = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS); border = EmptyBorder(18, 18, 18, 18) }
        contentPane = body
        body.add(JLabel("TravelTracker uses the same Ktor API, tunnel and OTP processes as RNDTransit."))
        body.add(JTextArea("For a different Supabase project, enter TravelTracker's project URL and publishable key below. " +
            "Leave BOTH blank to use the existing RNDTransit project. Never enter a secret or service-role key.").apply {
            isEditable = false; isOpaque = false; font = UIManager.getFont("Label.font"); lineWrap = true; wrapStyleWord = true
            border = EmptyBorder(12, 0, 10, 0)
        })
        fun row(label: String, field: JComponent) = JPanel(BorderLayout(12, 0)).apply {
            border = EmptyBorder(6, 0, 6, 0); maximumSize = Dimension(Int.MAX_VALUE, 42)
            add(JLabel(label).apply { preferredSize = Dimension(210, 26) }, BorderLayout.WEST)
            add(field, BorderLayout.CENTER)
        }
        body.add(row("TravelTracker Supabase URL", project))
        body.add(row("Publishable / legacy anon key", key))
        body.add(row("Nominatim base URL", provider))
        body.add(row("Application User-Agent", userAgent))
        body.add(JTextArea("Public Nominatim: at most one lookup per second across all users; no autocomplete. " +
            "Search and marker lookups are user-triggered, cached and attributed. No local address database is installed. " +
            "Do not send personal or confidential information. Save, then start the shared server.").apply {
            isEditable = false; isOpaque = false; font = UIManager.getFont("Label.font"); lineWrap = true; wrapStyleWord = true
            border = EmptyBorder(12, 0, 10, 0)
        })
        body.add(JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
            add(JButton("Cancel").apply { addActionListener { dispose() } })
            add(JButton("Save").apply { addActionListener {
                runCatching {
                    check(!active()) { "Stop the shared server before changing TravelTracker settings." }
                    val updated = store.load().copy(travelTrackerSupabaseUrl = project.text.trim(),
                        travelTrackerSupabaseKey = key.text.trim(), nominatimUrl = provider.text.trim(), nominatimUserAgent = userAgent.text.trim())
                    updated.validateTravelTrackerSettings()
                    store.save(updated)
                    dispose()
                }.onFailure { JOptionPane.showMessageDialog(this@TravelTrackerSettingsDialog, it.message ?: "Cannot save settings.") }
            } })
        })
        body.components.filterIsInstance<JComponent>().forEach { it.alignmentX = Component.LEFT_ALIGNMENT }
        size = Dimension(800, 430); minimumSize = Dimension(680, 420); setLocationRelativeTo(owner)
    }
}
