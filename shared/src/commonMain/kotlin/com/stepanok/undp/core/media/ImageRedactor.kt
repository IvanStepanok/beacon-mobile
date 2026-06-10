package com.stepanok.undp.core.media

/**
 * Outcome of on-device, OFFLINE photo redaction for ONE captured image. Each flag is
 * true ONLY when that detector actually RAN on this photo (and painted over any regions
 * it found) — so the report's [com.stepanok.undp.domain.model.Anonymization] flags never
 * overclaim a guarantee the build did not deliver.
 *
 * Faces are reliable (ML Kit Face Detection on Android, bundled offline model); license
 * plates are best-effort (a text-recognition + aspect-ratio heuristic). Redaction runs
 * entirely on-device with no network — essential for the low/no-connectivity setting —
 * inside the existing JPEG re-encode pass, so the redacted pixels are what gets stored
 * (irreversible). On any failure the result is all-false and the photo is still stored
 * (EXIF already stripped): privacy is never WORSE than before redaction existed.
 *
 * NOTE: the actual detection/blur is a platform implementation (Android: ML Kit; iOS:
 * Apple Vision — pending). This type is the only piece that crosses into common code,
 * because it must flow into the domain Anonymization record at submit time.
 */
data class RedactionResult(
    val facesFound: Int = 0,
    val platesFound: Int = 0,
    val facesRedacted: Boolean = false,
    val platesRedacted: Boolean = false,
)
