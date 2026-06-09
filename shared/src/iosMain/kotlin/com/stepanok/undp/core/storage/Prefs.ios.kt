package com.stepanok.undp.core.storage

import platform.Foundation.NSUserDefaults

actual object Prefs {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun getBool(key: String, default: Boolean): Boolean =
        if (defaults.objectForKey(key) == null) default else defaults.boolForKey(key)

    actual fun setBool(key: String, value: Boolean) {
        defaults.setBool(value, forKey = key)
    }

    actual fun getString(key: String): String? = defaults.stringForKey(key)

    actual fun setString(key: String, value: String) {
        defaults.setObject(value, forKey = key)
    }
}
