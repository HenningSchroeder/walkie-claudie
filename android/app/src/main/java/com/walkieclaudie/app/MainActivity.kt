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
        logLaunch(intent)
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

    // Diagnostics for the Samsung side key: how does a side-key launch differ from an icon tap?
    private fun logLaunch(i: Intent) = KeyLog.add(
        "launch",
        "flags=0x${Integer.toHexString(i.flags)} bounds=${i.sourceBounds} from=$referrer cat=${i.categories} extras=${i.extras?.keySet()}",
    )

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        logLaunch(intent)
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
