package com.walkieclaudie.app

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Button test screen: shows every key event the app can see, from any source. */
class ButtonTestActivity : Activity() {
    private lateinit var log: TextView
    private lateinit var tts: android.speech.tts.TextToSpeech

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)

        val rows = List(2) { LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL } }
        fun button(row: Int, label: Int, onClick: () -> Unit) = rows[row].addView(
            Button(this).apply { setText(label); setOnClickListener { onClick() } },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        button(0, R.string.bg_start) { startForegroundService(Intent(this, MediaButtonService::class.java)) }
        button(0, R.string.bg_stop) { stopService(Intent(this, MediaButtonService::class.java)) }
        button(0, R.string.a11y_open) { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        button(1, R.string.app_info) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
        button(1, R.string.copy) {
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("keys", KeyLog.text()))
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
        }
        button(1, R.string.clear) { KeyLog.clear() }

        log = TextView(this).apply { textSize = 17f; setTextIsSelectable(true) }
        // Same voice choice as the walkie, shown so a screenshot tells which engine/voice speaks.
        val ttsLine = TextView(this).apply { textSize = 13f; setTextIsSelectable(true); text = "TTS …" }
        tts = openTts(this) { ttsLine.text = it }
        setContentView(ScrollView(this).apply {
            padForSystemBars()
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(ttsLine)
                addView(TextView(context).apply { setText(R.string.help); textSize = 13f })
                rows.forEach { addView(it) }
                addView(log)
            })
        })
        KeyLog.add("app", "button test opened")
    }

    override fun onDestroy() {
        tts.shutdown()
        super.onDestroy()
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
