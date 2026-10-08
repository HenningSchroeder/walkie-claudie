package com.walkieclaudie.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import org.json.JSONObject

/** One LCD menu: PC address and key, the talk-button switches, voice, button test. Changes restart a running walkie. */
class SettingsActivity : Activity() {
    private val prefs by lazy { getSharedPreferences(WalkieService.PREFS, MODE_PRIVATE) }
    private lateinit var menu: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val body = Body(this) {}
        if (WalkieService.instance != null) body.led(Ui.LED_OK)
        menu = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.lcd(ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(menu) }, weight = 1f)
        body.key(key(R.string.back) { finish() })
    }

    override fun onResume() {
        super.onResume()
        build() // the voice may have changed in VoiceActivity
    }

    private fun build() {
        menu.removeAllViews()
        val url = prefs.getString("url", "")!!
        val key = prefs.getString("key", "")!!

        title(R.string.sec_connection)
        row(R.string.pc_address, url.removePrefix("http://").ifEmpty { getString(R.string.none) }, action(R.string.paste) { paste() }) {
            edit(R.string.pc_address, url, "http://192.168.0.10:7700", InputType.TYPE_TEXT_VARIATION_URI) { setPairing(it) || saveUrl(it) }
        }
        row(R.string.key, if (key.isEmpty()) getString(R.string.none) else "*".repeat(12), action(R.string.qr) { scan() }) {
            edit(R.string.key, key, getString(R.string.key_hint), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD) { save { putString("key", it) } }
        }

        title(R.string.sec_talk)
        switch(R.string.trig_volume, R.string.trig_volume_sub, WalkieService.VOLUME, true) {
            val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            if (packageName !in enabled) {
                Toast.makeText(this, R.string.a11y_needed, Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        switch(R.string.trig_ear_single, R.string.trig_ear_single_sub, WalkieService.EAR_SINGLE, true)
        switch(R.string.trig_ear_double, 0, WalkieService.EAR_DOUBLE, false)
        switch(R.string.trig_floating, R.string.trig_floating_sub, WalkieService.FLOATING, true)

        title(R.string.voice)
        row(prefs.getString(WalkieService.TTS_VOICE, "")!!.ifEmpty { getString(R.string.voice_auto_short) },
            getString(R.string.voice_speed, prefs.getFloat(WalkieService.TTS_RATE, 1f)), action(R.string.change)) {
            startActivity(Intent(this, VoiceActivity::class.java))
        }

        title(R.string.sec_check)
        row(getString(R.string.button_test), null, action(R.string.open)) { startActivity(Intent(this, ButtonTestActivity::class.java)) }
        menu.addView(lcdText(11f).apply { text = "build ${BuildConfig.BUILD}"; alpha = 0.5f }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dpi(10) })
    }

    private fun title(label: Int) = menu.addView(lcdText(Ui.S).apply { setText(label); alpha = 0.7f },
        LinearLayout.LayoutParams(-2, -2).apply { topMargin = if (menu.childCount == 0) 0 else dpi(12); bottomMargin = dpi(2) })

    private fun row(label: Int, sub: String?, right: TextView, onClick: () -> Unit) = row(getString(label), sub, right, onClick)

    private fun row(label: String, sub: String?, right: TextView, onClick: () -> Unit) {
        val left = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(lcdText().apply { text = label })
            if (sub != null) addView(lcdText(11f).apply { text = sub; alpha = 0.65f }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dpi(3) })
        }
        menu.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dpi(44) // comfortable tap target
            setPadding(0, dpi(7), 0, dpi(7))
            background = DottedLine(context)
            setOnClickListener { onClick() }
            addView(left, LinearLayout.LayoutParams(0, -2, 1f))
            addView(right, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dpi(8) })
        })
    }

    private fun action(label: Int, onClick: (() -> Unit)? = null) = lcdText().apply {
        setText(label)
        if (onClick != null) {
            setPadding(dpi(8), dpi(8), 0, dpi(8)) // bigger tap target than the word
            setOnClickListener { onClick() }
        }
    }

    private fun switch(label: Int, sub: Int, pref: String, default: Boolean, onEnable: () -> Unit = {}) {
        val on = prefs.getBoolean(pref, default)
        val chip = lcdText(13f).apply {
            text = if (on) "ON" else "OFF"
            setPadding(dpi(5), dpi(2), dpi(5), dpi(2))
            if (on) { setBackgroundColor(Ui.INK); setTextColor(Ui.LCD) }
            else { background = android.graphics.drawable.GradientDrawable().apply { setStroke(dpi(1), Ui.INK) }; alpha = 0.6f }
        }
        row(getString(label), if (sub == 0) null else getString(sub), chip) {
            save { putBoolean(pref, !on) }
            if (!on) onEnable()
        }
    }

    /** Pairing text ({"url":…,"key":…}, same as the QR) fills both fields. */
    private fun setPairing(text: String): Boolean {
        val j = text.trim().takeIf { it.startsWith("{") }?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return false
        save { putString("url", j.optString("url")).putString("key", j.optString("key")) }
        return true
    }

    private fun paste() {
        val text = getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()?.trim()
        if (text.isNullOrEmpty()) return Toast.makeText(this, R.string.clipboard_empty, Toast.LENGTH_SHORT).show()
        setPairing(text) || saveUrl(text)
    }

    private fun scan() = GmsBarcodeScanning.getClient(this).startScan().addOnSuccessListener { code ->
        if (!setPairing(code.rawValue.orEmpty())) Toast.makeText(this, R.string.bad_qr, Toast.LENGTH_LONG).show()
    }

    private fun saveUrl(url: String) = save { putString("url", if (url.isEmpty() || "://" in url) url else "http://$url") }

    private fun edit(label: Int, value: String, hint: String, type: Int, onSave: (String) -> Unit) {
        val field = EditText(this).apply { setText(value); this.hint = hint; inputType = type; setSingleLine() }
        AlertDialog.Builder(this)
            .setTitle(label)
            .setView(LinearLayout(this).apply { setPadding(dpi(20), dpi(8), dpi(20), 0); addView(field, LinearLayout.LayoutParams(-1, -2)) })
            .setPositiveButton(android.R.string.ok) { _, _ -> onSave(field.text.toString().trim()) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Store, redraw, and restart a running walkie so it picks the change up. */
    private fun save(change: android.content.SharedPreferences.Editor.() -> Unit): Boolean {
        prefs.edit().apply(change).apply()
        build()
        if (WalkieService.instance != null) startWalkie()
        return true
    }
}
