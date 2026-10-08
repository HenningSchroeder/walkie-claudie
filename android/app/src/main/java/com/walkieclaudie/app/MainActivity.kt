package com.walkieclaudie.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import org.json.JSONObject

/** Connection settings, status, open items and conversation. Talking happens via WalkieService. */
class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var issues: TextView
    private lateinit var talk: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS), 0)
        val prefs = getSharedPreferences(WalkieService.PREFS, MODE_PRIVATE)

        val url = EditText(this).apply {
            hint = "http://192.168.0.10:7700"
            setText(prefs.getString("url", ""))
            inputType = InputType.TYPE_TEXT_VARIATION_URI
        }
        val key = EditText(this).apply {
            hint = getString(R.string.key_hint)
            setText(prefs.getString("key", ""))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        // Pasting the pairing text ({"url":…,"key":…}, same as the QR) into the address box fills both fields.
        url.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(e: android.text.Editable) {
                val j = e.toString().trim().takeIf { it.startsWith("{") }?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return
                url.setText(j.optString("url"))
                key.setText(j.optString("key"))
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        val row = LinearLayout(this)
        fun button(label: Int, onClick: () -> Unit) = row.addView(
            Button(this).apply { setText(label); setOnClickListener { onClick() } },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        fun start() {
            prefs.edit()
                .putString("url", url.text.toString().trim())
                .putString("key", key.text.toString().trim())
                .apply()
            if (!Settings.canDrawOverlays(this))
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            else {
                stopService(Intent(this, WalkieService::class.java)) // restart picks up new settings
                startForegroundService(Intent(this, WalkieService::class.java))
            }
        }
        button(R.string.scan) {
            GmsBarcodeScanning.getClient(this).startScan().addOnSuccessListener { code ->
                val j = runCatching { JSONObject(code.rawValue!!) }.getOrNull()
                if (j == null) Toast.makeText(this, R.string.bad_qr, Toast.LENGTH_LONG).show()
                else { url.setText(j.getString("url")); key.setText(j.getString("key")); start() }
            }
        }
        button(R.string.start) { start() }
        button(R.string.bg_stop) { stopService(Intent(this, WalkieService::class.java)) }

        // Trigger switches; a running walkie restarts to pick up the change.
        val triggers = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun trigger(label: Int, pref: String, default: Boolean, onEnable: () -> Unit = {}) = triggers.addView(Switch(this).apply {
            setText(label)
            isChecked = prefs.getBoolean(pref, default)
            setOnCheckedChangeListener { _, on ->
                prefs.edit().putBoolean(pref, on).apply()
                if (on) onEnable()
                if (WalkieService.instance != null) start()
            }
        })
        trigger(R.string.trig_floating, WalkieService.FLOATING, true)
        trigger(R.string.trig_volume, WalkieService.VOLUME, true) {
            val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            if (packageName !in enabled) {
                Toast.makeText(this, R.string.a11y_needed, Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        trigger(R.string.trig_ear_single, WalkieService.EAR_SINGLE, true)
        trigger(R.string.trig_ear_double, WalkieService.EAR_DOUBLE, false)
        triggers.addView(TextView(this).apply { setText(R.string.ear_hint); textSize = 13f; alpha = 0.7f })
        button(R.string.button_test) { startActivity(Intent(this, ButtonTestActivity::class.java)) }

        status = TextView(this).apply { textSize = 16f }
        issues = TextView(this).apply { textSize = 16f; setPadding(0, 16, 0, 16) }
        talk = TextView(this).apply { textSize = 17f; setTextIsSelectable(true) }
        val talkButton = Button(this).apply {
            setText(R.string.talk)
            textSize = 22f
            minHeight = (88 * resources.displayMetrics.density).toInt()
            setOnClickListener {
                startForegroundService(Intent(this@MainActivity, WalkieService::class.java).setAction(WalkieService.TALK))
            }
        }

        val side = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(status)
            addView(url)
            addView(key)
            addView(row)
            addView(triggers)
            addView(issues)
        }
        val conversation = ScrollView(this).apply { addView(talk) }
        // Unfolded (wide): settings and open items left, conversation right. Folded: stacked.
        val wide = resources.configuration.screenWidthDp >= 600
        val content = LinearLayout(this).apply {
            orientation = if (wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            if (wide) {
                addView(side, LinearLayout.LayoutParams(0, -1, 1f))
                addView(conversation, LinearLayout.LayoutParams(0, -1, 1f).apply { marginStart = 32 })
            } else {
                addView(side)
                addView(conversation, LinearLayout.LayoutParams(-1, 0, 1f))
            }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            padForSystemBars()
            addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(talkButton, LinearLayout.LayoutParams(-1, -2))
        })
    }

    override fun onResume() {
        super.onResume()
        Talk.onChange = ::render
        render()
    }

    override fun onPause() {
        super.onPause()
        Talk.onChange = null
    }

    private fun render() {
        status.text = listOf(getString(Talk.status), Talk.statusDetail, Talk.usage).filter { it.isNotEmpty() }.joinToString(" · ")
        issues.text = if (Talk.issues.isEmpty()) getString(R.string.no_issues)
            else getString(R.string.issues) + "\n" + Talk.issues.joinToString("\n") { "• $it" }
        talk.text = Talk.lines.asReversed().joinToString("\n\n") {
            if (it.mine) "🗣 ${it.text}  (${getString(it.state.label)})" else "🤖 ${it.text}"
        }
    }
}

/** targetSdk 35+ draws edge-to-edge; keep content clear of the status/nav bars and the keyboard. */
fun View.padForSystemBars() = setOnApplyWindowInsetsListener { v, insets ->
    val b = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
    v.setPadding(32 + b.left, 32 + b.top, 32 + b.right, 32 + b.bottom)
    insets
}
