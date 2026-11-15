package transit.launcher

import java.awt.BorderLayout
import java.awt.Desktop
import java.awt.Dimension
import java.awt.FlowLayout
import java.net.URI
import javax.swing.*
import javax.swing.border.EmptyBorder

/** Edits tunnel providers without requiring a properties editor or a separate terminal window. */
internal class TunnelSettings(owner: JFrame, private val store: ConfigStore,
    private val setup: (ServerConfig, Boolean) -> Unit) : JDialog(owner, "Tunnel settings", true) {
    private val original = store.load()
    private val modes = linkedMapOf("Local only" to "off", "Cloudflare - temporary URL" to "quick",
        "Cloudflare - named tunnel" to "named", "Microsoft Dev Tunnels (Azure)" to "microsoft")
    private val mode = JComboBox(modes.keys.toTypedArray())
    private val executable = JTextField(original.devtunnel)
    private val id = JTextField(original.devTunnelId)
    private val cloudflared = JTextField(original.cloudflared)
    private val tokenFile = JTextField(original.tunnelTokenFile)
    private val address = JTextField(original.publicAddress)
    private val authUrl = JTextField(original.supabaseUrl)
    private val authKey = JTextField(original.supabaseKey)

    init {
        defaultCloseOperation = DISPOSE_ON_CLOSE
        val body = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS); border = EmptyBorder(16, 16, 16, 16) }
        contentPane = body
        mode.selectedItem = modes.entries.first { it.value == original.tunnelMode }.key
        body.add(row("Tunnel provider", mode))
        body.add(row("Microsoft CLI (blank = auto)", executable, true))
        body.add(row("Persistent Microsoft tunnel ID", id))
        body.add(JLabel("Already created rndtransit.use? Save that ID; do not create it again."))
        val signIn = JButton("Microsoft sign-in").apply { addActionListener { saveAndSetup(true) } }
        val create = JButton("Create new Microsoft tunnel").apply { addActionListener { saveAndSetup(false) } }
        body.add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            add(JButton("Download Microsoft CLI").apply { addActionListener {
                runCatching { Desktop.getDesktop().browse(URI("https://learn.microsoft.com/en-us/azure/developer/dev-tunnels/get-started")) }
                    .onFailure { showError("Open https://aka.ms/devtunnels/docs to install devtunnel.exe.") }
            } })
            add(signIn); add(create)
        })
        body.add(row("Cloudflare executable", cloudflared, true))
        body.add(row("Cloudflare token file (named)", tokenFile, true))
        body.add(row("Public HTTPS URL (named)", address))
        body.add(row("Supabase project HTTPS URL", authUrl))
        body.add(row("Supabase publishable key", authKey))
        body.add(JTextArea("Public access requires the app's existing login. Microsoft setup creates an API-only tunnel with public relay access.\n" +
            "Install the CLI once. Sign in with the same Windows user as this launcher. Start server manages the tunnel afterward.\n" +
            "Close any manually running devtunnel host before pressing Start. Changes apply on the next Start server.").apply {
            isEditable = false; isOpaque = false; lineWrap = true; wrapStyleWord = true; border = EmptyBorder(12, 0, 12, 0)
        })
        body.add(JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
            add(JButton("Cancel").apply { addActionListener { dispose() } })
            add(JButton("Save").apply { addActionListener { runCatching { val config = settings(); config.validate(); store.save(config); dispose() }.onFailure { showError(it.message) } } })
        })
        fun refresh() {
            val selected = modes.getValue(mode.selectedItem as String)
            executable.isEnabled = selected == "microsoft"; id.isEnabled = selected == "microsoft"
            signIn.isEnabled = selected == "microsoft"; create.isEnabled = selected == "microsoft"
            cloudflared.isEnabled = selected in listOf("quick", "named")
            tokenFile.isEnabled = selected == "named"; address.isEnabled = selected == "named"
        }
        mode.addActionListener { refresh() }; refresh()
        size = Dimension(830, 580); setLocationRelativeTo(owner)
    }

    private fun settings() = original.copy(tunnelMode = modes.getValue(mode.selectedItem as String),
        devtunnel = executable.text.trim(), devTunnelId = id.text.trim(), cloudflared = cloudflared.text.trim(),
        tunnelTokenFile = tokenFile.text.trim(), publicAddress = address.text.trim(),
        supabaseUrl = authUrl.text.trim(), supabaseKey = authKey.text.trim())

    private fun saveAndSetup(signIn: Boolean) {
        runCatching {
            val config = settings()
            devTunnelExecutable(config.devtunnel); requireDevTunnelId(config.devTunnelId)
            store.save(config)
            dispose()
            setup(config, signIn)
        }.onFailure { showError(it.message) }
    }

    private fun showError(message: String?) = JOptionPane.showMessageDialog(this, message ?: "Check tunnel settings.", "Tunnel settings", JOptionPane.ERROR_MESSAGE)

    private fun row(label: String, field: JComponent, browse: Boolean = false) = JPanel(BorderLayout(10, 0)).apply {
        border = EmptyBorder(4, 0, 4, 0); maximumSize = Dimension(Int.MAX_VALUE, 36)
        add(JLabel(label).apply { preferredSize = Dimension(225, 24) }, BorderLayout.WEST)
        add(field, BorderLayout.CENTER)
        if (browse) add(JButton("Browse...").apply { addActionListener {
            val chooser = JFileChooser()
            if (chooser.showOpenDialog(this@TunnelSettings) == JFileChooser.APPROVE_OPTION) (field as JTextField).text = chooser.selectedFile.absolutePath
        } }, BorderLayout.EAST)
    }
}
