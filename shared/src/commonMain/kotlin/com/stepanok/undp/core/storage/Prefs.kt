package com.stepanok.undp.core.storage

/** Tiny persistent key-value store (Android SharedPreferences / iOS NSUserDefaults). */
expect object Prefs {
    fun getBool(key: String, default: Boolean): Boolean
    fun setBool(key: String, value: Boolean)
    fun getString(key: String): String?
    fun setString(key: String, value: String)
}

object PrefKeys {
    const val ONBOARDING_SEEN = "onboarding_seen"

    /** Per-install anonymous device identity (X-Device-Id). Generated once, persisted forever. */
    const val DEVICE_ID = "device_id"
}
