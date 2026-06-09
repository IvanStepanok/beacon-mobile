package com.stepanok.undp

import android.app.Application
import com.stepanok.undp.core.android.AndroidAppContext

/** Captures the application context before any UI / Koin so shared platform code can reach it. */
class BeaconApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidAppContext.init(this)
    }
}
