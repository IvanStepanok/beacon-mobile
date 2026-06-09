package com.stepanok.undp.core.location

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
actual fun RequestLocationPermission(onResult: (granted: Boolean) -> Unit) {
    // CLLocationManager presents the system prompt the first time a location is requested.
    LaunchedEffect(Unit) { onResult(true) }
}
