package com.walkieclaudie.app

import android.app.Activity
import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.widget.LinearLayout
import android.widget.ScrollView
import java.util.Locale

/**
 * Opens the chosen TTS engine (default: the phone's default engine) in the phone's language, with the
 * chosen voice (default: that language's best) and speed. [onReady] gets a one-line diagnosis.
 * Note: since One UI 7 Samsung's engine (com.samsung.SMT) refuses third-party apps, so Android falls
 * back to Google there (confirmed on a Galaxy phone, 10/8) — nothing an app can do about it.
 */
fun openTts(ctx: Context, onReady: (String) -> Unit): TextToSpeech {
    val prefs = ctx.getSharedPreferences(WalkieService.PREFS, Context.MODE_PRIVATE)
    val default = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.TTS_DEFAULT_SYNTH)
    val engine = prefs.getString(WalkieService.TTS_ENGINE, "")!!.ifEmpty { default }
    lateinit var tts: TextToSpeech
    tts = TextToSpeech(ctx, { status ->
        if (status != TextToSpeech.SUCCESS) return@TextToSpeech onReady("TTS failed to start ($status), engine=$engine")
        tts.setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        tts.setLanguage(Locale.getDefault())
        val voices = tts.phoneLanguageVoices()
        val wanted = prefs.getString(WalkieService.TTS_VOICE, "")
        (voices.find { it.name == wanted } ?: voices.firstOrNull())?.let { tts.setVoice(it) }
        tts.setSpeechRate(prefs.getFloat(WalkieService.TTS_RATE, 1f))
        // Which engine actually connected (hidden API, diagnosis only).
        val using = runCatching { TextToSpeech::class.java.getMethod("getCurrentEngine").invoke(tts) }.getOrNull() ?: "?"
        val v = tts.voice
        val online = if (v?.isNetworkConnectionRequired == true) ", online" else ""
        onReady("TTS default=$default · using=$using · voice=${v?.name} (${v?.locale}, quality ${v?.quality}$online) · installed=${tts.engines.joinToString { it.name }}")
    }, engine)
    return tts
}

/**
 * Installed voices in the phone's language, best first: quality, then online voices (they sounded
 * clearly more natural on a Galaxy phone, 10/8; Google's engine falls back to on-device when offline).
 */
fun TextToSpeech.phoneLanguageVoices(): List<Voice> = voices.orEmpty()
    .filter { it.locale.language == Locale.getDefault().language && TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features }
    .sortedWith(compareByDescending<Voice> { it.quality }.thenByDescending { it.isNetworkConnectionRequired })

/** Pick the engine, voice and speed on one LCD menu; every change plays a sample sentence. */
class VoiceActivity : Activity() {
    private val prefs by lazy { getSharedPreferences(WalkieService.PREFS, MODE_PRIVATE) }
    private var tts: TextToSpeech? = null
    private lateinit var menu: LinearLayout
    private var diagnosis = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val body = Body(this) {}
        if (WalkieService.instance != null) body.led(Ui.LED_OK)
        menu = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.lcd(ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(menu) }, weight = 1f)
        body.key(key(R.string.back) { finish() })
        body.key(key(R.string.voice_preview) { sample() })
        reopen()
    }

    /** (Re)connects with the saved choice; redraws the menu once the engine is up. */
    private fun reopen(speak: Boolean = false) {
        tts?.shutdown()
        tts = openTts(this) {
            diagnosis = "$it · build ${BuildConfig.BUILD}"
            build()
            if (speak) sample()
        }
    }

    private fun build() {
        val t = tts ?: return
        menu.removeAllViews()

        val rate = prefs.getFloat(WalkieService.TTS_RATE, 1f)
        menu.menuRow(getString(R.string.voice_speed, rate), null, LinearLayout(this).apply {
            isBaselineAligned = false // else the two cells sit 1px apart in height and the second is clipped
            addView(step("−") { setRate(rate - 0.1f) })
            addView(step("+") { setRate(rate + 0.1f) }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dpi(8) })
        }, null)

        menu.menuTitle(getString(R.string.voice_engine))
        val engine = prefs.getString(WalkieService.TTS_ENGINE, "")!!
        val default = Settings.Secure.getString(contentResolver, Settings.Secure.TTS_DEFAULT_SYNTH)
        val defaultLabel = t.engines.find { it.name == default }?.label ?: default ?: "?"
        choice(getString(R.string.voice_engine_default, defaultLabel), null, engine == "") { pickEngine("") }
        t.engines.forEach { e -> choice(e.label, null, engine == e.name) { pickEngine(e.name) } }

        menu.menuTitle(getString(R.string.voice_voice))
        val voices = t.phoneLanguageVoices()
        val wanted = prefs.getString(WalkieService.TTS_VOICE, "")!!
        choice(getString(R.string.voice_auto), null, wanted == "") { pickVoice("", voices) }
        voices.forEach { v ->
            val sub = getString(R.string.voice_quality, v.quality) + if (v.isNetworkConnectionRequired) " · " + getString(R.string.voice_online) else ""
            choice(v.name, sub, wanted == v.name) { pickVoice(v.name, voices) }
        }

        menu.addView(lcdText(11f).apply { text = diagnosis; alpha = 0.5f }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(10) })
    }

    /** A pick-one row: ■ chosen, □ not. */
    private fun choice(label: String, sub: String?, on: Boolean, onPick: () -> Unit) =
        menu.menuRow((if (on) "■ " else "□ ") + label, sub, null) { if (!on) onPick() }

    /** Outlined −/+ cell, 40dp square so a thumb hits it. */
    private fun step(label: String, onClick: () -> Unit) = lcdText().apply {
        text = label
        gravity = android.view.Gravity.CENTER
        minWidth = dpi(40); minHeight = dpi(40)
        background = android.graphics.drawable.GradientDrawable().apply { setStroke(dpi(1), Ui.INK) }
        setOnClickListener { onClick() }
    }

    // 0.5x … 2.0x in 0.1 steps
    private fun setRate(r: Float) {
        val rate = (Math.round(r * 10) / 10f).coerceIn(0.5f, 2f)
        prefs.edit().putFloat(WalkieService.TTS_RATE, rate).apply()
        tts?.setSpeechRate(rate)
        build()
        sample()
    }

    private fun pickEngine(name: String) {
        prefs.edit().putString(WalkieService.TTS_ENGINE, name).putString(WalkieService.TTS_VOICE, "").apply()
        reopen(speak = true) // a different engine has different voices
    }

    private fun pickVoice(name: String, list: List<Voice>) {
        prefs.edit().putString(WalkieService.TTS_VOICE, name).apply()
        (list.find { it.name == name } ?: list.firstOrNull())?.let { tts?.setVoice(it) }
        build()
        sample()
    }

    private fun sample() {
        tts?.speak(getString(R.string.voice_sample), TextToSpeech.QUEUE_FLUSH, null, "sample")
    }

    override fun onPause() {
        super.onPause()
        tts?.stop()
        WalkieService.instance?.reloadVoice()
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }
}
