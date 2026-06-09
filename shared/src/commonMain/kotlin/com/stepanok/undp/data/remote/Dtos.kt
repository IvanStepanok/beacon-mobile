package com.stepanok.undp.data.remote

import kotlinx.serialization.Serializable

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

/** Optional Appendix-1 modular sections on the wire. Keys are EXACTLY electricity /
 *  healthServices / pressingNeeds (camelCase); values are the enum names lowercased. */
@Serializable
data class ModularDto(
    val electricity: String? = null,
    val healthServices: String? = null,
    val pressingNeeds: List<String> = emptyList(),
)

@Serializable
data class ReportDto(
    val id: String,
    val idempotencyKey: String = "",
    val damage: String,
    val damageTier: String = "minimal",
    val possiblyDamaged: Boolean = false,
    val infraTypes: List<String> = emptyList(),
    val crisisNature: List<String> = emptyList(),
    val debris: String = "unsure",
    val lat: Double? = null,
    val lng: Double? = null,
    val locationResolved: Boolean = true,
    val accuracyMeters: Double? = null,
    val buildingId: String? = null,
    val what3words: String? = null,
    val place: String = "",
    val version: Int = 1,
    val supersedesReportId: String? = null,
    val description: ReportDescriptionDto? = null,
    val aiLevel: String? = null,
    val aiConfidence: Int? = null,
    val sizeBytes: Long = 0,
    val synced: Boolean = true,
    val isMine: Boolean = false,
    val lifeSafety: Boolean = false,
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
data class DangerZoneDto(
    val id: String,
    val name: String,
    val note: String,
    val severity: String,
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
    val damage: String,
    val possiblyDamaged: Boolean,
    val infraTypes: List<String>,
    val infraOtherDetail: String? = null,
    val crisisNature: List<String>,
    val debris: String,
    // Null lat/lng + locationResolved=false marks a landmark-only (location-unresolved) report —
    // we never fabricate a 0/0 point. accuracyMeters is the horizontal GPS accuracy when a fix exists.
    val lat: Double? = null,
    val lng: Double? = null,
    val locationResolved: Boolean = true,
    val accuracyMeters: Double? = null,
    val buildingId: String? = null,
    val what3words: String? = null,
    val landmark: String? = null,
    val place: String,
    val description: ReportDescriptionDto? = null,
    val modular: ModularDto? = null,
    val lifeSafety: Boolean = false,
    val capturedAt: String,
)

@Serializable
data class PointsRequestDto(val points: Int, val reason: String)

@Serializable
data class ConfigDto(val damageScale: String = "tier3")
