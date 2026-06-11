@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.stepanok.undp.data.outbox

import com.stepanok.undp.data.remote.ReportDescriptionDto
import com.stepanok.undp.data.remote.modularSectionsOf
import com.stepanok.undp.data.remote.toSubmitDto
import com.stepanok.undp.domain.model.CrisisNature
import com.stepanok.undp.domain.model.DamageTier
import com.stepanok.undp.domain.model.DebrisState
import com.stepanok.undp.domain.model.InfraType
import com.stepanok.undp.domain.model.PhotoRef
import com.stepanok.undp.domain.model.Report
import com.stepanok.undp.domain.model.ReportDescription
import com.stepanok.undp.domain.model.ReportLocation
import com.stepanok.undp.domain.model.SyncState
import kotlin.time.Instant

// ── domain → persisted entry ───────────────────────────────────────────

fun Report.toOutboxEntry(): OutboxEntry {
    val snap = sync.toSnapshot()
    val photo = photos.firstOrNull()
    return OutboxEntry(
        submit = toSubmitDto(),
        photoLocalPath = photo?.localPath,
        photoRemoteUrl = photo?.remoteUrl,
        damageLevel = damage.name,
        damageTier = damage.name,
        place = place,
        capturedAtMillis = capturedAt.toEpochMilliseconds(),
        isMine = isMine,
        version = version,
        supersedesReportId = supersedesReportId,
        syncKind = snap.kind,
        attempt = snap.attempt,
        nextRetryAtMillis = snap.nextRetryMillis,
        reason = snap.reason,
        rejectCode = snap.rejectCode,
        rejectHttpStatus = snap.rejectHttpStatus,
    )
}

// ── persisted entry → domain (rebuilt for display + resumed upload) ─────

fun OutboxEntry.toReport(): Report {
    val s = submit
    val photos = photoLocalPath?.let { listOf(PhotoRef(localPath = it, remoteUrl = photoRemoteUrl)) } ?: emptyList()
    return Report(
        id = s.id,
        idempotencyKey = s.idempotencyKey,
        photos = photos,
        // Reconstruct the tier from either stored field (tolerant of pre-migration 5-level names).
        damage = tierOf(damageTier ?: damageLevel),
        // Advisory classifier suggestion (B2) survives the outbox round-trip so an offline report's
        // resumed upload still carries aiLevel/aiConfidence (re-derived via report.toSubmitDto()).
        aiLevel = s.aiLevel?.let { tierOf(it) },
        aiConfidence = s.aiConfidence,
        possiblyDamaged = s.possiblyDamaged,
        infraTypes = s.infraTypes.mapNotNull(::infraOf).toSet(),
        infraName = s.infraName,
        infraOtherDetail = s.infraOtherDetail,
        crisisNature = s.crisisNature.mapNotNull(::natureOf).toSet(),
        debris = debrisOf(s.debris),
        location = ReportLocation(
            lat = s.lat,
            lng = s.lng,
            locationResolved = s.locationResolved,
            buildingId = s.buildingId,
            buildingSource = s.buildingSource,
            // Entries persisted before the plusCode rename only carry the legacy what3words name.
            plusCode = s.plusCode ?: s.what3words,
            landmark = s.landmark,
            gpsAccuracyMeters = s.accuracyMeters,
        ),
        description = s.description?.let(::descriptionOf),
        // Generic blob → answers; pre-rename files and server-added sections decode identically.
        modular = s.modular?.let(::modularSectionsOf),
        capturedAt = Instant.fromEpochMilliseconds(capturedAtMillis),
        buildingId = s.buildingId,
        // Restore the crisis pin so a relaunch's resumed upload still joins the same crisis.
        crisisId = s.crisisId,
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
    val rejectCode: String? = null,
    val rejectHttpStatus: Int? = null,
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
    // Terminal server rejection survives relaunch — it must never melt back into Queued.
    is SyncState.Rejected -> SyncSnapshot(
        SyncKind.REJECTED,
        0,
        null,
        reason,
        rejectCode = code,
        rejectHttpStatus = httpStatus,
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
    SyncKind.REJECTED -> SyncState.Rejected(
        code = rejectCode.orEmpty(),
        reason = reason ?: "rejected by server",
        httpStatus = rejectHttpStatus ?: 0,
    )
}

// ── lenient string → enum helpers (mirror data.remote.Mappers, kept local) ──

// Parse the persisted damage value to a tier. Tolerant of pre-migration 5-level enum
// names so an outbox file written before the 3-tier purge still rebuilds correctly.
private fun tierOf(s: String): DamageTier = when (s.uppercase()) {
    "MINIMAL", "NONE", "SLIGHT" -> DamageTier.MINIMAL
    "PARTIAL", "MODERATE", "SEVERE" -> DamageTier.PARTIAL
    "COMPLETE", "DESTROYED" -> DamageTier.COMPLETE
    else -> DamageTier.MINIMAL
}

private fun infraOf(s: String): InfraType? = runCatching { InfraType.valueOf(s.uppercase()) }.getOrNull()

private fun natureOf(s: String): CrisisNature? = runCatching { CrisisNature.valueOf(s.uppercase()) }.getOrNull()

private fun debrisOf(s: String): DebrisState =
    runCatching { DebrisState.valueOf(s.uppercase()) }.getOrDefault(DebrisState.UNSURE)

private fun descriptionOf(d: ReportDescriptionDto): ReportDescription = ReportDescription(
    original = d.original,
    originalLang = d.originalLang,
    translated = d.translated.ifBlank { null },
    translatedLang = d.translatedLang,
)
