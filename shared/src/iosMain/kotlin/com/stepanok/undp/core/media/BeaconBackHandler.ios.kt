package com.stepanok.undp.core.media

import androidx.compose.runtime.Composable

@Composable
actual fun BeaconBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // iOS has no hardware back button; in-app back is driven by the on-screen control and the
    // navigator's edge-swipe, so there is nothing to intercept here.
}
