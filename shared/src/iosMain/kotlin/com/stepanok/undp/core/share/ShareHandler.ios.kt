package com.stepanok.undp.core.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

@Composable
actual fun rememberShareHandler(): ShareHandler = remember {
    ShareHandler { _, _, content ->
        val controller = UIActivityViewController(activityItems = listOf(content), applicationActivities = null)
        val root = UIApplication.sharedApplication.keyWindow?.rootViewController
        root?.presentViewController(controller, animated = true, completion = null)
    }
}
