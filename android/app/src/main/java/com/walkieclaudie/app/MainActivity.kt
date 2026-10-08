package com.walkieclaudie.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

private const val TODO_OPEN = "todo_open"

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
    private lateinit var log: ScrollView
    private lateinit var logBox: View
    private lateinit var toBottom: TextView
    private lateinit var talk: TextView
    private lateinit var rec: View
    private lateinit var recHint: TextView
    private lateinit var heard: TextView
    private lateinit var power: TextView
    private val cursor by lazy { CursorSpan(this) }
    private var shownText = ""
    private var pinned = true
    private var unseen = false // lines arrived while scrolled up
    private var lineCount = -1
    private val prefs by lazy { getSharedPreferences(WalkieService.PREFS, MODE_PRIVATE) }

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

        // Open items; tap to fold down to the title line (remembered).
        todoHead = lcdText(Ui.S).apply { alpha = 0.75f }
        todoList = lcdText().apply { setLineSpacing(0f, 1.4f); wrapByWord() }
        todo = body.lcd(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(todoHead)
            addView(todoList, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(4) })
            setOnClickListener {
                prefs.edit().putBoolean(TODO_OPEN, !prefs.getBoolean(TODO_OPEN, true)).apply()
                render()
            }
        })

        // Conversation, newest at the bottom.
        // Not selectable: a selectable TextView relays out on every cursor blink, which fights scrolling.
        talk = lcdText().apply {
            setLineSpacing(0f, 1.4f)
            gravity = Gravity.BOTTOM
            wrapByWord()
        }
        log = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(talk)
        }
        // Scrolled up: an inverted chip bottom-right jumps back down; it says "new" if lines arrived meanwhile.
        toBottom = lcdText(Ui.S, Ui.LCD).apply {
            setBackgroundColor(Ui.INK)
            setPadding(dpi(10), dpi(8), dpi(10), dpi(8))
            visibility = View.GONE
            setOnClickListener { log.smoothScrollTo(0, talk.height) }
        }
        logBox = body.lcd(android.widget.FrameLayout(this).apply {
            addView(log)
            addView(toBottom, android.widget.FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END))
        }, weight = 1f)
        // Stay pinned to the newest line while the reader is at the bottom, even when the text grows
        // or the open-items LCD above changes size; scrolling up unpins.
        log.setOnScrollChangeListener { v, _, _, _, _ ->
            pinned = !v.canScrollVertically(1)
            if (pinned) unseen = false
            showToBottom()
        }
        val follow = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> if (pinned) log.post { log.scrollTo(0, talk.height) } }
        log.addOnLayoutChangeListener(follow)
        talk.addOnLayoutChangeListener(follow)

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
        cursor.on = on
        talk.invalidate() // redraw only; no relayout
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
        logBox.visibility = todo.visibility
        rec.visibility = if (Talk.listening) View.VISIBLE else View.GONE
        recHint.text = getString(if (Talk.hold) R.string.release_to_send else R.string.press_to_send) +
            if (Talk.session > 1) " · " + getString(R.string.continued, Talk.session - 1) else ""
        heard.text = if (Talk.partial.isEmpty()) "" else "\"${Talk.partial}\""
        power.setText(if (Talk.listening) R.string.cancel else if (running) R.string.power_off else R.string.power_on)

        val shown = Talk.issues.take(4)
        val open = prefs.getBoolean(TODO_OPEN, true)
        todoHead.text = (if (open) "▼ " else "▶ ") + getString(R.string.todo, Talk.issues.size)
        todoList.text = SpannableStringBuilder().apply {
            shown.forEach { para(todoList, "· ", it) }
            if (Talk.issues.size > shown.size) para(todoList, "", getString(R.string.todo_more, Talk.issues.size - shown.size))
        }
        todoList.visibility = if (shown.isEmpty() || !open) View.GONE else View.VISIBLE

        // Each exchange (my line + replies) starts with a gap; wrapped lines hang under the text, not the prefix.
        val sb = SpannableStringBuilder()
        val gap = dpi(14)
        for (l in Talk.lines.takeLast(100)) {
            if (l.mine) sb.para(talk, getString(R.string.me),
                l.text + if (l.state != Talk.State.ANSWERED) " (${getString(l.state.label)})" else "",
                GapAbove(gap), ForegroundColorSpan(Ui.INK.alpha(0.6f)))
            else if (l.state == Talk.State.NOTE) sb.para(talk, "", "-- ${l.text} --", GapAbove(gap),
                AbsoluteSizeSpan(Ui.S.toInt(), true), ForegroundColorSpan(Ui.INK.alpha(0.55f)))
            else sb.para(talk, getString(R.string.claude), l.text)
        }
        val sys = listOfNotNull(
            getString(R.string.lost).takeIf { offline },
            Talk.statusDetail.ifEmpty { null },
        )
        for (s in sys) sb.para(talk, "", s, GapAbove(gap), AbsoluteSizeSpan(Ui.S.toInt(), true), ForegroundColorSpan(Ui.INK.alpha(0.55f)))
        sb.append("█")
        sb.setSpan(cursor, sb.length - 1, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (sb.toString() == shownText) return // polls usually change nothing here; leave the view alone
        shownText = sb.toString()
        talk.text = sb
        if (!pinned && lineCount >= 0 && Talk.lines.size > lineCount) unseen = true
        lineCount = Talk.lines.size
        showToBottom()
    }

    private fun showToBottom() {
        toBottom.visibility = if (pinned) View.GONE else View.VISIBLE
        toBottom.setText(if (unseen) R.string.new_lines else R.string.to_bottom)
    }

    /** Appends a paragraph whose wrapped lines indent past [prefix]. */
    private fun SpannableStringBuilder.para(view: TextView, prefix: String, text: String, vararg styles: Any) {
        if (isNotEmpty()) append("\n")
        val start = length
        append(prefix).append(text)
        setSpan(LeadingMarginSpan.Standard(0, view.paint.measureText(prefix).toInt()), start, length, 0)
        for (s in styles) setSpan(s, start, length, 0)
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
