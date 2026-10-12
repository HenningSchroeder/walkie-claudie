package com.walkieclaudie.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.session.MediaSession
import android.view.KeyEvent

/**
 * Holds an active MediaSession so Bluetooth earphone buttons reach us with the screen off
 * or another app in front (button test only; WalkieService has its own). Re-grabs the keys
 * with [playSilence] on every start.
 */
class MediaButtonService : Service() {
    private lateinit var session: MediaSession

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("listen", getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW))
        val notification = Notification.Builder(this, "listen")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentText(getString(R.string.notif_text))
            .build()
        startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)

        session = MediaSession(this, "walkie").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onMediaButtonEvent(intent: Intent): Boolean {
                    @Suppress("DEPRECATION")
                    val e = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return false
                    KeyLog.key("media", e)
                    return !inCall() // calls keep answer/hang-up
                }
            })
            playing(true)
            isActive = true
        }
        KeyLog.add("media", "session active")
        // Same as the walkie: step aside for the whole call, or a test left running eats answer/hang-up.
        if (android.os.Build.VERSION.SDK_INT >= 31) getSystemService(AudioManager::class.java)
            .addOnModeChangedListener(mainExecutor, modeListener)
        registerReceiver(screen, IntentFilter().apply { addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF) })
    }

    private val modeListener by lazy {
        AudioManager.OnModeChangedListener {
            KeyLog.add("media", "audio mode $it")
            session.playing(!inCall()); session.isActive = !inCall()
        }
    }

    // Marks screen on/off in the log so earphone presses can be read against it.
    private val screen = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = KeyLog.add("screen", i.action!!.substringAfterLast('_'))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        playSilence()
        return START_STICKY
    }

    override fun onDestroy() {
        if (android.os.Build.VERSION.SDK_INT >= 31) getSystemService(AudioManager::class.java)
            .removeOnModeChangedListener(modeListener)
        unregisterReceiver(screen)
        session.release()
        KeyLog.add("media", "session released")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null
}

/**
 * Android routes media keys to the most recently *playing* session, so a session that
 * wants earphone buttons plays half a second of silence to become that session.
 */
fun playSilence() {
    val rate = 8000
    val samples = rate / 2
    val track = AudioTrack.Builder()
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
        .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
        .setTransferMode(AudioTrack.MODE_STATIC)
        .setBufferSizeInBytes(samples * 2)
        .build()
    track.write(ShortArray(samples), 0, samples)
    track.setNotificationMarkerPosition(samples)
    track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
        override fun onMarkerReached(t: AudioTrack) = t.release()
        override fun onPeriodicNotification(t: AudioTrack) {}
    })
    track.play()
}
