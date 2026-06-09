package com.stepanok.undp.core.storage

import android.content.Context
import com.stepanok.undp.core.android.AndroidAppContext

actual object Prefs {
    private val prefs by lazy {
        AndroidAppContext.require().getSharedPreferences("beacon.prefs", Context.MODE_PRIVATE)
    }

    actual fun getBool(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)

    actual fun setBool(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    actual fun getString(key: String): String? = prefs.getString(key, null)

    actual fun setString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
}
