package com.stepanok.undp.core.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.stepanok.undp.core.io.capturesDirPath
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import java.io.File
import java.io.FileOutputStream

@Composable
actual fun rememberPhotoCapture(onResult: (CapturedPhoto?) -> Unit): PhotoCaptureController {
    val context = LocalContext.current
    val pending = remember { mutableStateOf<File?>(null) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = pending.value
        pending.value = null
        if (ok && file != null && file.length() > 0) {
            deliverProcessed(file.absolutePath, onResult)
        } else {
            onResult(null)
        }
    }

    val pickMedia = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) copyAndDeliver(context, uri, onResult) else onResult(null) }

    val requestCamera = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) launchCamera(context, pending, takePicture) else onResult(null)
    }

    return remember {
        object : PhotoCaptureController {
            override fun captureFromCamera() {
                val granted = context.checkSelfPermission(Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
                if (granted) launchCamera(context, pending, takePicture) else requestCamera.launch(Manifest.permission.CAMERA)
            }

            override fun pickFromLibrary() {
                pickMedia.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            }
        }
    }
}

private fun launchCamera(
    context: Context,
    pending: MutableState<File?>,
    launcher: ManagedActivityResultLauncher<Uri, Boolean>,
) {
    // Persistent captures dir (same root as the outbox; declared as a files-path in
    // file_paths.xml) — a queued photo must survive OS cache purges until upload.
    val file = File(capturesDirPath(), "capture_${java.lang.System.currentTimeMillis()}.jpg")
    pending.value = file
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    launcher.launch(uri)
}

// Process (downscale + EXIF-strip + on-device face/plate redaction) OFF the main thread —
// ML Kit's Tasks.await blocks and the launcher/camera callbacks fire on main — then post the
// CapturedPhoto (carrying its RedactionResult) back to the main thread.
internal fun deliverProcessed(path: String, onResult: (CapturedPhoto?) -> Unit) {
    Thread {
        // Read GPS from EXIF BEFORE downscaleAndStrip re-encodes the file and drops all metadata.
        val gps = readExifLatLng(path)
        val p = downscaleAndStrip(path)
        val photo = if (p.sizeBytes > 0) CapturedPhoto(path, p.sizeBytes, p.redaction, gps?.first, gps?.second) else null
        Handler(Looper.getMainLooper()).post { onResult(photo) }
    }.start()
}

private fun copyAndDeliver(context: Context, uri: Uri, onResult: (CapturedPhoto?) -> Unit) {
    Thread {
        val photo = runCatching {
            val file = File(capturesDirPath(), "pick_${java.lang.System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            if (file.length() > 0) {
                // Gallery imports usually carry GPS — read it (to center the map) BEFORE stripping.
                val gps = readExifLatLng(file.absolutePath)
                val p = downscaleAndStrip(file.absolutePath)
                CapturedPhoto(file.absolutePath, p.sizeBytes, p.redaction, gps?.first, gps?.second)
            } else {
                null
            }
        }.getOrNull()
        Handler(Looper.getMainLooper()).post { onResult(photo) }
    }.start()
}

/**
 * Read GPS lat/lng from the original photo's EXIF (gallery imports / GPS-tagged captures) BEFORE
 * [downscaleAndStrip] re-encodes and drops it. Used ONLY to center the capture map on where the
 * photo was taken; it is never persisted (the saved JPEG is always EXIF-stripped). Returns null
 * when the photo has no location, or the 0,0 "Null Island" sentinel some cameras write for "no fix".
 */
internal fun readExifLatLng(path: String): Pair<Double, Double>? = runCatching {
    val ll = ExifInterface(path).latLong ?: return@runCatching null
    val lat = ll[0]
    val lng = ll[1]
    if (lat != 0.0 || lng != 0.0) lat to lng else null
}.getOrNull()

/**
 * Downscale + recompress a JPEG in place: caps the longest side at [MAX_DIM] and
 * re-encodes at [JPEG_QUALITY] (typically cutting a multi-MB phone photo to a few
 * hundred KB, so uploads are fast on poor connectivity). The recompression also
 * drops ALL EXIF (GPS/timestamp/device); orientation is baked into the pixels so
 * the image is never sideways. Returns the new file size. On any failure it falls
 * back to stripping EXIF on the original (privacy is never sacrificed).
 */
private const val MAX_DIM = 1600
private const val JPEG_QUALITY = 80

/** File size + on-device redaction outcome of a processed photo (returned by [downscaleAndStrip]). */
internal data class ProcessedPhoto(val sizeBytes: Long, val redaction: RedactionResult)

internal fun downscaleAndStrip(path: String): ProcessedPhoto = runCatching {
    val orientation = ExifInterface(path)
        .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_DIM * 2) sample *= 2

    var bmp = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: return@runCatching ProcessedPhoto(File(path).length(), RedactionResult())

    val longest = maxOf(bmp.width, bmp.height)
    if (longest > MAX_DIM) {
        val scale = MAX_DIM.toFloat() / longest
        bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
    }
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
    }
    if (!matrix.isIdentity) {
        bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
    }
    // On-device, OFFLINE redaction (faces + plates) on the oriented bitmap, BEFORE encoding —
    // so the redacted pixels are what gets written (irreversible). Best-effort; never throws.
    val (redacted, redaction) = redactBitmap(bmp)
    FileOutputStream(path).use { out -> redacted.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out) }
    if (redacted !== bmp) redacted.recycle()
    bmp.recycle()
    ProcessedPhoto(File(path).length(), redaction)
}.getOrElse {
    stripSensitiveExif(path) // recompress failed → at least strip EXIF on the original
    ProcessedPhoto(File(path).length(), RedactionResult())
}

/** Remove location, timestamp and device-identity EXIF tags; keep orientation so photos aren't rotated. */
internal fun stripSensitiveExif(path: String) {
    runCatching {
        val exif = ExifInterface(path)
        listOf(
            ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_TIMESTAMP, ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_GPS_PROCESSING_METHOD,
            ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, ExifInterface.TAG_SOFTWARE,
            ExifInterface.TAG_USER_COMMENT,
        ).forEach { tag -> exif.setAttribute(tag, null) }
        exif.saveAttributes()
    }
}
