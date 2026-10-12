package com.bookmari.walkie

import android.accessibilityservice.AccessibilityService
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Volume-down long-press = push-to-talk, volume-up long-press = monologue on/off, while the walkie is on (screen on only; Android gives
 * accessibility no keys with the screen off). Volume keys are held back until we know:
 * released within [LONG_MS] → we apply the volume step ourselves; held longer → talk until release.
 * Also feeds the button-test log.
 */
class KeyAccessibilityService : AccessibilityService() {
    private val main = Handler(Looper.getMainLooper())
    private var holding = false
    private var upKey = false
    // Volume up held: monologue on/off (the beep says "let go"); volume down held: push-to-talk.
    private val startHold = Runnable {
        holding = true
        if (upKey) WalkieService.instance?.monologue() else WalkieService.instance?.holdStart()
    }

    override fun onServiceConnected() = KeyLog.add("a11y", "service connected")

    override fun onKeyEvent(event: KeyEvent): Boolean {
        KeyLog.key("a11y", event)
        val up = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP
        if (!up && event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) return false
        val enabled = getSharedPreferences(WalkieService.PREFS, MODE_PRIVATE).getBoolean(WalkieService.VOLUME, true)
        if (!enabled || WalkieService.instance == null || inCall()) return false // in a call: normal call volume
        // ponytail: one volume key at a time; pressing both at once is treated as two separate presses.
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) { holding = false; upKey = up; main.postDelayed(startHold, LONG_MS) }
            KeyEvent.ACTION_UP -> {
                main.removeCallbacks(startHold)
                if (holding) { if (!upKey) WalkieService.instance?.holdEnd() }
                else getSystemService(AudioManager::class.java).adjustSuggestedStreamVolume(
                    if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                    AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI,
                )
                holding = false
            }
        }
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {}
    override fun onInterrupt() {}

    companion object {
        /** Shorter presses stay volume steps. Measured taps were 100–170 ms, holds 700 ms+. */
        const val LONG_MS = 400L
    }
}
