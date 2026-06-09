package com.stepanok.undp.core.location

import androidx.compose.runtime.Composable

/**
 * Requests foreground location permission once on first composition and invokes
 * [onResult] with the outcome. On iOS this defers to CLLocationManager, which
 * presents the system prompt the first time a location is requested.
 */
@Composable
expect fun RequestLocationPermission(onResult: (granted: Boolean) -> Unit)
