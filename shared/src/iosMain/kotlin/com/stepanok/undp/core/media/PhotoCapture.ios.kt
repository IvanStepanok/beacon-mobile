package com.stepanok.undp.core.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSDate
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToFile
import platform.UIKit.UIApplication
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.darwin.NSObject

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

@OptIn(ExperimentalForeignApi::class)
private fun present(sourceType: UIImagePickerControllerSourceType, onResult: (CapturedPhoto?) -> Unit) {
    val root = UIApplication.sharedApplication.keyWindow?.rootViewController
    if (root == null) {
        onResult(null)
        return
    }
    val picker = UIImagePickerController()
    picker.sourceType =
        if (UIImagePickerController.isSourceTypeAvailable(sourceType)) sourceType
        else UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary
    picker.delegate = PickerDelegate(onResult)
    root.presentViewController(picker, animated = true, completion = null)
}

@OptIn(ExperimentalForeignApi::class)
private class PickerDelegate(
    private var onResult: ((CapturedPhoto?) -> Unit)?,
) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {

    // The picker holds its delegate weakly — keep a strong self-reference until a callback fires.
    private var selfRef: PickerDelegate? = this

    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>,
    ) {
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        val photo = image?.let { saveJpeg(it) }
        picker.dismissViewControllerAnimated(true, completion = null)
        finish(photo)
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, completion = null)
        finish(null)
    }

    private fun finish(photo: CapturedPhoto?) {
        onResult?.invoke(photo)
        onResult = null
        selfRef = null
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun saveJpeg(image: UIImage): CapturedPhoto? {
    // Downscale + recompress so uploads are small. UIImageJPEGRepresentation also
    // re-encodes (dropping EXIF); UIImage applies its own orientation when drawn.
    val data = UIImageJPEGRepresentation(downscale(image, 1600.0), 0.8) ?: return null
    val stamp = NSDate().timeIntervalSince1970.toString().replace(".", "")
    val path = NSTemporaryDirectory() + "capture_$stamp.jpg"
    return if (data.writeToFile(path, atomically = true)) CapturedPhoto(path, data.length.toLong()) else null
}

@OptIn(ExperimentalForeignApi::class)
private fun downscale(image: UIImage, maxDim: Double): UIImage {
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
