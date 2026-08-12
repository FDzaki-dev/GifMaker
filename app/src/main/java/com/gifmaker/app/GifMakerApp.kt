package com.gifmaker.app

import android.app.Application

class GifMakerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)
    }
}
