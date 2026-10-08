package com.walkieclaudie.app

import android.app.Application
import java.io.File

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        KeyLog.ctx = this
        Talk.load(File(filesDir, "talk.json"))
    }
}
