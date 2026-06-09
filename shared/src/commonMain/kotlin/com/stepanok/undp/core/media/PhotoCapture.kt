package com.stepanok.undp.core.media

import androidx.compose.runtime.Composable

/** A photo captured to app-local storage. */
data class CapturedPhoto(val path: String, val sizeBytes: Long)

/** Drives a real photo capture (device camera) or a pick from the photo library. */
interface PhotoCaptureController {
    fun captureFromCamera()
    fun pickFromLibrary()
}

/**
 * Wires platform photo capture into a composable. [onResult] receives the captured photo
 * (saved to app-local storage) or null if the user cancelled / capture failed. On Android it
 * uses the system camera (TakePicture + FileProvider) and Photo Picker; on iOS,
 * UIImagePickerController (camera, falling back to the photo library on the simulator).
 */
@Composable
expect fun rememberPhotoCapture(onResult: (CapturedPhoto?) -> Unit): PhotoCaptureController
