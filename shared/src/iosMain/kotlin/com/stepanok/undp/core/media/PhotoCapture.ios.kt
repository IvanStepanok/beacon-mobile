package com.stepanok.undp.core.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import com.stepanok.undp.core.io.capturesDirPath
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToFile
import platform.UIKit.UIApplication
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.Photos.PHAsset
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerPHAsset
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_get_main_queue

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberPhotoCapture(onResult: (CapturedPhoto?) -> Unit): PhotoCaptureController = remember {
    object : PhotoCaptureController {
        override fun captureFromCamera() =
            present(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera, onResult)

        override fun pickFromLibrary() =
            present(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary, onResult)
    }
}

// Strong, EXTERNAL reference to the live picker delegate. UIImagePickerController.delegate is a
// WEAK property, and Kotlin/Native's GC collects reference cycles — so a delegate kept alive only
// by a self-reference can be collected BEFORE the pick callback fires, silently dropping the first
// selection (the reported bug: "the photo isn't selected the first time"). A top-level strong ref
// guarantees the delegate outlives the picker until finish() clears it. Only one picker is on
// screen at a time, so a single slot is sufficient.
private var activePickerDelegate: PickerDelegate? = null

@OptIn(ExperimentalForeignApi::class)
private fun present(sourceType: UIImagePickerControllerSourceType, onResult: (CapturedPhoto?) -> Unit) {
    val root = UIApplication.sharedApplication.keyWindow?.rootViewController
    if (root == null) {
        onResult(null)
        return
    }
    // Present on the TOPMOST controller. Presenting on a controller that already has a presented
    // child is a silent no-op in UIKit — a stale/mid-dismiss presentation would otherwise swallow
    // the request, so the gallery "doesn't open" until a second tap.
    var top: UIViewController = root
    while (top.presentedViewController != null) top = top.presentedViewController!!

    val picker = UIImagePickerController()
    picker.sourceType =
        if (UIImagePickerController.isSourceTypeAvailable(sourceType)) sourceType
        else UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary
    val delegate = PickerDelegate(onResult)
    activePickerDelegate = delegate
    picker.delegate = delegate
    top.presentViewController(picker, animated = true, completion = null)
}

@OptIn(ExperimentalForeignApi::class)
private class PickerDelegate(
    private var onResult: ((CapturedPhoto?) -> Unit)?,
) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {

    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>,
    ) {
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        // The picked asset's GPS (when Photos access is granted) — read BEFORE re-encoding, used
        // ONLY to center the capture map. The saved JPEG is always EXIF-stripped (UIImageJPEGRepresentation).
        val gps = extractGps(didFinishPickingMediaWithInfo)
        picker.dismissViewControllerAnimated(true, completion = null)
        if (image == null) {
            finish(null)
            return
        }
        // saveJpeg now runs Vision detection (synchronous) — keep it OFF the main thread, then
        // post the result back to main for the Compose callback.
        dispatch_async(dispatch_get_global_queue(0, 0u)) {
            val photo = saveJpeg(image, gps?.first, gps?.second)
            dispatch_async(dispatch_get_main_queue()) { finish(photo) }
        }
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, completion = null)
        finish(null)
    }

    private fun finish(photo: CapturedPhoto?) {
        onResult?.invoke(photo)
        onResult = null
        activePickerDelegate = null
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun saveJpeg(image: UIImage, exifLat: Double?, exifLng: Double?): CapturedPhoto? {
    // Downscale + recompress so uploads are small. UIImageJPEGRepresentation also
    // re-encodes (dropping EXIF); UIImage applies its own orientation when drawn.
    // On-device, OFFLINE face/plate redaction (B1) BEFORE encoding — the redacted pixels
    // are what gets written (irreversible). Best-effort; never throws.
    val (redacted, redaction) = redactImage(downscale(image, 1600.0))
    val data = UIImageJPEGRepresentation(redacted, 0.8) ?: return null
    val stamp = NSDate().timeIntervalSince1970.toString().replace(".", "")
    // Persistent captures dir (same root as the outbox, NOT NSTemporaryDirectory) — a queued
    // photo must survive OS cache/tmp purges + process death until its upload succeeds.
    val path = capturesDirPath() + "/capture_$stamp.jpg"
    return if (data.writeToFile(path, atomically = true)) CapturedPhoto(path, data.length.toLong(), redaction, exifLat, exifLng) else null
}

/**
 * GPS of the picked photo from its PHAsset (gallery imports, when Photos access is granted) — read
 * BEFORE the JPEG re-encode strips EXIF, used only to center the capture map (never persisted).
 * Null for camera shots / when Photos access isn't granted / the 0,0 "no fix" sentinel.
 */
@OptIn(ExperimentalForeignApi::class)
private fun extractGps(info: Map<Any?, *>): Pair<Double, Double>? {
    val asset = info[UIImagePickerControllerPHAsset] as? PHAsset ?: return null
    val loc = asset.location ?: return null
    return loc.coordinate.useContents {
        if (latitude != 0.0 || longitude != 0.0) latitude to longitude else null
    }
}

@OptIn(ExperimentalForeignApi::class)
internal fun downscale(image: UIImage, maxDim: Double): UIImage {
    val (w, h) = image.size.useContents { width to height }
    val longest = maxOf(w, h)
    if (longest <= maxDim || longest <= 0.0) return image
    val scale = maxDim / longest
    val nw = w * scale
    val nh = h * scale
    UIGraphicsBeginImageContextWithOptions(CGSizeMake(nw, nh), false, 1.0)
    image.drawInRect(CGRectMake(0.0, 0.0, nw, nh))
    val out = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()
    return out ?: image
}
