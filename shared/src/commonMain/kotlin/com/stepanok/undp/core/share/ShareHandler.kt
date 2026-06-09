package com.stepanok.undp.core.share

import androidx.compose.runtime.Composable

/** Shares text content via the platform share sheet (Android chooser / iOS UIActivityViewController). */
fun interface ShareHandler {
    fun share(title: String, mimeType: String, content: String)
}

@Composable
expect fun rememberShareHandler(): ShareHandler
