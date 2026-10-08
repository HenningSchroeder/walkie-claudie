package com.walkieclaudie.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Conversation state shared by WalkieService and MainActivity, saved to disk on every change. Main thread only. */
object Talk {
    enum class State(val label: Int) {
        SENDING(R.string.st_sending), RECEIVED(R.string.st_received), ANSWERED(R.string.st_answered), REPLY(0),
        NOTE(0) // the app's own diagnostic line
    }

    class Line(val id: String, val text: String, var state: State) {
        val mine get() = state != State.REPLY && state != State.NOTE
    }

    val lines = ArrayList<Line>()
    var issues: List<String> = emptyList()
    var status = R.string.st_off
    var statusDetail = ""
    /** Plan usage, % used; -1 = unknown. */
    var usage5h = -1
    var usage7d = -1
    /** Recording right now; [hold] = a held volume key (release sends) rather than a toggle; [partial] = words so far. */
    var listening = false
    var hold = false
    var partial = ""
    var session = 0 // recognizer sessions in this recording (shown while transmitting)
    var onChange: (() -> Unit)? = null
    private var file: File? = null

    fun load(f: File) {
        file = f
        val j = runCatching { JSONObject(f.readText()) }.getOrNull() ?: return
        val a = j.getJSONArray("lines")
        for (i in 0 until a.length()) a.getJSONObject(i).let {
            lines.add(Line(it.getString("id"), it.getString("text"), State.valueOf(it.getString("state"))))
        }
        issues = j.getJSONArray("issues").let { a -> List(a.length()) { a.getString(it) } }
    }

    fun changed() {
        save()
        refresh()
    }

    /** Redraw only, for state that isn't saved. */
    fun refresh() = onChange?.invoke()

    private fun save() {
        val f = file ?: return
        while (lines.size > 300) lines.removeAt(0)
        val j = JSONObject()
            .put("lines", JSONArray(lines.map { JSONObject().put("id", it.id).put("text", it.text).put("state", it.state.name) }))
            .put("issues", JSONArray(issues))
        val tmp = File(f.path + ".tmp")
        tmp.writeText(j.toString())
        tmp.renameTo(f)
    }
}
