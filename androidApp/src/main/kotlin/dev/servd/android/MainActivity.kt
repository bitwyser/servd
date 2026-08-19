package dev.servd.android

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.widget.TextView
import dev.servd.core.Servd
import dev.servd.core.net.detectLanAddress

/**
 * Phase 0.5 hello screen: proves the Android app builds and runs against the shared `core`
 * (same LAN-detection code as the desktop host). Real host UI arrives in Phase 8.
 */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val lan = detectLanAddress()
        val body = buildString {
            appendLine("${Servd.NAME} v${Servd.VERSION}")
            appendLine(Servd.TAGLINE)
            appendLine()
            if (lan != null) {
                appendLine("LAN address")
                appendLine(lan.ip)
                appendLine("(${lan.interfaceName})")
            } else {
                appendLine("No LAN address found.")
                appendLine("Connect Wi-Fi or start a hotspot.")
            }
            appendLine()
            append("shared core: OK")
        }

        val view = TextView(this).apply {
            text = body
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setPadding(56, 96, 56, 56)
            setTextColor(Color.parseColor("#0F1A1F"))
            setBackgroundColor(Color.parseColor("#E9EEEF"))
        }
        setContentView(view)
    }
}
