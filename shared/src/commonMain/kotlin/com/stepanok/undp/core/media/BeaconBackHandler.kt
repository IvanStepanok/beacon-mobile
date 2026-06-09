package com.stepanok.undp.core.media

import androidx.compose.runtime.Composable

/**
 * Intercepts the system/gesture back gesture while [enabled].
 *
 * On Android this consumes the hardware/predictive back so a multi-step flow can step backward
 * instead of being popped wholesale (which would discard an in-progress capture). On iOS — which
 * has no hardware back button — it is a no-op; in-app back is driven by the on-screen control and
 * the navigator's edge-swipe.
 */
@Composable
expect fun BeaconBackHandler(enabled: Boolean = true, onBack: () -> Unit)
