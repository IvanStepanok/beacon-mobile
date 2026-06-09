package com.stepanok.undp.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import platform.Foundation.NSLocale
import platform.Foundation.NSUserDefaults
import platform.Foundation.preferredLanguages

private val systemDefault: String = (NSLocale.preferredLanguages.firstOrNull() as? String) ?: "en"

actual fun systemLocaleTag(): String = systemDefault

actual object LocalAppLocale {
    private const val KEY = "AppleLanguages"
    private val localeState = staticCompositionLocalOf { systemDefault }

    actual val current: String
        @Composable get() = localeState.current

    @Composable
    actual infix fun provides(value: String?): ProvidedValue<*> {
        val defaults = NSUserDefaults.standardUserDefaults
        if (value == null) {
            defaults.removeObjectForKey(KEY)
        } else {
            defaults.setObject(listOf(value), KEY)
        }
        return localeState.provides(value ?: systemDefault)
    }
}
