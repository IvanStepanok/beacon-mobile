package com.stepanok.undp.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/** Null = follow system language; otherwise a UN language tag chosen in Settings (shared devices). */
var appLocaleOverride by mutableStateOf<String?>(null)

/** JetBrains-documented pattern to force a resource locale at runtime (per-platform actuals). */
expect object LocalAppLocale {
    val current: String @Composable get
    @Composable infix fun provides(value: String?): ProvidedValue<*>
}

/** Current system locale tag (BCP-47). */
expect fun systemLocaleTag(): String

/**
 * Applies the resolved locale to resources and forces RTL for Arabic. Wraps the whole app so
 * `stringResource` re-resolves and layout mirrors when the override changes.
 */
@Composable
fun BeaconAppEnvironment(content: @Composable () -> Unit) {
    val override = appLocaleOverride
    val resolved = UnLanguage.fromTag(override ?: systemLocaleTag())
    CompositionLocalProvider(LocalAppLocale provides override) {
        key(override) {
            CompositionLocalProvider(
                LocalLayoutDirection provides if (resolved.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                content()
            }
        }
    }
}
