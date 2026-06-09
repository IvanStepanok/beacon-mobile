package com.stepanok.undp.core.android

import android.content.Context

/**
 * Holds the process-wide application [Context] so shared platform code (connectivity,
 * location, offline map storage) can reach Android system services. Set once from
 * [com.stepanok.undp.BeaconApplication.onCreate], which runs before any UI or Koin.
 */
object AndroidAppContext {
    @Volatile
    private var ctx: Context? = null

    fun init(context: Context) {
        ctx = context.applicationContext
    }

    fun require(): Context =
        ctx ?: error("AndroidAppContext not initialized — is BeaconApplication registered in the manifest?")
}
