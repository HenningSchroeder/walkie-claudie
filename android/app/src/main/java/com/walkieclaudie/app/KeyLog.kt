package com.walkieclaudie.app

import android.app.Application
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.PowerManager
import android.util.Log
import android.view.KeyEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        KeyLog.ctx = this
    }
}

/** In-process log shared by the activity, the accessibility service and the media session. Main thread only. */
object KeyLog {
    lateinit var ctx: Context
    private val lines = ArrayDeque<String>()
    private val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    var onChange: (() -> Unit)? = null

    /** Screen state and which headset (if any) is the audio output, at the moment of logging. */
    private fun state(): String {
        val screen = if (ctx.getSystemService(PowerManager::class.java).isInteractive) "on" else "OFF"
        val headset = ctx.getSystemService(AudioManager::class.java).getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .firstOrNull { it.type in HEADSETS }?.productName ?: "-"
        return "screen=$screen headset=$headset"
    }

    fun add(source: String, msg: String) {
        val line = "${time.format(Date())} [$source] $msg\n    ${state()}"
        Log.i("WalkieKeys", line)
        lines.addFirst(line)
        if (lines.size > 300) lines.removeLast()
        onChange?.invoke()
    }

    fun key(source: String, e: KeyEvent) {
        val code = KeyEvent.keyCodeToString(e.keyCode).removePrefix("KEYCODE_")
        val action = if (e.action == KeyEvent.ACTION_DOWN) "▼" else "▲ ${e.eventTime - e.downTime}ms"
        val repeat = if (e.repeatCount > 0) " rep=${e.repeatCount}" else ""
        val long = if (e.isLongPress) " LONG" else ""
        add(source, "$code $action$repeat$long (${e.device?.name})")
    }

    fun text() = lines.joinToString("\n")

    fun clear() {
        lines.clear()
        onChange?.invoke()
    }

    private val HEADSETS = setOf(
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_USB_HEADSET,
    )
}
