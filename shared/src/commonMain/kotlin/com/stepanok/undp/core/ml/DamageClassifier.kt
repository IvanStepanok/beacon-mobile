package com.stepanok.undp.core.ml

import com.stepanok.undp.domain.model.DamageTier

/** Confidence below which the classifier ABSTAINS — too weak to surface to the reporter. */
const val AI_CONFIDENCE_FLOOR: Float = 0.55f

/**
 * An advisory, on-device damage-tier suggestion (B2). [tier] == null means ABSTAIN — no model
 * bundled, an inference error, or confidence below [AI_CONFIDENCE_FLOOR]. [confidence] is 0f..1f.
 *
 * ADVISORY ONLY: this pre-highlights a tier for the reporter to confirm or change; it NEVER
 * auto-selects or auto-submits. The human grade stays authoritative (the brief's anti-"solely
 * generative AI" stance → human-in-the-loop).
 */
data class DamageSuggestion(val tier: DamageTier? = null, val confidence: Float = 0f) {
    val hasSuggestion: Boolean get() = tier != null
    /** Confidence as a 0–100 percent int for the wire (`aiConfidence`) and the UX banner. */
    val confidencePercent: Int get() = (confidence * 100f).toInt().coerceIn(0, 100)
}

/**
 * On-device, OFFLINE advisory damage classifier. Android = LiteRT/TFLite; iOS = Core ML. Runs
 * entirely on-device with no network — essential for the low/no-connectivity setting. NEVER
 * throws: returns an abstaining [DamageSuggestion] on ANY failure so the capture flow is never
 * blocked. The backend already accepts + stores the resulting `aiLevel`/`aiConfidence`.
 */
interface DamageClassifier {
    /**
     * Classify the photo at [imagePath]. Returns an abstaining suggestion (tier = null) on any
     * failure, when no model is bundled, or when confidence is below [AI_CONFIDENCE_FLOOR].
     */
    suspend fun classify(imagePath: String): DamageSuggestion
}

expect fun createDamageClassifier(): DamageClassifier
