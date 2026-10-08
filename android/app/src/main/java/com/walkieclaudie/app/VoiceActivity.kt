package com.walkieclaudie.app

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.media.AudioAttributes
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
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

/** Pick the engine, voice and speed; every change plays a sample sentence. */
class VoiceActivity : Activity() {
    private val prefs by lazy { getSharedPreferences(WalkieService.PREFS, MODE_PRIVATE) }
    private var tts: TextToSpeech? = null
    private lateinit var engines: RadioGroup
    private lateinit var voices: RadioGroup
    private lateinit var info: TextView
    private var voiceNames = listOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fun label(text: Int) = TextView(this).apply { setText(text); textSize = 14f; alpha = 0.7f; setPadding(0, 32, 0, 4) }

        engines = RadioGroup(this)
        voices = RadioGroup(this)
        info = TextView(this).apply { textSize = 12f; alpha = 0.6f; setPadding(0, 24, 0, 0); setTextIsSelectable(true) }
        val rate = prefs.getFloat(WalkieService.TTS_RATE, 1f)
        val rateLabel = TextView(this).apply { textSize = 14f; alpha = 0.7f; setPadding(0, 32, 0, 4); text = getString(R.string.voice_speed, rate) }
        // 0.5x … 2.0x in 0.1 steps
        val rateBar = SeekBar(this).apply {
            max = 15
            progress = ((rate - 0.5f) * 10).toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, p: Int, fromUser: Boolean) {
                    rateLabel.text = getString(R.string.voice_speed, 0.5f + p / 10f)
                }
                override fun onStartTrackingTouch(bar: SeekBar) {}
                override fun onStopTrackingTouch(bar: SeekBar) {
                    val r = 0.5f + bar.progress / 10f
                    prefs.edit().putFloat(WalkieService.TTS_RATE, r).apply()
                    tts?.setSpeechRate(r)
                    sample()
                }
            })
        }

        setContentView(ScrollView(this).apply { padForSystemBars() }.also { it.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply { setText(R.string.voice); textSize = 24f; typeface = Typeface.DEFAULT_BOLD })
            addView(label(R.string.voice_engine))
            addView(engines)
            addView(rateLabel)
            addView(rateBar)
            addView(Button(context).apply { setText(R.string.voice_preview); setOnClickListener { sample() } },
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = 32 })
            addView(label(R.string.voice_voice))
            addView(voices)
            addView(info)
        }) })
        reopen(fillEngines = true)
    }

    /** (Re)connects with the saved choice; refreshes the lists once the engine is up. */
    private fun reopen(fillEngines: Boolean = false, speak: Boolean = false) {
        tts?.shutdown()
        tts = openTts(this) { diagnosis ->
            info.text = "$diagnosis · build ${BuildConfig.BUILD}"
            if (fillEngines) fillEngines()
            fillVoices()
            if (speak) sample()
        }
    }

    private fun fillEngines() {
        val t = tts ?: return
        val default = Settings.Secure.getString(contentResolver, Settings.Secure.TTS_DEFAULT_SYNTH)
        val list = t.engines
        val keys = listOf("") + list.map { it.name }
        val defaultLabel = list.find { it.name == default }?.label ?: default ?: "?"
        val labels = listOf(getString(R.string.voice_engine_default, defaultLabel)) + list.map { it.label }
        engines.bind(labels, keys.indexOf(prefs.getString(WalkieService.TTS_ENGINE, "")).coerceAtLeast(0)) { i ->
            if (keys[i] == prefs.getString(WalkieService.TTS_ENGINE, "")) return@bind
            prefs.edit().putString(WalkieService.TTS_ENGINE, keys[i]).putString(WalkieService.TTS_VOICE, "").apply()
            reopen(speak = true) // a different engine has different voices
        }
    }

    private fun fillVoices() {
        val t = tts ?: return
        val list = t.phoneLanguageVoices()
        voiceNames = listOf("") + list.map { it.name }
        val labels = listOf(getString(R.string.voice_auto)) + list.map {
            it.name + " · " + getString(R.string.voice_quality, it.quality) +
                if (it.isNetworkConnectionRequired) " · " + getString(R.string.voice_online) else ""
        }
        voices.bind(labels, voiceNames.indexOf(prefs.getString(WalkieService.TTS_VOICE, "")).coerceAtLeast(0)) { i ->
            if (voiceNames[i] == prefs.getString(WalkieService.TTS_VOICE, "")) return@bind
            prefs.edit().putString(WalkieService.TTS_VOICE, voiceNames[i]).apply()
            (list.find { it.name == voiceNames[i] } ?: list.firstOrNull())?.let { t.setVoice(it) }
            sample()
        }
    }

    // Radio lists, not spinners: on a Galaxy phone the spinners rendered empty (theme text colour).
    private fun RadioGroup.bind(labels: List<String>, selected: Int, onPick: (Int) -> Unit) {
        setOnCheckedChangeListener(null)
        removeAllViews()
        labels.forEachIndexed { i, l -> addView(RadioButton(context).apply { id = i + 1; text = l; textSize = 15f }) }
        check(selected + 1)
        setOnCheckedChangeListener { _, id -> if (id > 0) onPick(id - 1) }
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
