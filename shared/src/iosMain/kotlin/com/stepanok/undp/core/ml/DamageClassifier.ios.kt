package com.stepanok.undp.core.ml

/**
 * iOS advisory damage classifier (B2). The on-device Core ML model (`DamageClassifier.mlmodelc`
 * in the app bundle) + Vision inference is loaded once the trained model ships (B2 fallback
 * ladder). Until then this ABSTAINS — the capture flow is unaffected and the AI banner never
 * appears (the report's `aiLevel`/`aiConfidence` stay null). Never throws.
 *
 * The send-path, UX, and backend ingest of `aiLevel`/`aiConfidence` are already wired, so adding
 * the model is a pure asset + inference-fill-in swap; nothing else changes.
 */
private class IosDamageClassifier : DamageClassifier {
    override suspend fun classify(imagePath: String): DamageSuggestion = DamageSuggestion()
}

actual fun createDamageClassifier(): DamageClassifier = IosDamageClassifier()
