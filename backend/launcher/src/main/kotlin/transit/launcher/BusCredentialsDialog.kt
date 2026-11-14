package transit.launcher

import java.awt.*
import javax.swing.*
import javax.swing.border.EmptyBorder

/** Only bus sectors present in the imported graph can be enabled. Existing tokens stay masked and unfilled. */
internal class BusCredentialsDialog(owner: JFrame, summaries: List<BusCredentialSummary>, save: (BusCredentialEdit) -> Unit) :
    JDialog(owner, "exo bus realtime settings", true) {
    init {
        val sector = JComboBox(summaries.map { it.sector.agency }.toTypedArray())
        val token = JPasswordField()
        val enabled = JCheckBox("Enable realtime for this sector")
        val interval = JSpinner(SpinnerNumberModel(60, 30, 3600, 30))
        val status = JLabel()
        val body = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS); border = EmptyBorder(18, 18, 18, 18) }
        contentPane = body
        fun row(label: String, field: JComponent) = JPanel(BorderLayout(12, 0)).apply {
            border = EmptyBorder(6, 0, 6, 0); maximumSize = Dimension(Int.MAX_VALUE, 40)
            add(JLabel(label).apply { preferredSize = Dimension(190, 24) }, BorderLayout.WEST); add(field, BorderLayout.CENTER)
        }
        body.add(row("Imported bus sector", sector)); body.add(row("Credentials", status))
        body.add(row("Replace Chrono bus token", token)); body.add(row("Refresh interval (seconds)", interval)); body.add(enabled)
        body.add(JTextArea("Save applies to the selected sector. Leave the token blank to keep its saved value.\n\n" +
            "Use the agency code and bus subscription from your Chrono developer account. Each sector uses its own imported schedules and realtime status.\n\n" +
            "Stop and start the server after saving. API access is verified by the feed connection, not by saving a token.").apply {
            font = UIManager.getFont("Label.font"); isEditable = false; isOpaque = false; lineWrap = true; wrapStyleWord = true
            border = EmptyBorder(12, 0, 12, 0)
        })
        fun select() {
            val item = summaries[sector.selectedIndex]
            status.text = if (item.saved) "Token saved (hidden)" else "No token saved"
            token.text = ""; enabled.isSelected = item.enabled; interval.value = item.pollSeconds
        }
        sector.addActionListener { select() }; select()
        body.add(JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
            add(JButton("Cancel").apply { addActionListener { dispose() } })
            add(JButton("Save").apply { addActionListener {
                val secret = token.password
                try {
                    interval.commitEdit()
                    save(BusCredentialEdit(sector.selectedItem as String, String(secret), enabled.isSelected, (interval.value as Number).toInt()))
                    token.text = ""; dispose()
                } catch (_: Exception) { JOptionPane.showMessageDialog(this@BusCredentialsDialog, "Choose a refresh interval from 30 to 3600 seconds.") }
                finally { secret.fill('\u0000') }
            } })
        })
        size = Dimension(730, 430); setLocationRelativeTo(owner)
    }
}
