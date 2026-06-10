package com.stepanok.undp.domain.model

/** Reference to a captured photo — local now, gains [remoteUrl] after upload. */
data class PhotoRef(
    val localPath: String,
    val remoteUrl: String? = null,
    val sizeBytes: Long = 0L,
)

/** Where the report is — footprint-snapped if possible, with GPS / Plus Code / landmark fallbacks.
 *
 *  [lat]/[lng] are NULL for landmark-only (location-unresolved) reports, where [locationResolved] is
 *  false and the [landmark] text is the only locator. We never fabricate a 0/0 point. When a point is
 *  resolved, [locationResolved] is true and [gpsAccuracyMeters] holds the horizontal accuracy (if known).
 *
 *  [buildingId] is set ONLY when a real footprint polygon was tapped (the stable fp- id from the map);
 *  [buildingSource] is then "footprint". GPS-only / bare-tap reports carry null for both — we never
 *  fabricate a coordinate-grid building identity (that would defeat the server's near-dup guard). */
data class ReportLocation(
    val lat: Double? = null,
    val lng: Double? = null,
    val locationResolved: Boolean = true,
    val buildingId: String? = null,
    val buildingSource: String? = null,
    val plusCode: String? = null,
    val landmark: String? = null,
    val gpsAccuracyMeters: Double? = null,
)

/** Free-text note plus its translation, so analysts see the original and the translated view. */
data class ReportDescription(
    val original: String,
    val originalLang: String,
    val translated: String? = null,
    val translatedLang: String? = null,
)

/** Optional modular section answers, keyed by the form-schema section key (camelCase wire key).
 *
 *  Dynamic by design: a section UNDP adds server-side flows through to the wire blob (and the
 *  backend's dynamic export flattening) untouched. [single] holds single-choice answers
 *  (key → selected wire value), [multi] the multi-select answers (key → values in schema order),
 *  [otherTexts] each section's "Other → specify" free text (serialized as "<key>Other" on the
 *  wire, e.g. pressingNeedsOther). */
data class ModularSections(
    val single: Map<String, String> = emptyMap(),
    val multi: Map<String, List<String>> = emptyMap(),
    val otherTexts: Map<String, String> = emptyMap(),
) {
    /** Convenience accessors for the three built-in Appendix-1 sections. */
    val electricity: String? get() = single["electricity"]
    val healthServices: String? get() = single["healthServices"]
    val pressingNeeds: List<String> get() = multi["pressingNeeds"].orEmpty()
    val pressingNeedsOther: String? get() = otherTexts["pressingNeeds"]

    val isEmpty: Boolean get() = single.isEmpty() && multi.isEmpty() && otherTexts.isEmpty()
}

/** Privacy guarantees applied on-device before a photo is queued.
 *
 *  [exifStripped] always runs (downscale + re-encode strips ALL EXIF). [facesBlurred] /
 *  [platesBlurred] are set PER-PHOTO from the on-device, offline redactor (Android: ML Kit
 *  Face Detection — reliable — plus a license-plate text heuristic — best-effort) — true only
 *  when that detector actually ran on the photo, so the flags never overclaim. iOS redaction
 *  (Apple Vision) is a fast-follow; until then iOS photos are EXIF-stripped with these flags
 *  false (honest). */
data class Anonymization(
    val anonymous: Boolean = true,
    val exifStripped: Boolean = true,
    val facesBlurred: Boolean = false,
    val platesBlurred: Boolean = false,
)
