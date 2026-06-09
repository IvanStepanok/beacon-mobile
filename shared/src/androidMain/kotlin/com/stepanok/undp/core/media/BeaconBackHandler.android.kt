package com.stepanok.undp.core.media

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

@Composable
actual fun BeaconBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}
