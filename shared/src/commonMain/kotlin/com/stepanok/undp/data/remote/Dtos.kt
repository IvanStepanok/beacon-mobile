package com.stepanok.undp.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Wire DTOs mirroring the Go backend's camelCase JSON. The client uses
 *  ignoreUnknownKeys, so analyst-only fields the mobile doesn't model are dropped. */

@Serializable
data class ItemsDto<T>(val items: List<T> = emptyList())

@Serializable
data class ReportDescriptionDto(
    val original: String = "",
    val originalLang: String = "",
    val translated: String = "",
    val translatedLang: String? = null,
)

@Serializable
data class ReportDto(
    val id: String,
    val idempotencyKey: String = "",
    val damage: String,
    val possiblyDamaged: Boolean = false,
    val infraTypes: List<String> = emptyList(),
    val infraName: String? = null,
    val crisisNature: List<String> = emptyList(),
    val debris: String = "unsure",
    val lat: Double? = null,
    val lng: Double? = null,
    val locationResolved: Boolean = true,
    // The server EMITS gpsAccuracyMeters (accuracyMeters is a submit-only alias it accepts) —
    // reading the wrong name silently dropped accuracy for every fetched report.
    val gpsAccuracyMeters: Double? = null,
    val buildingId: String? = null,
    val buildingSource: String? = null,
    // Open Location Code. The server emits BOTH plusCode and the legacy misnamed
    // what3words (same value, kept for compat) — prefer plusCode, fall back to legacy.
    val plusCode: String? = null,
    val what3words: String? = null,
    // Flat landmark (the server also nests it under location) — without it a fetched
    // landmark-only report had no readable locator at all.
    val landmark: String? = null,
    val place: String = "",
    val version: Int = 1,
    val supersedesReportId: String? = null,
    val description: ReportDescriptionDto? = null,
    val aiLevel: String? = null,
    val aiConfidence: Int? = null,
    val sizeBytes: Long = 0,
    val synced: Boolean = true,
    val isMine: Boolean = false,
    val capturedAt: String,
)

@Serializable
data class CrisisDto(
    val id: String,
    val title: String,
    val area: String,
    val nature: String = "earthquake",
    val centerLat: Double = 0.0,
    val centerLng: Double = 0.0,
    val source: String = "UNDP RAPIDA",
    val startedAt: String,
    val glide: String? = null,
    val responseLevel: Int? = null,
    val status: String = "active",
    val radiusKm: Double = 0.0,
    val distanceKm: Double? = null,
    val covers: Boolean? = null,
)

@Serializable
data class AreaGroupDto(
    val area: String,
    val count: Int,
    val worst: String,
)

@Serializable
data class BuildingVersionDto(
    val reportId: String,
    val v: Int = 1,
    val damage: String,
    val at: String,
    val isCurrent: Boolean = false,
    val by: String = "",
    val note: String = "",
)

@Serializable
data class BuildingTimelineDto(
    val buildingId: String = "",
    val current: String? = null,
    val versions: List<BuildingVersionDto> = emptyList(),
)

@Serializable
data class BadgeDto(
    val id: String = "",
    val name: String = "",
    val earned: Boolean = false,
    val progressLabel: String? = null,
)

@Serializable
data class ProfileDto(
    val anonymousId: String = "",
    val alias: String? = null,
    val reportCount: Int = 0,
    val buildingCount: Int = 0,
    val points: Int = 0,
    val badges: List<BadgeDto> = emptyList(),
)

/** Outbound submit payload (subset the backend accepts; server stamps the rest). */
@Serializable
data class SubmitReportDto(
    val id: String,
    val idempotencyKey: String,
    // Explicit crisis pin from the active map scope — the server keeps it for landmark-only
    // (no-coords) reports, which spatial assignment could never place. Null/omitted = the
    // server assigns by space+time (we never fabricate a pin without a scope).
    val crisisId: String? = null,
    val damage: String,
    val possiblyDamaged: Boolean,
    /** Advisory, on-device classifier suggestion (B2): the model's tier + confidence (0–100),
     *  stored ALONGSIDE the human [damage]. Omitted when the model abstained / no model. The
     *  backend already validates + persists ai_level / ai_confidence. */
    val aiLevel: String? = null,
    val aiConfidence: Int? = null,
    val infraTypes: List<String>,
    /** Name/details of the infrastructure (any type), e.g. "Cumhuriyet Primary School". */
    val infraName: String? = null,
    val infraOtherDetail: String? = null,
    val crisisNature: List<String>,
    val debris: String,
    // Null lat/lng + locationResolved=false marks a landmark-only (location-unresolved) report —
    // we never fabricate a 0/0 point. accuracyMeters is the horizontal GPS accuracy when a fix exists.
    val lat: Double? = null,
    val lng: Double? = null,
    val locationResolved: Boolean = true,
    val accuracyMeters: Double? = null,
    // buildingId only when a real footprint was tapped — buildingSource is then "footprint".
    val buildingId: String? = null,
    val buildingSource: String? = null,
    // plusCode is the canonical Open Location Code field; the legacy misnamed what3words
    // carries the same value so not-yet-migrated servers (and old persisted outbox files)
    // keep working. The server prefers plusCode, falling back to what3words.
    val plusCode: String? = null,
    val what3words: String? = null,
    val landmark: String? = null,
    // Null when unknown — the client never fabricates a place label ("Your location").
    val place: String? = null,
    val description: ReportDescriptionDto? = null,
    // The modular blob exactly as the server stores it: single-choice answers as bare strings
    // (e.g. electricity), multi-select as arrays (pressingNeeds), "<key>Other" free-text
    // companions (pressingNeedsOther). A raw JsonObject — not a fixed DTO — so answers to
    // sections UNDP adds server-side flow through untouched.
    val modular: JsonObject? = null,
    val capturedAt: String,
)

/** The backend's JSON error envelope, returned with every non-2xx status. */
@Serializable
data class ErrorEnvelopeDto(val error: String = "", val message: String = "")

/** GET /form-schema — the resolved modular capture form (built-in Appendix-1 defaults with
 *  the crisis's required/disabled overrides applied). */
@Serializable
data class FormOptionDto(val value: String = "", val label: String = "")

@Serializable
data class FormSectionDto(
    val key: String = "",
    val title: String = "",
    val type: String = "single", // single | multi
    val required: Boolean = false,
    val allowOtherText: Boolean = false,
    val options: List<FormOptionDto> = emptyList(),
)

@Serializable
data class FormSchemaDto(val sections: List<FormSectionDto> = emptyList())
