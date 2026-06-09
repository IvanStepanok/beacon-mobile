package com.stepanok.undp.core.media

import androidx.compose.runtime.Composable

/** Requests camera permission once on first composition and reports the outcome. */
@Composable
expect fun RequestCameraPermission(onResult: (granted: Boolean) -> Unit)
