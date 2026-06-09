@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.stepanok.undp.domain.model

import kotlin.time.Instant

/**
 * A single community damage report — the export-ready core of Beacon. Carries every UNDP field
 * plus the linkage ([buildingId]/[version]/[supersedesReportId]) that powers per-building
 * versioning, and its current [sync] state.
 */
data class Report(
    val id: String,
    val idempotencyKey: String,
    val photos: List<PhotoRef> = emptyList(),
    /** Display grade (5-level). For tier3 reports this is the representative level of [damageTier]. */
    val damage: DamageLevel,
    /** Required 3-tier classification, sent to the server when the reporter used the 3-tier scale. */
    val damageTier: DamageTier? = null,
    /** Reporter unsure of the exact grade (resolves the satellite "possibly damaged" class). */
    val possiblyDamaged: Boolean = false,
    /** Intake life-safety flag (people trapped/injured) → drives the dispatch fast lane. */
    val lifeSafety: Boolean = false,
    val infraTypes: Set<InfraType> = emptySet(),
    val infraOtherDetail: String? = null,
    val crisisNature: Set<CrisisNature> = emptySet(),
    val debris: DebrisState = DebrisState.UNSURE,
    val location: ReportLocation,
    val description: ReportDescription? = null,
    val modular: ModularSections? = null,
    val anonymization: Anonymization = Anonymization(),
    val capturedAt: Instant,
    val buildingId: String? = null,
    val version: Int = 1,
    val supersedesReportId: String? = null,
    val sync: SyncState = SyncState.Queued,
    /** Human-friendly place label for lists, e.g. "Akdeniz Ave". */
    val place: String = "",
    /** True for reports submitted from this device (drives "My Reports" vs the all-community map). */
    val isMine: Boolean = false,
) {
    val isSynced: Boolean get() = sync is SyncState.Synced
}
