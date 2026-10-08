package com.walkieclaudie.app

import android.app.Activity
import android.app.KeyguardManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager

/** Target for Samsung "Side button > Double press > Open app": log, beep, vanish. */
class SideButtonActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        val locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked
        val screenOn = getSystemService(PowerManager::class.java).isInteractive
        KeyLog.add("side", "launched locked=$locked screenOn=$screenOn")
        val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
        tone.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        Handler(Looper.getMainLooper()).postDelayed({ tone.release() }, 400)
        finish()
    }
}
