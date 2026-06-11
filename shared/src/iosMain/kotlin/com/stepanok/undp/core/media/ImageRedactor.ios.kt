@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.stepanok.undp.core.media

import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGImageCreateWithImageInRect
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGImageRef
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.CoreGraphics.CGContextSetInterpolationQuality
import platform.CoreGraphics.kCGInterpolationNone
import platform.Foundation.NSData
import platform.Foundation.NSLog
import platform.UIKit.UIColor
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetCurrentContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageOrientation
import platform.UIKit.UIRectFill
import platform.Vision.VNDetectFaceRectanglesRequest
import platform.Vision.VNFaceObservation
import platform.Vision.VNImageRequestHandler
import platform.Vision.VNRecognizeTextRequest
import platform.Vision.VNRecognizedText
import platform.Vision.VNRecognizedTextObservation

private const val TAG = "BeaconRedact"

/** A redaction region in TOP-LEFT pixel coordinates of the upright image. */
internal data class PixRect(val x: Double, val y: Double, val w: Double, val h: Double)

/**
 * iOS on-device, OFFLINE face + license-plate redaction (B1) — the iOS counterpart of the
 * Android ML Kit path. Faces use Apple Vision [VNDetectFaceRectanglesRequest] (reliable);
 * plates use [VNRecognizeTextRequest] + the same aspect-ratio/character heuristic (best-effort).
 * Runs fully on-device via system frameworks (no network, no extra binary size).
 *
 * HONESTY: a detector that THREW (vs. ran and found nothing) leaves its `*Redacted` flag FALSE
 * so the report's [com.stepanok.undp.domain.model.Anonymization] never claims a guarantee that
 * did not run. The photo is always still encoded — privacy is never WORSE than before.
 *
 * Coordinate note: Vision returns NORMALIZED rects with origin at the BOTTOM-left; UIKit/CGImage
 * crop space is TOP-left, so [toPixRect] flips Y. We normalize the input to an upright image first
 * so the CGImage pixels match what Vision sees and what we draw.
 *
 * Detection runs through Vision's JPEG-DATA handler, not the CGImage handler: the CGImage-backed
 * VNImageRequestHandler under-detects on decoded bitmaps (observed: 0 faces on a clear frontal
 * face, while the data handler finds it). We encode the upright image to JPEG once and detect on
 * that; the normalized rects map straight back onto the same upright pixels we then draw on.
 */
internal fun redactImage(input: UIImage): Pair<UIImage, RedactionResult> {
    val image = normalizedUpright(input)
    val cg = image.CGImage ?: return image to RedactionResult()
    val w = CGImageGetWidth(cg).toDouble()
    val h = CGImageGetHeight(cg).toDouble()
    if (w < 1.0 || h < 1.0) return image to RedactionResult()

    val jpeg = UIImageJPEGRepresentation(image, 0.95)

    // null == the detector threw / no data (logged); empty == it ran and found nothing.
    val faces: List<PixRect>? = jpeg?.let { d ->
        runCatching { detectFaces(d, w, h) }
            .onFailure { NSLog("$TAG: face detection failed: ${it.message}") }.getOrNull()
    }
    val plates: List<PixRect>? = jpeg?.let { d ->
        runCatching { detectPlates(d, w, h) }
            .onFailure { NSLog("$TAG: plate detection failed: ${it.message}") }.getOrNull()
    }

    val faceList = faces.orEmpty()
    val plateList = plates.orEmpty()
    NSLog("$TAG: redact ${w.toInt()}x${h.toInt()}: faces=${faces?.size ?: -1} plates=${plates?.size ?: -1}")

    if (faceList.isEmpty() && plateList.isEmpty()) {
        return image to RedactionResult(facesRedacted = faces != null, platesRedacted = plates != null)
    }

    // Precompute the pixelated face tiles BEFORE opening the output context (no nested contexts).
    val faceTiles = faceList.mapNotNull { r -> pixelatedTile(cg, r)?.let { it to r } }

    UIGraphicsBeginImageContextWithOptions(CGSizeMake(w, h), true, 1.0)
    image.drawInRect(CGRectMake(0.0, 0.0, w, h))
    UIGraphicsGetCurrentContext()?.let { CGContextSetInterpolationQuality(it, kCGInterpolationNone) }
    faceTiles.forEach { (tile, r) -> tile.drawInRect(CGRectMake(r.x, r.y, r.w, r.h)) }
    if (plateList.isNotEmpty()) {
        UIColor(red = 0.0, green = 0.0, blue = 0.0, alpha = 1.0).setFill()
        plateList.forEach { r -> UIRectFill(CGRectMake(r.x, r.y, r.w, r.h)) }
    }
    val out = UIGraphicsGetImageFromCurrentImageContext() ?: image
    UIGraphicsEndImageContext()

    return out to RedactionResult(
        facesFound = faceList.size, platesFound = plateList.size,
        facesRedacted = faces != null, platesRedacted = plates != null,
    )
}

/** Redraw to an upright (orientation == .up) image so CGImage pixels match Vision + our drawing. */
private fun normalizedUpright(image: UIImage): UIImage {
    if (image.imageOrientation == UIImageOrientation.UIImageOrientationUp) return image
    return image.size.useContents {
        UIGraphicsBeginImageContextWithOptions(CGSizeMake(width, height), false, image.scale)
        image.drawInRect(CGRectMake(0.0, 0.0, width, height))
        val r = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()
        r ?: image
    }
}

/** Detect faces on the JPEG data. THROWS on Vision failure (caught + logged by [redactImage]). */
private fun detectFaces(jpeg: NSData, w: Double, h: Double): List<PixRect> {
    val req = VNDetectFaceRectanglesRequest()
    VNImageRequestHandler(data = jpeg, options = emptyMap<Any?, Any?>())
        .performRequests(listOf(req), null)
    return (req.results ?: emptyList<Any?>()).mapNotNull { o ->
        val f = o as? VNFaceObservation ?: return@mapNotNull null
        f.boundingBox.useContents { toPixRect(origin.x, origin.y, size.width, size.height, w, h, 0.22) }
    }
}

/** Detect plate-like text on the JPEG data. THROWS on Vision failure (caught by [redactImage]). */
private fun detectPlates(jpeg: NSData, w: Double, h: Double): List<PixRect> {
    // Default recognition level (.accurate) — best-effort plate text; runs off the main thread.
    val req = VNRecognizeTextRequest()
    VNImageRequestHandler(data = jpeg, options = emptyMap<Any?, Any?>())
        .performRequests(listOf(req), null)
    return (req.results ?: emptyList<Any?>()).mapNotNull { o ->
        val t = o as? VNRecognizedTextObservation ?: return@mapNotNull null
        val str = (t.topCandidates(1u).firstOrNull() as? VNRecognizedText)?.string ?: return@mapNotNull null
        t.boundingBox.useContents {
            val rw = size.width * w
            val rh = size.height * h
            if (looksLikePlateIos(str, rw, rh, h)) {
                toPixRect(origin.x, origin.y, size.width, size.height, w, h, 0.14)
            } else {
                null
            }
        }
    }
}

/** A plate-like line: 4–10 alphanumerics with ≥2 digits, wide aspect ratio, modest height. */
internal fun looksLikePlateIos(text: String, boxW: Double, boxH: Double, imgH: Double): Boolean {
    val t = text.trim()
    val alnum = t.count(Char::isLetterOrDigit)
    if (alnum < 4 || alnum > 10 || t.count(Char::isDigit) < 2) return false
    if (boxH <= 0.0 || imgH <= 0.0) return false
    val ar = boxW / boxH
    if (ar < 2.0 || ar > 7.0) return false
    return (boxH / imgH) in 0.015..0.30
}

/** Vision normalized (bottom-left origin) → expanded TOP-left pixel rect, clamped to the image. */
private fun toPixRect(ox: Double, oy: Double, ow: Double, oh: Double, w: Double, h: Double, frac: Double): PixRect {
    var rw = ow * w
    var rh = oh * h
    var x = ox * w - rw * frac
    var y = (1.0 - oy - oh) * h - rh * frac
    rw += rw * frac * 2.0
    rh += rh * frac * 2.0
    if (x < 0.0) { rw += x; x = 0.0 }
    if (y < 0.0) { rh += y; y = 0.0 }
    if (x + rw > w) rw = w - x
    if (y + rh > h) rh = h - y
    return PixRect(x, y, rw, rh)
}

/** Crop the region and downsample to a tiny image → a blocky mosaic when drawn back large. */
private fun pixelatedTile(cg: CGImageRef, r: PixRect): UIImage? {
    if (r.w < 2.0 || r.h < 2.0) return null
    val crop = CGImageCreateWithImageInRect(cg, CGRectMake(r.x, r.y, r.w, r.h)) ?: return null
    val sw = 9.0
    val sh = 9.0
    UIGraphicsBeginImageContextWithOptions(CGSizeMake(sw, sh), true, 1.0)
    UIImage.imageWithCGImage(crop).drawInRect(CGRectMake(0.0, 0.0, sw, sh))
    val tile = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()
    return tile
}
