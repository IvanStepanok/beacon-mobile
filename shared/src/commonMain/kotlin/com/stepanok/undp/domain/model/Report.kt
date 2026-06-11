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
    /** The mandated 3-tier damage classification (minimal/partial/complete). */
    val damage: DamageTier,
    /** Reporter unsure of the exact grade (resolves the satellite "possibly damaged" class). */
    val possiblyDamaged: Boolean = false,
    val infraTypes: Set<InfraType> = emptySet(),
    /** Name/details of the infrastructure (any type), e.g. "Cumhuriyet Primary School". */
    val infraName: String? = null,
    val infraOtherDetail: String? = null,
    val crisisNature: Set<CrisisNature> = emptySet(),
    val debris: DebrisState = DebrisState.UNSURE,
    val location: ReportLocation,
    val description: ReportDescription? = null,
    val modular: ModularSections? = null,
    val anonymization: Anonymization = Anonymization(),
    val capturedAt: Instant,
    val buildingId: String? = null,
    /** Crisis this report is pinned to — stamped from the active map scope at submit time so a
     *  landmark-only (no-coords) report still joins its crisis (the server keeps an explicit pin
     *  for no-coords reports). Null = no scope → the server assigns spatially. */
    val crisisId: String? = null,
    val version: Int = 1,
    val supersedesReportId: String? = null,
    val sync: SyncState = SyncState.Queued,
    /** Human-friendly place label for lists, e.g. "Akdeniz Ave". Empty when unknown — the client
     *  never sends a fabricated one; use [placeLabel] for display. */
    val place: String = "",
    /** True for reports submitted from this device (drives "My Reports" vs the all-community map). */
    val isMine: Boolean = false,
) {
    val isSynced: Boolean get() = sync is SyncState.Synced

    /** Terminal server rejection — never retried, excluded from "queued" counts and flushes. */
    val isRejected: Boolean get() = sync is SyncState.Rejected

    /** List/preview label for where the report is. [place] may be empty (the client no longer
     *  fabricates one) — fall back to the Plus Code, then the landmark, then rounded coordinates.
     *  Legacy queued reports may still carry the old hardcoded "Your location" placeholder; treat
     *  it as absent so the honest fallback shows instead. */
    val placeLabel: String get() {
        place.trim().takeIf { it.isNotEmpty() && !it.equals("Your location", ignoreCase = true) }?.let { return it }
        location.plusCode?.takeIf { it.isNotBlank() }?.let { return it }
        location.landmark?.takeIf { it.isNotBlank() }?.let { return it }
        val lat = location.lat
        val lng = location.lng
        return if (lat != null && lng != null) "${lat.round2()}, ${lng.round2()}" else "—"
    }
}

private fun Double.round2(): Double = kotlin.math.round(this * 100) / 100
