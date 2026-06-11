package com.stepanok.undp.core.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

private const val TAG = "BeaconRedact"

/**
 * On-device, OFFLINE face + license-plate redaction (B1). Detects regions on [src], paints
 * over them on a mutable copy, and returns (redacted bitmap, result). Faces use ML Kit Face
 * Detection (BUNDLED offline model — reliable, no network/Play-Services download); plates use
 * ML Kit Text Recognition + an aspect-ratio/character heuristic (best-effort). MUST run OFF the
 * main thread — `Tasks.await` blocks.
 *
 * HONESTY: a detector that THREW (model failed to load, runtime error) is NOT the same as one
 * that ran and found nothing. A thrown detector leaves its `*Redacted` flag FALSE so the report's
 * Anonymization never claims a guarantee that did not run. The photo is always still stored (EXIF
 * already stripped) — privacy is never WORSE than before redaction existed.
 */
internal fun redactBitmap(src: Bitmap): Pair<Bitmap, RedactionResult> {
    // null == the detector threw (logged); empty == it ran and found nothing.
    val faces: List<Rect>? = try {
        detectFaces(src)
    } catch (e: Throwable) {
        Log.w(TAG, "face detection failed (no blur applied; flag stays false)", e); null
    }
    val plates: List<Rect>? = try {
        detectPlates(src)
    } catch (e: Throwable) {
        Log.w(TAG, "plate detection failed (no blur applied; flag stays false)", e); null
    }
    val faceList = faces.orEmpty()
    val plateList = plates.orEmpty()
    Log.i(TAG, "redact ${src.width}x${src.height}: faces=${faces?.size ?: "ERR"} plates=${plates?.size ?: "ERR"}")

    if (faceList.isEmpty() && plateList.isEmpty()) {
        // Nothing to paint. Flags reflect whether each detector actually RAN (honest).
        return src to RedactionResult(facesRedacted = faces != null, platesRedacted = plates != null)
    }
    val out = if (src.isMutable) src else src.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(out)
    faceList.forEach { pixelate(out, canvas, it) }
    plateList.forEach { solidBox(canvas, it) }
    return out to RedactionResult(
        facesFound = faceList.size, platesFound = plateList.size,
        facesRedacted = faces != null, platesRedacted = plates != null,
    )
}

/** Detect faces. THROWS on ML Kit failure (caught + logged by the caller) — do not swallow here. */
private fun detectFaces(bmp: Bitmap): List<Rect> {
    val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .build(),
    )
    return try {
        Tasks.await(detector.process(InputImage.fromBitmap(bmp, 0)))
            .map { expand(it.boundingBox, bmp, 0.18f) }
    } finally {
        detector.close()
    }
}

/** Detect plate-like text. THROWS on ML Kit failure (caught + logged by the caller).
 *
 *  Escalating passes, each only when the previous found nothing — the common case
 *  (readable straight plate, or no plate at all) stays a single OCR run:
 *  1. native resolution;
 *  2. 2× upscale — distant plates are often ~20px tall, below the OCR's practical
 *     glyph floor;
 *  3. ±28° rotations at 1.5× — the Latin recognizer can't read text tilted much
 *     past ~20°, and street-scene plates are routinely shot at an angle (a real
 *     user photo with a ~35°-tilted plate OCR'd as nothing at any scale).
 *  Boxes from transformed rasters are mapped back to source pixels (axis-aligned
 *  hull of the inverse-mapped corners — over-covers slightly, fine for redaction). */
private fun detectPlates(bmp: Bitmap): List<Rect> {
    ocrPlateBoxes(bmp).takeIf { it.isNotEmpty() }?.let { return it }
    if (maxOf(bmp.width, bmp.height) * 2 > 4096) return emptyList() // keep retry rasters sane
    val passes = listOf(
        Matrix().apply { setScale(2f, 2f) },
        Matrix().apply { setScale(1.5f, 1.5f); postRotate(28f) },
        Matrix().apply { setScale(1.5f, 1.5f); postRotate(-28f) },
    )
    for (m in passes) {
        ocrTransformed(bmp, m).takeIf { it.isNotEmpty() }?.let { return it }
    }
    return emptyList()
}

/** OCR [src] redrawn under [m] (scale and/or rotation); plate boxes come back in
 *  SOURCE coordinates via the inverted transform. */
private fun ocrTransformed(src: Bitmap, m: Matrix): List<Rect> {
    val bounds = RectF(0f, 0f, src.width.toFloat(), src.height.toFloat())
    m.mapRect(bounds)
    // Same translation Bitmap.createBitmap(src, m) would bake in: keep all pixels positive.
    val full = Matrix(m).apply { postTranslate(-bounds.left, -bounds.top) }
    val raster = Bitmap.createBitmap(bounds.width().toInt(), bounds.height().toInt(), Bitmap.Config.ARGB_8888)
    Canvas(raster).drawBitmap(src, full, Paint(Paint.FILTER_BITMAP_FLAG))
    val inverse = Matrix().also { full.invert(it) }
    return try {
        ocrPlateBoxes(raster).map { r ->
            val pts = floatArrayOf(
                r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.top.toFloat(),
                r.right.toFloat(), r.bottom.toFloat(), r.left.toFloat(), r.bottom.toFloat(),
            )
            inverse.mapPoints(pts)
            val xs = listOf(pts[0], pts[2], pts[4], pts[6])
            val ys = listOf(pts[1], pts[3], pts[5], pts[7])
            Rect(
                xs.min().toInt().coerceAtLeast(0), ys.min().toInt().coerceAtLeast(0),
                xs.max().toInt().coerceAtMost(src.width), ys.max().toInt().coerceAtMost(src.height),
            )
        }
    } finally {
        raster.recycle()
    }
}

/** One OCR pass over [bmp]; boxes are in [bmp]'s own coordinates. */
private fun ocrPlateBoxes(bmp: Bitmap): List<Rect> {
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    return try {
        val text = Tasks.await(recognizer.process(InputImage.fromBitmap(bmp, 0)))
        buildList {
            for (block in text.textBlocks) {
                for (line in block.lines) {
                    val box = line.boundingBox ?: continue
                    if (looksLikePlate(line.text, box, bmp.height)) add(expand(box, bmp, 0.12f))
                }
            }
        }
    } finally {
        recognizer.close()
    }
}

/** A plate-like line: 4–10 alphanumerics with ≥2 digits, wide aspect ratio, modest height. */
internal fun looksLikePlate(text: String, box: Rect, imgHeight: Int): Boolean {
    val t = text.trim()
    val alnum = t.count(Char::isLetterOrDigit)
    if (alnum < 4 || alnum > 10 || t.count(Char::isDigit) < 2) return false
    val h = box.height().toFloat()
    if (h <= 0f || imgHeight <= 0) return false
    val ar = box.width().toFloat() / h
    if (ar < 2.0f || ar > 7.0f) return false
    return (h / imgHeight.toFloat()) in 0.015f..0.30f
}

private fun expand(r: Rect, bmp: Bitmap, frac: Float): Rect {
    val dx = (r.width() * frac).toInt()
    val dy = (r.height() * frac).toInt()
    return Rect(
        (r.left - dx).coerceAtLeast(0), (r.top - dy).coerceAtLeast(0),
        (r.right + dx).coerceAtMost(bmp.width), (r.bottom + dy).coerceAtMost(bmp.height),
    )
}

private fun solidBox(canvas: Canvas, r: Rect) {
    canvas.drawRect(RectF(r), Paint().apply { color = Color.BLACK })
}

/** Pixelate a region (scale a crop down to 8×8 then back up); irreversible once re-encoded. */
private fun pixelate(bmp: Bitmap, canvas: Canvas, r: Rect) {
    if (r.width() <= 1 || r.height() <= 1) return
    val crop = Bitmap.createBitmap(bmp, r.left, r.top, r.width(), r.height())
    val small = Bitmap.createScaledBitmap(crop, 8, 8, false)
    val mosaic = Bitmap.createScaledBitmap(small, r.width(), r.height(), false)
    canvas.drawBitmap(mosaic, null, RectF(r), null)
    crop.recycle(); small.recycle(); mosaic.recycle()
}
