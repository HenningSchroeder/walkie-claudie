package com.bookmari.walkie

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
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
        val body = Body(this) {}
        if (WalkieService.instance != null) body.led(Ui.LED_OK)

        val menu = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        menu.menuRow(getString(R.string.earphone_buttons), null, LinearLayout(this).apply {
            addView(lcdAction(getString(R.string.bg_start)) { startForegroundService(Intent(context, MediaButtonService::class.java)) })
            addView(lcdAction(getString(R.string.bg_stop)) { stopService(Intent(context, MediaButtonService::class.java)) })
        }, null)
        val a11y = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        menu.menuRow(getString(R.string.a11y_open), null, lcdAction(getString(R.string.open)), a11y)
        menu.menuRow(getString(R.string.app_info), null, lcdAction(getString(R.string.open))) { openAppInfo() }
        body.lcd(menu)

        log = lcdText(Ui.S)
        // Same voice choice as the walkie, shown so a screenshot tells which engine/voice speaks.
        val ttsLine = lcdText(11f).apply { alpha = 0.5f; text = "TTS …" }
        tts = openTts(this) { ttsLine.text = "$it · build ${BuildConfig.BUILD}" }
        body.lcd(ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(lcdText(Ui.S).apply { setText(R.string.help); alpha = 0.7f; wrapByWord() })
                addView(ttsLine, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(8) })
                addView(log, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(10) })
            })
        }, weight = 1f)

        body.key(key(R.string.back) { finish() })
        body.key(key(R.string.copy) {
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("keys", KeyLog.text()))
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
        })
        body.key(key(R.string.clear) { KeyLog.clear() })
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
