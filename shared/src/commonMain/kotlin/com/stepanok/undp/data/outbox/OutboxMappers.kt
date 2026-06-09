@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.stepanok.undp.data.outbox

import com.stepanok.undp.data.remote.ReportDescriptionDto
import com.stepanok.undp.data.remote.toSubmitDto
import com.stepanok.undp.domain.model.CrisisNature
import com.stepanok.undp.domain.model.DamageLevel
import com.stepanok.undp.domain.model.DamageTier
import com.stepanok.undp.domain.model.DebrisState
import com.stepanok.undp.domain.model.ElectricityCondition
import com.stepanok.undp.domain.model.HealthServices
import com.stepanok.undp.domain.model.InfraType
import com.stepanok.undp.domain.model.ModularSections
import com.stepanok.undp.domain.model.PhotoRef
import com.stepanok.undp.domain.model.PressingNeed
import com.stepanok.undp.domain.model.Report
import com.stepanok.undp.domain.model.ReportDescription
import com.stepanok.undp.domain.model.ReportLocation
import com.stepanok.undp.domain.model.SyncState
import com.stepanok.undp.domain.model.toTier
import kotlin.time.Instant

// ── domain → persisted entry ───────────────────────────────────────────

fun Report.toOutboxEntry(): OutboxEntry {
    val (kind, attempt, nextRetry, reason) = sync.toSnapshot()
    val photo = photos.firstOrNull()
    return OutboxEntry(
        submit = toSubmitDto(),
        photoLocalPath = photo?.localPath,
        photoRemoteUrl = photo?.remoteUrl,
        damageLevel = damage.name,
        damageTier = damageTier?.name,
        place = place,
        capturedAtMillis = capturedAt.toEpochMilliseconds(),
        isMine = isMine,
        version = version,
        supersedesReportId = supersedesReportId,
        syncKind = kind,
        attempt = attempt,
        nextRetryAtMillis = nextRetry,
        reason = reason,
    )
}

// ── persisted entry → domain (rebuilt for display + resumed upload) ─────

fun OutboxEntry.toReport(): Report {
    val s = submit
    val photos = photoLocalPath?.let { listOf(PhotoRef(localPath = it, remoteUrl = photoRemoteUrl)) } ?: emptyList()
    val level = damageLevelOf(damageLevel)
    return Report(
        id = s.id,
        idempotencyKey = s.idempotencyKey,
        photos = photos,
        damage = level,
        damageTier = damageTier?.let { tierOf(it) } ?: level.toTier(),
        possiblyDamaged = s.possiblyDamaged,
        lifeSafety = s.lifeSafety,
        infraTypes = s.infraTypes.mapNotNull(::infraOf).toSet(),
        infraOtherDetail = s.infraOtherDetail,
        crisisNature = s.crisisNature.mapNotNull(::natureOf).toSet(),
        debris = debrisOf(s.debris),
        location = ReportLocation(
            lat = s.lat,
            lng = s.lng,
            locationResolved = s.locationResolved,
            buildingId = s.buildingId,
            what3words = s.what3words,
            landmark = s.landmark,
            gpsAccuracyMeters = s.accuracyMeters,
        ),
        description = s.description?.let(::descriptionOf),
        modular = s.modular?.let { m ->
            ModularSections(
                electricity = m.electricity?.let(::electricityOf),
                healthServices = m.healthServices?.let(::healthServicesOf),
                pressingNeeds = m.pressingNeeds.mapNotNull(::pressingNeedOf).toSet(),
            )
        },
        capturedAt = Instant.fromEpochMilliseconds(capturedAtMillis),
        buildingId = s.buildingId,
        version = version,
        supersedesReportId = supersedesReportId,
        sync = syncStateOf(),
        place = place,
        isMine = isMine,
    )
}

// ── SyncState ↔ flattened snapshot ──────────────────────────────────────

private data class SyncSnapshot(
    val kind: SyncKind,
    val attempt: Int,
    val nextRetryMillis: Long?,
    val reason: String?,
)

private fun SyncState.toSnapshot(): SyncSnapshot = when (this) {
    is SyncState.Queued -> SyncSnapshot(SyncKind.QUEUED, 0, null, null)
    // An in-flight transfer isn't durable; persist it as Queued so a relaunch re-attempts cleanly.
    is SyncState.Syncing -> SyncSnapshot(SyncKind.QUEUED, 0, null, null)
    is SyncState.Synced -> SyncSnapshot(SyncKind.SYNCED, 0, null, null)
    is SyncState.PhotoPending -> SyncSnapshot(SyncKind.PHOTO_PENDING, 0, null, null)
    is SyncState.Failed -> SyncSnapshot(
        SyncKind.FAILED,
        attempt,
        nextRetryAt.toEpochMilliseconds(),
        reason,
    )
}

private fun OutboxEntry.syncStateOf(): SyncState = when (syncKind) {
    SyncKind.QUEUED -> SyncState.Queued
    SyncKind.SYNCED -> SyncState.Synced
    SyncKind.PHOTO_PENDING -> SyncState.PhotoPending
    SyncKind.FAILED -> SyncState.Failed(
        attempt = attempt,
        nextRetryAt = Instant.fromEpochMilliseconds(nextRetryAtMillis ?: 0L),
        reason = reason ?: "upload failed",
    )
}

// ── lenient string → enum helpers (mirror data.remote.Mappers, kept local) ──

private fun damageLevelOf(s: String): DamageLevel = when (s.lowercase()) {
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

private fun tierOf(s: String): DamageTier =
    runCatching { DamageTier.valueOf(s.uppercase()) }.getOrElse {
        runCatching { DamageLevel.valueOf(s.uppercase()).toTier() }.getOrDefault(DamageTier.MINIMAL)
    }

private fun infraOf(s: String): InfraType? = runCatching { InfraType.valueOf(s.uppercase()) }.getOrNull()

private fun natureOf(s: String): CrisisNature? = runCatching { CrisisNature.valueOf(s.uppercase()) }.getOrNull()

private fun debrisOf(s: String): DebrisState =
    runCatching { DebrisState.valueOf(s.uppercase()) }.getOrDefault(DebrisState.UNSURE)

private fun electricityOf(s: String): ElectricityCondition? = runCatching { ElectricityCondition.valueOf(s.uppercase()) }.getOrNull()

private fun healthServicesOf(s: String): HealthServices? = runCatching { HealthServices.valueOf(s.uppercase()) }.getOrNull()

private fun pressingNeedOf(s: String): PressingNeed? = runCatching { PressingNeed.valueOf(s.uppercase()) }.getOrNull()

private fun descriptionOf(d: ReportDescriptionDto): ReportDescription = ReportDescription(
    original = d.original,
    originalLang = d.originalLang,
    translated = d.translated.ifBlank { null },
    translatedLang = d.translatedLang,
)
