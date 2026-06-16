@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.stepanok.undp.core.ml

import com.stepanok.undp.domain.model.DamageTier
import platform.CoreML.MLModel
import platform.Foundation.NSBundle
import platform.Foundation.NSLog
import platform.UIKit.UIImage
import platform.Vision.VNClassificationObservation
import platform.Vision.VNCoreMLModel
import platform.Vision.VNCoreMLRequest
import platform.Vision.VNImageCropAndScaleOptionScaleFill
import platform.Vision.VNImageRequestHandler

private const val TAG = "BeaconDamage"

private fun tierOf(identifier: String): DamageTier? = when (identifier.lowercase()) {
    "minimal" -> DamageTier.MINIMAL
    "partial" -> DamageTier.PARTIAL
    "complete" -> DamageTier.COMPLETE
    else -> null
}

/**
 * iOS advisory damage classifier (B2): on-device, OFFLINE Core ML (MobileNetV3-Small) inference via
 * Vision — the same VN* pattern as the B1 redactor. The model is an IMAGE-input classifier (Vision
 * scales the photo to 224×224 with ScaleFill to match how it was trained; preprocessing is baked
 * into the model). Abstains below the 0.45 floor, on a missing model, or any failure; never throws.
 */
private class IosDamageClassifier : DamageClassifier {

    // Lazy, single VNCoreMLModel load; null when the compiled model isn't bundled (→ abstain).
    private val visionModel: VNCoreMLModel? by lazy {
        runCatching {
            val url = NSBundle.mainBundle.URLForResource("DamageClassifier", withExtension = "mlmodelc")
                ?: return@runCatching null
            val ml = MLModel.modelWithContentsOfURL(url, null) ?: return@runCatching null
            VNCoreMLModel.modelForMLModel(ml, null)
        }.onFailure { NSLog("$TAG: Core ML model load failed: ${it.message}") }.getOrNull()
    }

    override suspend fun classify(imagePath: String): DamageSuggestion {
        val model = visionModel ?: return DamageSuggestion()
        return runCatching {
            val cg = UIImage(contentsOfFile = imagePath)?.CGImage ?: return@runCatching DamageSuggestion()
            val request = VNCoreMLRequest(model)
            request.imageCropAndScaleOption = VNImageCropAndScaleOptionScaleFill
            VNImageRequestHandler(cGImage = cg, options = emptyMap<Any?, Any?>())
                .performRequests(listOf(request), null)
            val top = request.results?.firstOrNull() as? VNClassificationObservation
                ?: return@runCatching DamageSuggestion()
            val tier = tierOf(top.identifier) ?: return@runCatching DamageSuggestion()
            val confidence = top.confidence
            NSLog("$TAG: ${top.identifier} conf=$confidence")
            if (confidence < AI_CONFIDENCE_FLOOR) DamageSuggestion() else DamageSuggestion(tier, confidence)
        }.getOrDefault(DamageSuggestion())
    }
}

actual fun createDamageClassifier(): DamageClassifier = IosDamageClassifier()
