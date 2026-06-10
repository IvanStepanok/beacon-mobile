package com.stepanok.undp.core.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * On-device, OFFLINE face + license-plate redaction (B1). Detects regions on [src], paints
 * over them on a mutable copy, and returns (redacted bitmap, result). Faces use ML Kit Face
 * Detection (BUNDLED offline model — reliable, no network/Play-Services download); plates use
 * ML Kit Text Recognition + an aspect-ratio/character heuristic (best-effort). MUST run OFF the
 * main thread — `Tasks.await` blocks. Never throws: on any failure returns (src, all-false) so
 * the photo is still stored (EXIF already stripped) — privacy is never worse than before.
 */
internal fun redactBitmap(src: Bitmap): Pair<Bitmap, RedactionResult> = runCatching {
    val faces = detectFaces(src)
    val plates = detectPlates(src)
    if (faces.isEmpty() && plates.isEmpty()) {
        // Detectors ran and found nothing to redact — certify processed, keep original pixels.
        return@runCatching src to RedactionResult(facesRedacted = true, platesRedacted = true)
    }
    val out = if (src.isMutable) src else src.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(out)
    faces.forEach { pixelate(out, canvas, it) }
    plates.forEach { solidBox(canvas, it) }
    out to RedactionResult(
        facesFound = faces.size, platesFound = plates.size,
        facesRedacted = true, platesRedacted = true,
    )
}.getOrDefault(src to RedactionResult())

private fun detectFaces(bmp: Bitmap): List<Rect> = runCatching {
    val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .build(),
    )
    try {
        Tasks.await(detector.process(InputImage.fromBitmap(bmp, 0)))
            .map { expand(it.boundingBox, bmp, 0.18f) }
    } finally {
        detector.close()
    }
}.getOrDefault(emptyList())

private fun detectPlates(bmp: Bitmap): List<Rect> = runCatching {
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    try {
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
}.getOrDefault(emptyList())

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
