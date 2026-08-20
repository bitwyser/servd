package dev.servd.android

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * servd's Android control screen - a thin native shell. The real UI is the served web dashboard;
 * this screen only starts/stops the hub (via the foreground service) and shows how to reach it:
 * the share URL, a scan-to-join QR, the cert fingerprint to verify, and the SSH/FTP credentials.
 */
class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var detailText: TextView
    private lateinit var qr: ImageView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var openButton: Button

    private val ink = Color.parseColor("#0F1A1F")
    private val muted = Color.parseColor("#5A6B72")
    private val accent = Color.parseColor("#1F6F5C")

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = render()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        render()
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(ServdHostService.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag") registerReceiver(stateReceiver, filter)
        }
        render()
    }

    override fun onPause() {
        super.onPause()
        runCatching { unregisterReceiver(stateReceiver) }
    }

    private fun buildUi(): View {
        val pad = dp(20)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#E9EEEF"))
            setPadding(pad, dp(36), pad, pad)
        }

        root.addView(TextView(this).apply {
            text = "servd"
            setTextColor(ink)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        })
        root.addView(TextView(this).apply {
            text = "your device, as a local-network hub"
            setTextColor(muted)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dp(2), 0, dp(20))
        })

        statusText = TextView(this).apply {
            setTextColor(ink)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        root.addView(statusText)

        qr = ImageView(this).apply {
            val size = dp(200)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                topMargin = dp(16); gravity = Gravity.CENTER_HORIZONTAL
            }
            visibility = View.GONE
        }
        root.addView(qr)

        detailText = TextView(this).apply {
            setTextColor(ink)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.MONOSPACE
            setPadding(0, dp(16), 0, dp(16))
            setTextIsSelectable(true)
        }

        startButton = button("Start hub", accent, Color.WHITE) { startHub() }
        stopButton = button("Stop hub", Color.parseColor("#B23A3A"), Color.WHITE) { ServdHostService.stop(this) }
        openButton = button("Open dashboard", Color.WHITE, ink) { openDashboard() }
        val copyButton = button("Copy share URL", Color.WHITE, ink) { copyUrl() }

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(startButton)
            addView(stopButton)
            addView(openButton)
            addView(copyButton)
        }
        root.addView(buttons)
        root.addView(detailText)

        return ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#E9EEEF"))
            addView(root)
        }
    }

    private fun render() {
        val info = ServdHost.info
        val running = ServdHost.isRunning && info != null

        startButton.visibility = if (running) View.GONE else View.VISIBLE
        stopButton.visibility = if (running) View.VISIBLE else View.GONE
        openButton.visibility = if (running) View.VISIBLE else View.GONE

        if (info == null || !running) {
            statusText.text = "Hub is stopped"
            qr.visibility = View.GONE
            detailText.text = "Start the hub, then open the dashboard here or scan the QR from another " +
                "device on the same Wi-Fi or hotspot."
            return
        }

        statusText.text = "Hub is running"
        runCatching { qr.setImageBitmap(QrBitmap.of("${info.url}/", dp(200))) }
        qr.visibility = View.VISIBLE

        val lanNote = if (info.onLan) "" else
            "\nNo Wi-Fi/hotspot detected - reachable on this device only until you connect one.\n"
        detailText.text = buildString {
            append("share    : ").append(info.url).append("/\n")
            append("admin    : ").append(info.adminUrl).append("/  (this device only)\n")
            append(lanNote)
            append("\ncert     : self-signed. Verify this SHA-256 fingerprint on first connect:\n")
            append("           ").append(info.fingerprint).append('\n')
            append("\nSSH/SFTP : port ").append(info.sshPort)
            append("   user ").append(info.username).append("   pass ").append(info.password).append('\n')
            append("FTPS     : port ").append(info.ftpPort)
            append("   user ").append(info.username).append("   pass ").append(info.password).append('\n')
            append("\nSSH and FTP start off - enable them from the admin dashboard on this device.")
        }
    }

    private fun startHub() {
        ensureNotificationPermission()
        ServdHostService.start(this)
        Toast.makeText(this, "Starting servd...", Toast.LENGTH_SHORT).show()
    }

    private fun openDashboard() {
        val url = ServdHost.info?.adminUrl ?: return
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$url/"))) }
            .onFailure { Toast.makeText(this, "No browser to open the dashboard", Toast.LENGTH_SHORT).show() }
    }

    private fun copyUrl() {
        val url = ServdHost.info?.url ?: return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("servd", "$url/"))
        Toast.makeText(this, "Share URL copied", Toast.LENGTH_SHORT).show()
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    private fun button(label: String, bg: Int, fg: Int, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(fg)
            setBackgroundColor(bg)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(10) }
            setOnClickListener { onClick() }
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
