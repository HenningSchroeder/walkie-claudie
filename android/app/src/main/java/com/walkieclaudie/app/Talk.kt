package com.walkieclaudie.app

/** Conversation state shared by WalkieService and MainActivity. Main thread only. */
object Talk {
    /** [state] is a string resource for my lines (sending / received / answered), 0 for replies. */
    class Line(val id: String, val mine: Boolean, val text: String, var state: Int)

    // ponytail: in memory only; history is gone when the process dies. Persist with the offline queue (step 3).
    val lines = ArrayList<Line>()
    var issues: List<String> = emptyList()
    var status = R.string.st_off
    var statusDetail = ""
    var onChange: (() -> Unit)? = null

    fun changed() = onChange?.invoke()
}
