package com.stepanok.undp.domain.model

/** Reference to a captured photo — local now, gains [remoteUrl] after upload. */
data class PhotoRef(
    val localPath: String,
    val remoteUrl: String? = null,
    val sizeBytes: Long = 0L,
)

/** Where the report is — footprint-snapped if possible, with GPS / what3words / landmark fallbacks.
 *
 *  [lat]/[lng] are NULL for landmark-only (location-unresolved) reports, where [locationResolved] is
 *  false and the [landmark] text is the only locator. We never fabricate a 0/0 point. When a point is
 *  resolved, [locationResolved] is true and [gpsAccuracyMeters] holds the horizontal accuracy (if known). */
data class ReportLocation(
    val lat: Double? = null,
    val lng: Double? = null,
    val locationResolved: Boolean = true,
    val buildingId: String? = null,
    val what3words: String? = null,
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

/** Optional Appendix-1 modular sections. */
data class ModularSections(
    val electricity: ElectricityCondition? = null,
    val healthServices: HealthServices? = null,
    val pressingNeeds: Set<PressingNeed> = emptySet(),
)

/** Privacy guarantees applied on-device before a photo is queued.
 *
 *  Honesty: only [exifStripped] is actually implemented. Face/plate blurring is NOT yet
 *  implemented, so those flags default to false — the app must not claim a guarantee it
 *  does not deliver. Flip them to true only when on-device blurring actually runs. */
data class Anonymization(
    val anonymous: Boolean = true,
    val exifStripped: Boolean = true,
    val facesBlurred: Boolean = false,
    val platesBlurred: Boolean = false,
)
