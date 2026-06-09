@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.stepanok.undp.data.remote

import com.stepanok.undp.domain.model.AreaGroup
import com.stepanok.undp.domain.model.Badge
import com.stepanok.undp.domain.model.BuildingTimeline
import com.stepanok.undp.domain.model.BuildingVersion
import com.stepanok.undp.domain.model.Crisis
import com.stepanok.undp.domain.model.CrisisNature
import com.stepanok.undp.domain.model.DamageLevel
import com.stepanok.undp.domain.model.DamageTier
import com.stepanok.undp.domain.model.toTier
import com.stepanok.undp.domain.model.DangerSeverity
import com.stepanok.undp.domain.model.DangerZone
import com.stepanok.undp.domain.model.DebrisState
import com.stepanok.undp.domain.model.InfraType
import com.stepanok.undp.domain.model.Profile
import com.stepanok.undp.domain.model.Report
import com.stepanok.undp.domain.model.ReportDescription
import com.stepanok.undp.domain.model.ReportLocation
import com.stepanok.undp.domain.model.SyncState
import kotlin.time.Clock
import kotlin.time.Instant

// ── enum + time helpers (lowercase wire ↔ UPPERCASE enum), tolerant of unknowns ──

// Accepts EITHER vocabulary: a 5-level EMS-98 grade, or a 3-tier value (which we
// render with its representative 5-level grade so the existing UI displays sensibly).
private fun damage(s: String): DamageLevel = when (s.lowercase()) {
    "none" -> DamageLevel.NONE
    "slight" -> DamageLevel.SLIGHT
    "moderate" -> DamageLevel.MODERATE
    "severe" -> DamageLevel.SEVERE
    "destroyed" -> DamageLevel.DESTROYED
    "minimal" -> DamageLevel.NONE
    "partial" -> DamageLevel.MODERATE
    "complete" -> DamageLevel.DESTROYED
    else -> DamageLevel.NONE
}

private fun tier(s: String): DamageTier =
    runCatching { DamageTier.valueOf(s.uppercase()) }.getOrElse {
        runCatching { DamageLevel.valueOf(s.uppercase()).toTier() }.getOrDefault(DamageTier.MINIMAL)
    }

private fun infra(s: String): InfraType? = runCatching { InfraType.valueOf(s.uppercase()) }.getOrNull()

private fun nature(s: String): CrisisNature? = runCatching { CrisisNature.valueOf(s.uppercase()) }.getOrNull()

private fun debris(s: String): DebrisState =
    runCatching { DebrisState.valueOf(s.uppercase()) }.getOrDefault(DebrisState.UNSURE)

private fun severity(s: String): DangerSeverity =
    runCatching { DangerSeverity.valueOf(s.uppercase()) }.getOrDefault(DangerSeverity.CAUTION)

private fun instant(s: String): Instant = runCatching { Instant.parse(s) }.getOrElse { Clock.System.now() }

// ── DTO → domain ──────────────────────────────────────────────────────

fun ReportDto.toDomain(): Report = Report(
    id = id,
    idempotencyKey = idempotencyKey.ifBlank { "idem-$id" },
    damage = damage(damage),
    damageTier = tier(damageTier),
    possiblyDamaged = possiblyDamaged,
    lifeSafety = lifeSafety,
    infraTypes = infraTypes.mapNotNull(::infra).toSet(),
    crisisNature = crisisNature.mapNotNull(::nature).toSet(),
    debris = debris(debris),
    location = ReportLocation(
        lat = lat,
        lng = lng,
        // Resolved only when the server actually sent a point AND flagged it resolved.
        locationResolved = locationResolved && lat != null && lng != null,
        buildingId = buildingId,
        what3words = what3words,
        gpsAccuracyMeters = accuracyMeters,
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

fun DangerZoneDto.toDomain(): DangerZone = DangerZone(id, name, note, severity(severity))

fun AreaGroupDto.toDomain(): AreaGroup = AreaGroup(area, count, damage(worst))

fun BuildingTimelineDto.toDomain(): BuildingTimeline = BuildingTimeline(
    buildingId = buildingId,
    current = current?.let(::damage),
    versions = versions.map { BuildingVersion(it.reportId, damage(it.damage), instant(it.at), it.note, it.isCurrent) },
)

fun ProfileDto.toDomain(): Profile = Profile(
    anonymousId = anonymousId, alias = alias,
    reportCount = reportCount, buildingCount = buildingCount, points = points,
    badges = badges.map { Badge(it.id, it.name, it.earned, it.progressLabel) },
)

// ── domain → submit DTO ───────────────────────────────────────────────

fun Report.toSubmitDto(): SubmitReportDto = SubmitReportDto(
    id = id,
    idempotencyKey = idempotencyKey,
    // Send the chosen tier when the reporter used the 3-tier scale; else the EMS-98 grade.
    // The server accepts both and always derives the required 3-tier rollup.
    damage = (damageTier?.name ?: damage.name).lowercase(),
    possiblyDamaged = possiblyDamaged,
    infraTypes = infraTypes.map { it.name.lowercase() },
    infraOtherDetail = infraOtherDetail,
    crisisNature = crisisNature.map { it.name.lowercase() },
    debris = debris.name.lowercase(),
    lat = location.lat,
    lng = location.lng,
    locationResolved = location.locationResolved,
    accuracyMeters = location.gpsAccuracyMeters,
    buildingId = buildingId,
    what3words = location.what3words,
    landmark = location.landmark,
    place = place,
    description = description?.let { ReportDescriptionDto(it.original, it.originalLang, it.translated ?: "", it.translatedLang) },
    modular = modular?.let { m ->
        ModularDto(
            electricity = m.electricity?.name?.lowercase(),
            healthServices = m.healthServices?.name?.lowercase(),
            pressingNeeds = m.pressingNeeds.map { it.name.lowercase() },
        )
    },
    lifeSafety = lifeSafety,
    capturedAt = capturedAt.toString(),
)
