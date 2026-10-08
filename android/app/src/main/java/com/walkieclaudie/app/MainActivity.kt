package com.walkieclaudie.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Status, usage, open items and conversation on LCDs; [settings][on/off][talk] keys. Talking happens via WalkieService. */
class MainActivity : Activity() {
    private lateinit var body: Body
    private lateinit var state: TextView
    private lateinit var antenna: Antenna
    private lateinit var u5: TextView
    private lateinit var u7: TextView
    private lateinit var c5: Cells
    private lateinit var c7: Cells
    private lateinit var todo: View
    private lateinit var todoHead: TextView
    private lateinit var todoList: TextView
    private lateinit var log: View
    private lateinit var talk: TextView
    private lateinit var rec: View
    private lateinit var recHint: TextView
    private lateinit var heard: TextView
    private lateinit var power: TextView
    private val cursor by lazy { CursorSpan(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS), 0)
        body = Body(this, ::blink)

        // Top LCD: channel, connection, usage meters.
        state = lcdText(Ui.S).apply { alpha = 0.75f }
        antenna = Antenna(this)
        val head = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(lcdText().apply { setText(R.string.channel) }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(state)
            addView(antenna, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dpi(4) })
        }
        fun meter(): Pair<TextView, Cells> = lcdText() to Cells(this)
        val (t5, m5) = meter(); val (t7, m7) = meter()
        u5 = t5; u7 = t7; c5 = m5; c7 = m7
        val meters = LinearLayout(this).apply {
            listOf(u5 to c5, u7 to c7).forEachIndexed { i, (t, c) ->
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(t)
                    addView(c, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(3) })
                }, LinearLayout.LayoutParams(0, -2, 1f).apply { if (i > 0) marginStart = dpi(14) })
            }
        }
        body.lcd(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(head)
            addView(meters, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(4) })
        })

        // Open items.
        todoHead = lcdText(Ui.S).apply { alpha = 0.75f }
        todoList = lcdText().apply { setLineSpacing(0f, 1.35f) }
        todo = body.lcd(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(todoHead)
            addView(todoList, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(4) })
        })

        // Conversation, newest at the bottom.
        talk = lcdText().apply {
            setLineSpacing(0f, 1.3f)
            gravity = Gravity.BOTTOM
            setTextIsSelectable(true)
        }
        log = body.lcd(ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(talk)
        }, weight = 1f)

        // Transmitting: one inverted LCD in place of the two above.
        recHint = lcdText(Ui.S, Ui.LCD).apply { alpha = 0.8f }
        heard = lcdText(color = Ui.LCD).apply { gravity = Gravity.CENTER }
        rec = body.lcd(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(lcdText(Ui.L, Ui.LCD).apply { setText(R.string.transmitting); letterSpacing = 0.04f }, LinearLayout.LayoutParams(-2, -2))
            addView(recHint, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dpi(10) })
            addView(heard, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dpi(18) })
        }, inverted = true, weight = 1f)

        body.key(key(R.string.settings) { startActivity(Intent(this, SettingsActivity::class.java)) })
        power = key(R.string.power_off) {
            when {
                Talk.listening -> startService(Intent(this, WalkieService::class.java).setAction(WalkieService.CANCEL))
                WalkieService.instance != null -> stopService(Intent(this, WalkieService::class.java))
                else -> startWalkie()
            }
        }
        body.key(power)
        body.key(key(R.string.talk) {
            startForegroundService(Intent(this, WalkieService::class.java).setAction(WalkieService.TALK))
        }.apply { setTextColor(Color.WHITE) })
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

    private fun blink(on: Boolean) {
        if (Talk.status == R.string.st_connecting) antenna.bars = if (on) 1 else 0
        // Re-setting the span is what makes a selectable TextView redraw that spot.
        val t = talk.text as? Spannable ?: return
        val start = t.getSpanStart(cursor).takeIf { it >= 0 } ?: return
        val end = t.getSpanEnd(cursor)
        cursor.on = on
        t.removeSpan(cursor)
        t.setSpan(cursor, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun render() {
        val running = WalkieService.instance != null
        val offline = Talk.status == R.string.st_offline
        state.setText(Talk.status)
        antenna.bars = if (Talk.status == R.string.st_connected) 4 else if (Talk.status == R.string.st_connecting) 1 else 0
        when {
            Talk.listening -> body.led(Ui.LED_REC)
            offline -> body.led(Ui.LED_REC, blink = true)
            running -> body.led(Ui.LED_OK)
            else -> body.led(Ui.KEY)
        }

        fun meter(t: TextView, c: Cells, label: Int, pct: Int) {
            t.text = SpannableStringBuilder(getString(label)).apply {
                setSpan(AbsoluteSizeSpan(Ui.S.toInt(), true), 0, length, 0)
                setSpan(ForegroundColorSpan(Ui.INK.alpha(0.75f)), 0, length, 0)
                append(" ").append(if (pct < 0) "--" else "$pct%")
            }
            c.lit = if (pct < 0) 0 else (pct + 5) / 10
            c.visibility = if (Talk.listening) View.GONE else View.VISIBLE
        }
        meter(u5, c5, R.string.usage_5h, Talk.usage5h)
        meter(u7, c7, R.string.usage_7d, Talk.usage7d)

        todo.visibility = if (Talk.listening) View.GONE else View.VISIBLE
        log.visibility = todo.visibility
        rec.visibility = if (Talk.listening) View.VISIBLE else View.GONE
        recHint.setText(if (Talk.hold) R.string.release_to_send else R.string.press_to_send)
        heard.text = if (Talk.partial.isEmpty()) "" else "\"${Talk.partial}\""
        power.setText(if (Talk.listening) R.string.cancel else if (running) R.string.power_off else R.string.power_on)

        val shown = Talk.issues.take(4)
        todoHead.text = getString(R.string.todo, Talk.issues.size)
        todoList.text = (shown.map { "· $it" } +
            if (Talk.issues.size > shown.size) listOf(getString(R.string.todo_more, Talk.issues.size - shown.size)) else emptyList())
            .joinToString("\n")
        todoList.visibility = if (shown.isEmpty()) View.GONE else View.VISIBLE

        val sb = SpannableStringBuilder()
        for (l in Talk.lines.takeLast(100)) {
            if (sb.isNotEmpty()) sb.append("\n")
            val start = sb.length
            if (l.mine) {
                sb.append(getString(R.string.me)).append(l.text)
                if (l.state != Talk.State.ANSWERED) sb.append(" (").append(getString(l.state.label)).append(")")
                sb.setSpan(ForegroundColorSpan(Ui.INK.alpha(0.6f)), start, sb.length, 0)
            } else sb.append(getString(R.string.claude)).append(l.text)
        }
        val sys = listOfNotNull(
            getString(R.string.lost).takeIf { offline },
            Talk.statusDetail.ifEmpty { null },
        )
        for (s in sys) {
            if (sb.isNotEmpty()) sb.append("\n")
            val start = sb.length
            sb.append(s)
            sb.setSpan(AbsoluteSizeSpan(Ui.S.toInt(), true), start, sb.length, 0)
            sb.setSpan(ForegroundColorSpan(Ui.INK.alpha(0.55f)), start, sb.length, 0)
        }
        sb.append("█")
        sb.setSpan(cursor, sb.length - 1, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        talk.text = sb
        (log as ScrollView).post { log.scrollTo(0, talk.height) }
    }
}

/** (Re)starts the walkie with the stored settings, asking for the overlay permission first if the floating button needs it. */
fun Activity.startWalkie() {
    val prefs = getSharedPreferences(WalkieService.PREFS, Activity.MODE_PRIVATE)
    if (prefs.getBoolean(WalkieService.FLOATING, true) && !Settings.canDrawOverlays(this))
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
    else {
        stopService(Intent(this, WalkieService::class.java)) // restart picks up new settings
        startForegroundService(Intent(this, WalkieService::class.java))
    }
}

/** targetSdk 35+ draws edge-to-edge; keep content clear of the status/nav bars and the keyboard. */
fun View.padForSystemBars() = setOnApplyWindowInsetsListener { v, insets ->
    val b = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
    v.setPadding(32 + b.left, 32 + b.top, 32 + b.right, 32 + b.bottom)
    insets
}
