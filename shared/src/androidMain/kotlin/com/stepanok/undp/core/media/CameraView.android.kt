package com.stepanok.undp.core.media

import android.content.Context
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import com.stepanok.undp.core.io.capturesDirPath
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/** CameraX-backed live camera: drives a Preview SurfaceRequest into CameraXViewfinder + captures stills. */
class AndroidCameraHandle(private val context: Context) : CameraHandle {

    private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
    val surfaceRequest: StateFlow<SurfaceRequest?> = _surfaceRequest.asStateFlow()

    private val _torchOn = MutableStateFlow(false)
    private val _hasTorch = MutableStateFlow(false)
    private val _available = MutableStateFlow(false)
    override val torchOn: StateFlow<Boolean> = _torchOn.asStateFlow()
    override val hasTorch: StateFlow<Boolean> = _hasTorch.asStateFlow()
    override val available: StateFlow<Boolean> = _available.asStateFlow()

    private var lensFacing = CameraSelector.DEFAULT_BACK_CAMERA
    private var camera: Camera? = null
    private val rebind = MutableStateFlow(0)

    private val preview = Preview.Builder().build().apply {
        setSurfaceProvider { request -> _surfaceRequest.value = request }
    }
    private val imageCapture = ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        .build()

    private suspend fun awaitProvider(): ProcessCameraProvider = suspendCancellableCoroutine { cont ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({ if (cont.isActive) cont.resume(future.get()) }, ContextCompat.getMainExecutor(context))
    }

    /** Binds the camera to [owner] and re-binds whenever the lens is switched. Suspends until cancelled. */
    suspend fun bindLoop(owner: LifecycleOwner) {
        val provider = awaitProvider()
        rebind.collectLatest {
            try {
                provider.unbindAll()
                _torchOn.value = false
                camera = provider.bindToLifecycle(owner, lensFacing, preview, imageCapture)
                _hasTorch.value = camera?.cameraInfo?.hasFlashUnit() == true
                _available.value = true
                awaitCancellation()
            } catch (_: Exception) {
                _available.value = false
            } finally {
                provider.unbindAll()
            }
        }
    }

    override fun capture(onResult: (CapturedPhoto?) -> Unit) {
        // Persistent captures dir (same root as the outbox) — a queued photo must survive
        // OS cache purges + process death until its upload succeeds.
        val file = File(capturesDirPath(), "capture_${java.lang.System.currentTimeMillis()}.jpg")
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        imageCapture.takePicture(
            options,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                    // Downscale/recompress (drops ALL EXIF) + on-device face/plate redaction. This
                    // runs ML Kit (Tasks.await blocks), so deliverProcessed does the work OFF this
                    // main-thread callback and posts the CapturedPhoto back to the main thread.
                    deliverProcessed(file.absolutePath, onResult)
                }

                override fun onError(exception: ImageCaptureException) {
                    onResult(null)
                }
            },
        )
    }

    override fun toggleTorch() {
        val c = camera ?: return
        if (c.cameraInfo.hasFlashUnit()) {
            val newState = !_torchOn.value
            c.cameraControl.enableTorch(newState)
            _torchOn.value = newState
        }
    }

    override fun switchLens() {
        lensFacing = if (lensFacing == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        rebind.value += 1
    }
}

@Composable
actual fun rememberCameraHandle(): CameraHandle {
    val context = LocalContext.current
    return remember { AndroidCameraHandle(context.applicationContext) }
}

@Composable
actual fun CameraPreview(handle: CameraHandle, modifier: Modifier) {
    val android = handle as AndroidCameraHandle
    val owner: LifecycleOwner = LocalLifecycleOwner.current
    val request by android.surfaceRequest.collectAsState()

    LaunchedEffect(owner) { android.bindLoop(owner) }

    request?.let { CameraXViewfinder(surfaceRequest = it, modifier = modifier) }
}
