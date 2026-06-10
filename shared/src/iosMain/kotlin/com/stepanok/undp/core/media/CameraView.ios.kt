@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.stepanok.undp.core.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureDevicePosition
import platform.AVFoundation.AVCaptureDevicePositionBack
import platform.AVFoundation.AVCaptureDevicePositionFront
import platform.AVFoundation.AVCaptureFlashModeOff
import platform.AVFoundation.AVCapturePhoto
import platform.AVFoundation.AVCapturePhotoCaptureDelegateProtocol
import platform.AVFoundation.AVCapturePhotoOutput
import platform.AVFoundation.AVCapturePhotoSettings
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPresetPhoto
import platform.AVFoundation.AVCaptureTorchModeOff
import platform.AVFoundation.AVCaptureTorchModeOn
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.fileDataRepresentation
import platform.AVFoundation.flashMode
import platform.AVFoundation.hasTorch
import platform.AVFoundation.position
import platform.AVFoundation.setFlashMode
import platform.AVFoundation.torchMode
import com.stepanok.undp.core.io.capturesDirPath
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToFile
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIView
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_create

/** AVFoundation-backed live camera: preview layer hosted via Compose UIKitView + still capture. */
class IosCameraHandle : CameraHandle {

    private val _torchOn = MutableStateFlow(false)
    private val _hasTorch = MutableStateFlow(false)
    private val _available = MutableStateFlow(false)
    override val torchOn: StateFlow<Boolean> = _torchOn.asStateFlow()
    override val hasTorch: StateFlow<Boolean> = _hasTorch.asStateFlow()
    override val available: StateFlow<Boolean> = _available.asStateFlow()

    private val session = AVCaptureSession()
    private val photoOutput = AVCapturePhotoOutput()
    private val sessionQueue = dispatch_queue_create("com.stepanok.beacon.camera", null)

    private var device: AVCaptureDevice? = null
    private var input: AVCaptureDeviceInput? = null
    private var positionValue: AVCaptureDevicePosition = AVCaptureDevicePositionBack
    private var photoDelegate: PhotoCaptureDelegate? = null

    private val previewLayer = AVCaptureVideoPreviewLayer(session = session).apply {
        videoGravity = AVLayerVideoGravityResizeAspectFill
    }

    /** UIView that keeps the preview layer sized to its bounds (hosted by Compose UIKitView). */
    val previewView: UIView = PreviewUIView(previewLayer)

    init {
        session.sessionPreset = AVCaptureSessionPresetPhoto
        configureInput(positionValue)
        if (session.canAddOutput(photoOutput)) session.addOutput(photoOutput)
    }

    private fun deviceFor(pos: AVCaptureDevicePosition): AVCaptureDevice? {
        val all = AVCaptureDevice.devicesWithMediaType(AVMediaTypeVideo)
        val match = all.firstOrNull { (it as? AVCaptureDevice)?.position == pos } as? AVCaptureDevice
        return match ?: AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
    }

    private fun configureInput(pos: AVCaptureDevicePosition) {
        session.beginConfiguration()
        input?.let { session.removeInput(it) }
        val dev = deviceFor(pos)
        device = dev
        if (dev != null) {
            val newInput = AVCaptureDeviceInput.deviceInputWithDevice(dev, null) as? AVCaptureDeviceInput
            if (newInput != null && session.canAddInput(newInput)) {
                session.addInput(newInput)
                input = newInput
            }
            _hasTorch.value = dev.hasTorch
            _available.value = true
        } else {
            _available.value = false
        }
        session.commitConfiguration()
    }

    fun start() {
        dispatch_async(sessionQueue) { if (!session.running) session.startRunning() }
    }

    fun stop() {
        dispatch_async(sessionQueue) { if (session.running) session.stopRunning() }
    }

    override fun capture(onResult: (CapturedPhoto?) -> Unit) {
        if (!_available.value) {
            onResult(null)
            return
        }
        val settings = AVCapturePhotoSettings()
        settings.flashMode = AVCaptureFlashModeOff
        val delegate = PhotoCaptureDelegate { photo ->
            dispatch_async(dispatch_get_main_queue()) { onResult(photo) }
        }
        photoDelegate = delegate
        dispatch_async(sessionQueue) {
            photoOutput.capturePhotoWithSettings(settings, delegate)
        }
    }

    override fun toggleTorch() {
        val dev = device ?: return
        if (!dev.hasTorch) return
        if (dev.lockForConfiguration(null)) {
            val newState = !_torchOn.value
            dev.torchMode = if (newState) AVCaptureTorchModeOn else AVCaptureTorchModeOff
            dev.unlockForConfiguration()
            _torchOn.value = newState
        }
    }

    override fun switchLens() {
        dispatch_async(sessionQueue) {
            positionValue = if (positionValue == AVCaptureDevicePositionBack) {
                AVCaptureDevicePositionFront
            } else {
                AVCaptureDevicePositionBack
            }
            _torchOn.value = false
            configureInput(positionValue)
        }
    }
}

private class PreviewUIView(
    private val previewLayer: AVCaptureVideoPreviewLayer,
) : UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    init {
        layer.addSublayer(previewLayer)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        previewLayer.setFrame(bounds)
    }
}

private class PhotoCaptureDelegate(
    private var onResult: ((CapturedPhoto?) -> Unit)?,
) : NSObject(), AVCapturePhotoCaptureDelegateProtocol {

    @Suppress("unused")
    private var selfRef: PhotoCaptureDelegate? = this

    override fun captureOutput(
        output: AVCapturePhotoOutput,
        didFinishProcessingPhoto: AVCapturePhoto,
        error: NSError?,
    ) {
        val data: NSData? = if (error == null) didFinishProcessingPhoto.fileDataRepresentation() else null
        val photo = data?.let { writeCaptureJpeg(it) }
        onResult?.invoke(photo)
        onResult = null
        selfRef = null
    }
}

private fun writeCaptureJpeg(data: NSData): CapturedPhoto? {
    // Same downscale + recompress as the picker path (UIImageJPEGRepresentation re-encodes,
    // dropping EXIF) — a full-res AVCapture still would otherwise land in the outbox at
    // multi-MB size, with device metadata embedded.
    val image = UIImage.imageWithData(data) ?: return null
    val jpeg = UIImageJPEGRepresentation(downscale(image, 1600.0), 0.8) ?: return null
    // Persistent captures dir (same root as the outbox, NOT NSTemporaryDirectory) — a queued
    // photo must survive OS cache/tmp purges + process death until its upload succeeds.
    val stamp = NSDate().timeIntervalSince1970.toString().replace(".", "")
    val path = capturesDirPath() + "/capture_$stamp.jpg"
    return if (jpeg.writeToFile(path, atomically = true)) CapturedPhoto(path, jpeg.length.toLong()) else null
}

@Composable
actual fun rememberCameraHandle(): CameraHandle = remember { IosCameraHandle() }

@Composable
actual fun CameraPreview(handle: CameraHandle, modifier: Modifier) {
    val ios = handle as IosCameraHandle
    DisposableEffect(Unit) {
        ios.start()
        onDispose { ios.stop() }
    }
    UIKitView(factory = { ios.previewView }, modifier = modifier)
}
