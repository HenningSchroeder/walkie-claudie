package com.bookmari.walkie

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        KeyLog.ctx = this
        Talk.load(Channels.talkFile(this))
    }
}
