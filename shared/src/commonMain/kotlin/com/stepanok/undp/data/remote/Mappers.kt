@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.stepanok.undp.data.remote

import com.stepanok.undp.domain.model.AreaGroup
import com.stepanok.undp.domain.model.Badge
import com.stepanok.undp.domain.model.BuildingTimeline
import com.stepanok.undp.domain.model.BuildingVersion
import com.stepanok.undp.domain.model.Crisis
import com.stepanok.undp.domain.model.CrisisNature
import com.stepanok.undp.domain.model.DamageTier
import com.stepanok.undp.domain.model.DebrisState
import com.stepanok.undp.domain.model.FormOption
import com.stepanok.undp.domain.model.FormSection
import com.stepanok.undp.domain.model.FormSectionType
import com.stepanok.undp.domain.model.InfraType
import com.stepanok.undp.domain.model.ModularSections
import com.stepanok.undp.domain.model.Profile
import com.stepanok.undp.domain.model.Report
import com.stepanok.undp.domain.model.ReportDescription
import com.stepanok.undp.domain.model.ReportLocation
import com.stepanok.undp.domain.model.SyncState
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.time.Clock
import kotlin.time.Instant

// ── enum + time helpers (lowercase wire ↔ UPPERCASE enum), tolerant of unknowns ──

// The mandated 3-tier classification. Tolerant of legacy 5-level grades a not-yet-migrated
// server might still emit (none/slight → minimal, moderate/severe → partial, destroyed → complete).
private fun tier(s: String): DamageTier = when (s.lowercase()) {
    "minimal", "none", "slight" -> DamageTier.MINIMAL
    "partial", "moderate", "severe" -> DamageTier.PARTIAL
    "complete", "destroyed" -> DamageTier.COMPLETE
    else -> DamageTier.MINIMAL
}

private fun infra(s: String): InfraType? = runCatching { InfraType.valueOf(s.uppercase()) }.getOrNull()

private fun nature(s: String): CrisisNature? = runCatching { CrisisNature.valueOf(s.uppercase()) }.getOrNull()

private fun debris(s: String): DebrisState =
    runCatching { DebrisState.valueOf(s.uppercase()) }.getOrDefault(DebrisState.UNSURE)

private fun instant(s: String): Instant = runCatching { Instant.parse(s) }.getOrElse { Clock.System.now() }

// ── DTO → domain ──────────────────────────────────────────────────────

fun ReportDto.toDomain(): Report = Report(
    id = id,
    idempotencyKey = idempotencyKey.ifBlank { "idem-$id" },
    damage = tier(damage),
    // Advisory on-device classifier suggestion (B2), if the server stored one.
    aiLevel = aiLevel?.let { tier(it) },
    aiConfidence = aiConfidence,
    possiblyDamaged = possiblyDamaged,
    infraTypes = infraTypes.mapNotNull(::infra).toSet(),
    infraName = infraName,
    crisisNature = crisisNature.mapNotNull(::nature).toSet(),
    debris = debris(debris),
    location = ReportLocation(
        lat = lat,
        lng = lng,
        // Resolved only when the server actually sent a point AND flagged it resolved.
        locationResolved = locationResolved && lat != null && lng != null,
        buildingId = buildingId,
        buildingSource = buildingSource,
        // Prefer the canonical plusCode; older servers only emit the legacy what3words name.
        plusCode = plusCode ?: what3words,
        landmark = landmark,
        gpsAccuracyMeters = gpsAccuracyMeters,
    ),
    description = description?.let {
        ReportDescription(it.original, it.originalLang, it.translated.ifBlank { null }, it.translatedLang)
    },
    capturedAt = instant(capturedAt),
    buildingId = buildingId,
    version = version,
    supersedesReportId = supersedesReportId,
    sync = if (synced) SyncState.Synced else SyncState.Queued,
    place = place,
    isMine = isMine,
)

fun CrisisDto.toDomain(): Crisis = Crisis(
    id = id, title = title, area = area,
    nature = nature(nature) ?: CrisisNature.EARTHQUAKE,
    startedAt = instant(startedAt),
    centerLat = centerLat, centerLng = centerLng, source = source,
    status = status, radiusKm = radiusKm, distanceKm = distanceKm, covers = covers ?: false,
)

fun AreaGroupDto.toDomain(): AreaGroup = AreaGroup(area, count, tier(worst))

fun BuildingTimelineDto.toDomain(): BuildingTimeline = BuildingTimeline(
    buildingId = buildingId,
    current = current?.let(::tier),
    versions = versions.map { BuildingVersion(it.reportId, tier(it.damage), instant(it.at), it.note, it.isCurrent) },
)

fun ProfileDto.toDomain(): Profile = Profile(
    anonymousId = anonymousId, alias = alias,
    reportCount = reportCount, buildingCount = buildingCount, points = points,
    badges = badges.map { Badge(it.id, it.name, it.earned, it.progressLabel) },
)

/** Sections without a key or options can't be rendered/answered — drop them defensively. */
fun FormSchemaDto.toDomain(): List<FormSection> =
    sections.filter { it.key.isNotBlank() && it.options.isNotEmpty() }.map { s ->
        FormSection(
            key = s.key,
            title = s.title,
            type = if (s.type.equals("multi", ignoreCase = true)) FormSectionType.MULTI else FormSectionType.SINGLE,
            required = s.required,
            allowOtherText = s.allowOtherText,
            options = s.options.map { FormOption(it.value, it.label) },
        )
    }

// ── modular blob ↔ domain (generic: server-added sections round-trip untouched) ──

/** The modular blob exactly as the server stores it: single-choice answers as bare strings,
 *  multi-select as arrays, "<key>Other" free-text companions (e.g. pressingNeedsOther). */
fun ModularSections.toWireBlob(): JsonObject = buildJsonObject {
    single.forEach { (key, value) -> put(key, value) }
    multi.forEach { (key, values) -> putJsonArray(key) { values.forEach { add(it) } } }
    otherTexts.forEach { (key, text) -> put("${key}Other", text) }
}

/** Rebuild [ModularSections] from a wire/persisted blob: bare strings are single-choice answers
 *  ("<key>Other" strings are free-text companions), arrays are multi-select. Tolerant of any
 *  shape (non-string JSON values are skipped); null when nothing usable remains. Pre-rename
 *  outbox files decode identically — the blob shape never changed. */
fun modularSectionsOf(blob: JsonObject): ModularSections? {
    val single = mutableMapOf<String, String>()
    val multi = mutableMapOf<String, List<String>>()
    val others = mutableMapOf<String, String>()
    blob.forEach { (key, element) ->
        when {
            element is JsonPrimitive && element.isString ->
                if (key.endsWith("Other") && key.length > "Other".length) {
                    others[key.removeSuffix("Other")] = element.content
                } else {
                    single[key] = element.content
                }
            element is JsonArray ->
                element.mapNotNull { (it as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content }
                    .takeIf { it.isNotEmpty() }?.let { multi[key] = it }
            else -> Unit
        }
    }
    return ModularSections(single, multi, others).takeUnless { it.isEmpty }
}

// ── domain → submit DTO ───────────────────────────────────────────────

fun Report.toSubmitDto(): SubmitReportDto = SubmitReportDto(
    id = id,
    idempotencyKey = idempotencyKey,
    // Crisis pin stamped from the map scope at enqueue time (null = server assigns spatially).
    crisisId = crisisId,
    // The mandated 3-tier classification (minimal|partial|complete).
    damage = damage.name.lowercase(),
    possiblyDamaged = possiblyDamaged,
    // Advisory on-device classifier suggestion (B2) — same tier vocabulary, lowercased.
    aiLevel = aiLevel?.name?.lowercase(),
    aiConfidence = aiConfidence,
    infraTypes = infraTypes.map { it.name.lowercase() },
    infraName = infraName,
    infraOtherDetail = infraOtherDetail,
    crisisNature = crisisNature.map { it.name.lowercase() },
    debris = debris.name.lowercase(),
    lat = location.lat,
    lng = location.lng,
    locationResolved = location.locationResolved,
    accuracyMeters = location.gpsAccuracyMeters,
    buildingId = buildingId,
    buildingSource = location.buildingSource,
    // Canonical plusCode; mirrored into the legacy what3words name for compat.
    plusCode = location.plusCode,
    what3words = location.plusCode,
    landmark = location.landmark,
    place = place.ifBlank { null },
    description = description?.let { ReportDescriptionDto(it.original, it.originalLang, it.translated ?: "", it.translatedLang) },
    modular = modular?.takeUnless { it.isEmpty }?.toWireBlob(),
    capturedAt = capturedAt.toString(),
)
