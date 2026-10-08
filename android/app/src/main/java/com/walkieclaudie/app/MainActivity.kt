package com.walkieclaudie.app

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Button test screen: shows every key event the app can see, from any source. */
class MainActivity : Activity() {
    private lateinit var log: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun button(label: Int, onClick: () -> Unit) =
            buttons.addView(Button(this).apply { setText(label); setOnClickListener { onClick() } })
        button(R.string.bg_start) { startForegroundService(Intent(this, MediaButtonService::class.java)) }
        button(R.string.bg_stop) { stopService(Intent(this, MediaButtonService::class.java)) }
        button(R.string.a11y_open) { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        button(R.string.copy) {
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("keys", KeyLog.text()))
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
        }
        button(R.string.clear) { KeyLog.clear() }

        log = TextView(this).apply { setTextIsSelectable(true); typeface = android.graphics.Typeface.MONOSPACE }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            addView(TextView(context).apply { setText(R.string.help) })
            addView(android.widget.HorizontalScrollView(context).apply { addView(buttons) })
            addView(ScrollView(context).apply { addView(log) })
        })
        KeyLog.add("app", "MainActivity created")
    }

    override fun onResume() {
        super.onResume()
        KeyLog.onChange = { log.text = KeyLog.text() }
        log.text = KeyLog.text()
    }

    override fun onPause() {
        super.onPause()
        KeyLog.onChange = null
    }

    // Log, then let the system handle it as usual (volume still changes, etc.).
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        KeyLog.key("activity", event)
        return super.dispatchKeyEvent(event)
    }
}
