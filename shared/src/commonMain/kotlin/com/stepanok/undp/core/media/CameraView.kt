package com.stepanok.undp.core.media

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.StateFlow

/**
 * Imperative handle for the live in-app camera (CameraX on Android, AVFoundation on iOS).
 * Owned by the capture screen; the platform [CameraPreview] renders its feed and manages
 * the capture-session lifecycle.
 */
interface CameraHandle {
    /** Whether the continuous torch is currently on. */
    val torchOn: StateFlow<Boolean>
    /** Whether the active camera has a torch/flash unit (hide the toggle if false). */
    val hasTorch: StateFlow<Boolean>
    /** True once a usable camera device is available (false e.g. on the iOS simulator). */
    val available: StateFlow<Boolean>

    /** Capture a still photo to app-local storage (sensitive EXIF stripped); null on failure. */
    fun capture(onResult: (CapturedPhoto?) -> Unit)
    fun toggleTorch()
    fun switchLens()
}

/** Creates and remembers the platform camera handle for the current screen. */
@Composable
expect fun rememberCameraHandle(): CameraHandle

/** Renders the live camera preview, starting the session on entry and releasing it on exit. */
@Composable
expect fun CameraPreview(handle: CameraHandle, modifier: Modifier)
