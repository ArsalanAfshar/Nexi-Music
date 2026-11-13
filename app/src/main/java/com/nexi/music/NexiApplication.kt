package com.nexi.music

import android.app.Application
import com.nexi.music.diagnostics.CrashLogStore

class NexiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLogStore.install(this)
    }
}
