@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.stepanok.undp.core.media

import kotlin.test.Test
import kotlin.test.assertTrue
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation

/**
 * On-device verification of the iOS Apple Vision redaction (B1), running on the iOS simulator
 * exactly as production does. Reads the curated test photos from the host filesystem (the iOS
 * simulator shares the Mac's filesystem at the same absolute paths) and asserts the detectors
 * actually ran + found regions. Each test also writes the redacted JPEG back to disk so the
 * result can be inspected visually (faces pixelated in the correct place = the Vision→UIKit
 * coordinate flip is right).
 */
class ImageRedactorIosTest {

    private val dir = "/Users/ivanstepanok/Developer/RaccoonGang/MVP/UNDP/test-shots/blur"

    @Test
    fun facePhotoIsDetectedAndPixelated() {
        val data = NSData.dataWithContentsOfFile("$dir/face_damage.jpg")
            ?: throw AssertionError("face test image not readable from simulator: $dir/face_damage.jpg")
        val img = UIImage.imageWithData(data) ?: throw AssertionError("face image decode failed")

        val (out, result) = redactImage(img)
        UIImageJPEGRepresentation(out, 0.92)?.writeToFile("$dir/ios_redacted_face.jpg", true)

        assertTrue(result.facesRedacted, "face detector should have run (facesRedacted=true)")
        assertTrue(result.facesFound >= 1, "expected >=1 face detected, got ${result.facesFound}")
    }

    @Test
    fun platePhotoRunsTextDetector() {
        val data = NSData.dataWithContentsOfFile("$dir/car_plate.jpg")
            ?: throw AssertionError("plate test image not readable: $dir/car_plate.jpg")
        val img = UIImage.imageWithData(data) ?: throw AssertionError("plate image decode failed")

        val (out, result) = redactImage(img)
        UIImageJPEGRepresentation(out, 0.92)?.writeToFile("$dir/ios_redacted_plate.jpg", true)

        // Plate detection is best-effort; assert only that the text detector ran without throwing.
        assertTrue(result.platesRedacted, "text detector should have run (platesRedacted=true)")
    }
}
