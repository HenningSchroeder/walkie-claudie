package com.walkieclaudie.app

import android.util.Log
import android.view.KeyEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** In-process log shared by the activity, the accessibility service and the media session. Main thread only. */
object KeyLog {
    private val lines = ArrayDeque<String>()
    private val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    var onChange: (() -> Unit)? = null

    fun add(source: String, msg: String) {
        val line = "${time.format(Date())} [$source] $msg"
        Log.i("WalkieKeys", line)
        lines.addFirst(line)
        if (lines.size > 500) lines.removeLast()
        onChange?.invoke()
    }

    fun key(source: String, e: KeyEvent) {
        val action = if (e.action == KeyEvent.ACTION_DOWN) "DOWN" else "UP"
        val held = if (e.action == KeyEvent.ACTION_UP) " held=${e.eventTime - e.downTime}ms" else ""
        val long = if (e.isLongPress) " LONG" else ""
        add(source, "${KeyEvent.keyCodeToString(e.keyCode)} $action rep=${e.repeatCount}$held$long dev=${e.device?.name}")
    }

    fun text() = lines.joinToString("\n")

    fun clear() {
        lines.clear()
        onChange?.invoke()
    }
}
