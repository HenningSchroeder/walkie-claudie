package com.walkieclaudie.app

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.media.AudioManager
import android.media.ToneGenerator
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread
import kotlin.math.abs

/**
 * The walkie-talkie: floating push-to-talk button, speech recognition, sending to the PC
 * channel, and long-polling for replies that are spoken aloud.
 * Start with action [TALK] to toggle listening (used by the in-app button), [CANCEL] to drop a recording.
 */
class WalkieService : Service() {
    companion object {
        const val TALK = "talk"
        const val CANCEL = "cancel"
        const val PREFS = "walkie"
        // Trigger switches (all on by default except earphone double-press).
        const val FLOATING = "trig_floating"
        const val VOLUME = "trig_volume"
        const val EAR_SINGLE = "trig_ear_single"
        const val EAR_DOUBLE = "trig_ear_double"
        // Voice: engine package ("" = phone default), voice name ("" = best), speech rate.
        const val TTS_ENGINE = "tts_engine"
        const val TTS_VOICE = "tts_voice"
        const val TTS_RATE = "tts_rate"
        const val TTS_VOICE_LABEL = "tts_voice_label" // "Voice 3 · online", for the settings line
        const val CHIMES_MUTED = "chimes_muted" // we muted the notification stream; undo on next start if the app died

        /** The running service, for the accessibility service's volume-key hold. Main thread only. */
        var instance: WalkieService? = null
    }

    private val main = Handler(Looper.getMainLooper())
    private lateinit var prefs: SharedPreferences
    private lateinit var baseUrl: String
    private lateinit var key: String
    @Volatile private var running = true
    private val outbox = LinkedBlockingQueue<Talk.Line>()

    private lateinit var tts: TextToSpeech
    private lateinit var tone: ToneGenerator
    private var recognizer: SpeechRecognizer? = null
    private lateinit var wm: WindowManager
    private lateinit var button: TextView
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("walkie", getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW))
        val notification = Notification.Builder(this, "walkie")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentText(getString(R.string.notif_walkie))
            .setContentIntent(android.app.PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), android.app.PendingIntent.FLAG_IMMUTABLE))
            .build()
        startForeground(2, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        baseUrl = Channels.url(this)
        key = prefs.getString("key", "")!!
        unmuteChimes() // left muted if the app died mid-recording
        tone = ToneGenerator(AudioManager.STREAM_MUSIC, 90)
        tts = openTts(this) {
            if (prefs.getBoolean(Channels.ANNOUNCE, false)) { // just switched channel
                prefs.edit().remove(Channels.ANNOUNCE).apply()
                tts.speak(Channels.spoken(this), TextToSpeech.QUEUE_ADD, null, "channel")
            }
        }
        setStatus(R.string.st_connecting)
        if (prefs.getBoolean(FLOATING, true) && Settings.canDrawOverlays(this)) addButton() // the in-app talk button works regardless
        addEarphone()
        instance = this
        // A call that starts mid-reply or mid-recording wins: stop both.
        if (android.os.Build.VERSION.SDK_INT >= 31) getSystemService(AudioManager::class.java)
            .addOnModeChangedListener(mainExecutor, modeListener)
        Talk.lines.filter { it.state == Talk.State.SENDING }.forEach(outbox::put) // unsent from last time
        thread(isDaemon = true, name = "walkie-send") { sendLoop() }
        thread(isDaemon = true, name = "walkie-poll") { pollLoop() }
        thread(isDaemon = true, name = "walkie-scan") { Channels.scan(this); main.post { Talk.refresh() } } // names for the header
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            TALK -> toggleListening()
            CANCEL -> { recognizer?.cancel(); endListening() }
        }
        return START_NOT_STICKY
    }

    // lazy: the listener type only exists on API 31+, so build it only behind the version checks.
    private val modeListener by lazy {
        AudioManager.OnModeChangedListener { if (inCall()) { tts.stop(); recognizer?.cancel(); endListening() } }
    }

    override fun onDestroy() {
        if (android.os.Build.VERSION.SDK_INT >= 31) getSystemService(AudioManager::class.java)
            .removeOnModeChangedListener(modeListener)
        instance = null
        running = false
        session?.release()
        recognizer?.destroy()
        unmuteChimes()
        if (::button.isInitialized) wm.removeView(button)
        tts.shutdown()
        tone.release()
        setStatus(R.string.st_off)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    // --- floating button ---

    @SuppressLint("ClickableViewAccessibility")
    private fun addButton() {
        wm = getSystemService(WindowManager::class.java)
        val lp = WindowManager.LayoutParams(
            dpi(64), dpi(50), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt("x", 0)
            y = prefs.getInt("y", 600)
        }
        button = key(R.string.talk) {}.apply { textSize = 12f }
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
        button.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; startX = lp.x; startY = lp.y; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - downX).toInt(); val dy = (e.rawY - downY).toInt()
                    if (abs(dx) + abs(dy) > 24) moved = true
                    if (moved) { lp.x = startX + dx; lp.y = startY + dy; wm.updateViewLayout(v, lp) }
                }
                MotionEvent.ACTION_UP -> when {
                    moved -> prefs.edit().putInt("x", lp.x).putInt("y", lp.y).apply()
                    e.eventTime - e.downTime > 600 && session != null -> { // long-press: earphone button back to the walkie
                        playSilence()
                        tone.startTone(ToneGenerator.TONE_PROP_ACK, 200)
                    }
                    else -> toggleListening()
                }
            }
            true
        }
        wm.addView(button, lp)
    }

    private fun setListening(on: Boolean) {
        Talk.listening = on
        if (!on) { Talk.hold = false; Talk.partial = "" }
        Talk.refresh()
        if (::button.isInitialized) (button.background as KeyDrawable).face = if (on) Ui.LED_REC else Ui.KEY
    }

    // --- earphone buttons ---

    /** Earphones send no hold time (measured on Bluetooth earbuds), so a press toggles talking. */
    private fun addEarphone() {
        val keys = buildSet {
            if (prefs.getBoolean(EAR_SINGLE, true)) addAll(listOf(
                KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE,
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK,
            ))
            if (prefs.getBoolean(EAR_DOUBLE, false)) add(KeyEvent.KEYCODE_MEDIA_NEXT)
        }
        if (keys.isEmpty()) return
        session = MediaSession(this, "walkie").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onMediaButtonEvent(intent: Intent): Boolean {
                    @Suppress("DEPRECATION")
                    val e = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return false
                    if (e.keyCode !in keys || inCall()) return false // calls keep answer/hang-up
                    if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0) toggleListening()
                    return true
                }
            })
            setPlaybackState(
                PlaybackState.Builder()
                    .setActions(PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT)
                    .setState(PlaybackState.STATE_PLAYING, 0, 1f)
                    .build()
            )
            isActive = true
        }
        // Taken once at start. A music/podcast app that plays afterwards keeps the earphone button
        // (even paused; user's call 10/8) until the user takes it back: long-press the floating button.
        playSilence()
    }

    // --- speech in ---

    // A recording spans several recognizer sessions: the recognizer ends a session on its own when the
    // speaker pauses, so until the user ends it (release / press again) we keep what it heard and open a
    // fresh recognizer shortly after. [heard] is the text so far; [finishing] = the user ended it;
    // [errors] = recognizer error codes this recording, written to the conversation when it fails.
    private var heard = ""
    private var finishing = false
    private val errors = ArrayList<Int>()
    private var sessions = 0
    private var failures = 0 // in a row, without the recognizer getting ready
    private val maxRecording = Runnable { finishListening() } // a toggle someone forgot to press again

    // Google's recognizer chimes (usage NOTIFICATION_EVENT → notification stream, Galaxy phone 10/8) whenever a
    // session opens or closes, so a continued recording chimed at every pause, and the chimes doubled our own
    // beeps (and may play on the phone speaker too, being notifications). The notification stream is muted for
    // the whole recording and a moment after, so only our beeps play (media stream: the earphones). Music and
    // podcasts are on another stream. Already muted by the user: left alone. Muted when the app died: undone on start.
    private val audio by lazy { getSystemService(AudioManager::class.java) }
    private val unmuteLater = Runnable { unmuteChimes() }

    private fun muteChimes() {
        main.removeCallbacks(unmuteLater)
        if (prefs.getBoolean(CHIMES_MUTED, false) || audio.isStreamMute(AudioManager.STREAM_NOTIFICATION)) return
        if (runCatching { audio.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_MUTE, 0) }.isSuccess)
            prefs.edit().putBoolean(CHIMES_MUTED, true).apply()
    }

    private fun unmuteChimes() {
        main.removeCallbacks(unmuteLater)
        if (!prefs.getBoolean(CHIMES_MUTED, false)) return
        runCatching { audio.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0) }
        prefs.edit().putBoolean(CHIMES_MUTED, false).apply()
    }

    private fun toggleListening() {
        if (recognizer != null || Talk.listening) return finishListening()
        if (inCall()) return // the call owns the mic and the buttons
        tts.stop() // talking over a reply interrupts it
        heard = ""
        finishing = false
        errors.clear()
        sessions = 0
        failures = 0
        main.postDelayed(maxRecording, 120_000)
        setListening(true)
        muteChimes()
        session()
    }

    /** One recognizer session; a new recognizer each time (reusing one right after it ended failed on a real phone). */
    private fun session() {
        val r = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer = r
        val first = sessions++ == 0
        Talk.session = sessions
        Talk.refresh()
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                failures = 0
                if (first) {
                    tone.startTone(ToneGenerator.TONE_PROP_BEEP, 120) // the next sessions stay silent
                }
            }
            override fun onResults(results: Bundle) {
                if (recognizer !== r) return
                val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!text.isNullOrBlank()) heard = "$heard $text".trim()
                if (finishing) doneListening() else next(r)
            }
            override fun onError(error: Int) {
                if (recognizer !== r) return
                errors.add(error)
                val pause = error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || error == SpeechRecognizer.ERROR_NO_MATCH
                // Busy/client/network errors right after a session (a real phone's recognizer closes slowly)
                // are retried with a growing pause; permission and language errors can't get better.
                val fatal = error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ||
                    error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED || error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE
                when {
                    finishing || fatal -> doneListening() // anything heard so far still goes out
                    pause -> next(r)
                    ++failures <= 5 -> next(r, 300L * failures)
                    else -> doneListening()
                }
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
                Talk.partial = "$heard $text".trim()
                Talk.refresh()
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true))
    }

    /** The recognizer closed a session on a pause: drop it and open a fresh one shortly. */
    private fun next(r: SpeechRecognizer, delayMs: Long = 300) {
        r.destroy()
        recognizer = null
        main.postDelayed({ if (Talk.listening && recognizer == null && !finishing) session() }, delayMs)
    }

    /** The user ended the recording: the open session's result arrives in onResults/onError, then it sends. */
    private fun finishListening() {
        if (!Talk.listening) return
        finishing = true
        val r = recognizer ?: return doneListening() // between sessions: nothing pending
        r.stopListening()
    }

    private fun doneListening() {
        val text = heard
        val failed = errors.filter { it != SpeechRecognizer.ERROR_SPEECH_TIMEOUT && it != SpeechRecognizer.ERROR_NO_MATCH }
        endListening()
        // ponytail: diagnostic line while pause-continuation is being tuned on real phones; drop once it's stable.
        if (failed.isNotEmpty() || sessions > 1 || text.isEmpty() && errors.isNotEmpty())
            note(getString(R.string.err_speech_note, sessions, errors.joinToString(",").ifEmpty { "-" }))
        if (text.isEmpty()) tone.startTone(ToneGenerator.TONE_PROP_NACK, 300) else send(text)
    }

    /** A line in the conversation for diagnosis (kept in the log, not sent). */
    private fun note(text: String) {
        Talk.lines.add(Talk.Line(UUID.randomUUID().toString(), text, Talk.State.NOTE))
        Talk.changed()
    }

    /** Picks up a voice changed in VoiceActivity. */
    fun reloadVoice() {
        tts.shutdown()
        tts = openTts(this) {}
    }

    /** Volume key held past the long-press threshold: start talking; [holdEnd] on release sends. */
    fun holdStart() {
        if (!Talk.listening) toggleListening()
        Talk.hold = true
        Talk.refresh()
    }

    fun holdEnd() = finishListening()

    private fun endListening() {
        main.removeCallbacks(maxRecording)
        recognizer?.destroy()
        recognizer = null
        main.postDelayed(unmuteLater, 1_000) // the closing chime comes a little after
        setListening(false)
    }

    @Volatile private var sentBeepAt = 0L
    private val RECEIVED_GAP_MS = 1_200L // after the "sent" beep; 0.7 s still sounded run together on a real phone (10/9)

    private fun send(text: String) {
        val line = Talk.Line(UUID.randomUUID().toString(), text, Talk.State.SENDING)
        Talk.lines.add(line)
        Talk.changed()
        tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 200)
        sentBeepAt = android.os.SystemClock.uptimeMillis()
        outbox.put(line)
    }

    // --- network ---

    private fun http(method: String, path: String, body: String? = null, readTimeoutMs: Int = 10_000): String {
        val c = URL(baseUrl + path).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 5_000
            c.readTimeout = readTimeoutMs
            c.setRequestProperty("Authorization", "Bearer $key")
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toByteArray()) }
            }
            if (c.responseCode != 200) throw IOException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().readText()
        } finally {
            c.disconnect()
        }
    }

    /** Sends in order, retrying each message until the PC acks it; the server dedupes by id. */
    private fun sendLoop() {
        while (running) {
            val line = outbox.take()
            var firstFailure = 0L
            while (running) {
                try {
                    http("POST", "/say", JSONObject().put("id", line.id).put("text", line.text).toString())
                    main.post {
                        if (line.state == Talk.State.SENDING) line.state = Talk.State.RECEIVED
                        Talk.changed()
                    }
                    // On a LAN the ack is back within ~0.1 s: keep the "received" beep apart from the "sent" one.
                    main.postAtTime({ tone.startTone(ToneGenerator.TONE_PROP_ACK, 200) }, sentBeepAt + RECEIVED_GAP_MS)
                    break
                } catch (e: Exception) {
                    val now = System.currentTimeMillis()
                    if (firstFailure == 0L) firstFailure = now
                    // One "couldn't send" sound once it has been failing for a while; keep retrying silently.
                    else if (now - firstFailure in 30_000..33_000) main.post { tone.startTone(ToneGenerator.TONE_PROP_NACK, 500) }
                    setStatus(R.string.st_offline, "${e.message} · ${getString(R.string.waiting, outbox.size + 1)}")
                    Thread.sleep(3_000)
                }
            }
        }
    }

    private fun pollLoop() {
        val cursorKey = Channels.cursorKey(this)
        var cursor = prefs.getLong(cursorKey, 0)
        // A channel never listened to may hold a long history: only its last 10 minutes are new to us.
        val since = if (prefs.contains(cursorKey)) 0L else System.currentTimeMillis() - 10 * 60_000
        var wait = "&wait=0" // first poll after (re)connecting answers at once
        while (running) {
            try {
                val res = JSONObject(http("GET", "/poll?after=$cursor$wait", readTimeoutMs = 40_000))
                wait = ""
                setStatus(R.string.st_connected)
                val replies = res.getJSONArray("replies")
                val issues = res.getJSONArray("issues").let { a -> List(a.length()) { a.getString(it) } }
                for (i in 0 until replies.length()) {
                    val r = replies.getJSONObject(i)
                    cursor = maxOf(cursor, r.getLong("seq"))
                    if (runCatching { java.time.Instant.parse(r.getString("ts")).toEpochMilli() }.getOrDefault(Long.MAX_VALUE) < since) continue
                    main.post { onReply(r.getLong("seq").toString(), r.getString("text"), r.optString("reply_to")) }
                }
                prefs.edit().putLong(cursorKey, cursor).apply()
                val usage = res.optJSONObject("usage")
                fun pct(k: String) = usage?.optJSONObject(k)?.optDouble("used_percentage")?.takeIf { !it.isNaN() }?.toInt() ?: -1
                val u5 = pct("five_hour"); val u7 = pct("seven_day")
                main.post { Talk.issues = issues; Talk.usage5h = u5; Talk.usage7d = u7; Talk.changed() }
            } catch (e: Exception) {
                if (!running) return
                setStatus(R.string.st_offline, e.message ?: "")
                wait = "&wait=0"
                Thread.sleep(3_000)
            }
        }
    }

    private fun onReply(id: String, text: String, replyTo: String) {
        Talk.lines.find { it.id == replyTo }?.state = Talk.State.ANSWERED
        Talk.lines.add(Talk.Line(id, text, Talk.State.REPLY))
        Talk.changed()
        speakWhenFree(id, text)
    }

    /** Replies wait out a phone call, then play with the incoming-radio chirp. */
    private fun speakWhenFree(id: String, text: String) {
        if (!running) return
        if (inCall()) return run { main.postDelayed({ speakWhenFree(id, text) }, 2_000) }
        tone.startTone(ToneGenerator.TONE_SUP_RADIO_ACK, 300)
        main.postDelayed({ tts.speak(text, TextToSpeech.QUEUE_ADD, null, id) }, 400)
    }

    private fun setStatus(status: Int, detail: String = "") = main.post {
        Talk.status = status
        Talk.statusDetail = detail
        Talk.changed()
    }
}

/** Ringing, in a phone call, or in a VoIP call (KakaoTalk etc.): the walkie keeps its hands off. */
fun Context.inCall() = getSystemService(AudioManager::class.java).mode != AudioManager.MODE_NORMAL
