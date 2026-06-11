package com.stepanok.undp.domain.repository

import com.stepanok.undp.domain.model.AreaGroup
import com.stepanok.undp.domain.model.BuildingTimeline
import com.stepanok.undp.domain.model.Crisis
import com.stepanok.undp.domain.model.DownloadBundle
import com.stepanok.undp.domain.model.FormSection
import com.stepanok.undp.domain.model.OutboxItem
import com.stepanok.undp.domain.model.Profile
import com.stepanok.undp.domain.model.Report
import com.stepanok.undp.domain.model.SyncState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The mock→server swap boundary: every screen depends only on these interfaces, so going live is
 * an edit to the DI module + new Ktor-backed implementations, with no feature/UI changes.
 */
/** A viewport rectangle (WGS84). */
data class MapBounds(val minLng: Double, val minLat: Double, val maxLng: Double, val maxLat: Double) {
    /** Backend bbox query string "minLng,minLat,maxLng,maxLat". */
    fun toQuery(): String = "$minLng,$minLat,$maxLng,$maxLat"
}

interface ReportRepository {
    fun observeReports(): Flow<List<Report>>
    /** Only reports submitted from this device — for the "My Reports" tab. */
    fun observeMyReports(): Flow<List<Report>>
    fun observeReport(id: String): Flow<Report?>
    /** One report per building — its latest version — for the map pins. */
    fun observeLatestPerBuilding(): Flow<List<Report>>
    /**
     * Location-first scope for the community feed + map pins: a crisis, a viewport
     * bbox, or both. Until a scope is set the feed is empty (no hardcoded crisis) —
     * so a user anywhere loads only what's near them, never another region's data.
     */
    suspend fun setMapScope(crisisId: String?, bounds: MapBounds?)
    /** Ordered damage history for one building (latest + timeline). */
    fun observeBuildingTimeline(buildingId: String): Flow<BuildingTimeline>
    /** Reports grouped by area for prioritization. */
    fun observeAreaGroups(): Flow<List<AreaGroup>>
    suspend fun submit(report: Report)
    /** Update one report's sync state (driven by [SyncManager]). */
    suspend fun updateSync(reportId: String, state: SyncState)
    /** The modular capture-form sections — server-driven (GET /form-schema for the current
     *  crisis scope), cached so the form works offline, with the built-in Appendix-1 default
     *  as the final fallback. */
    suspend fun formSections(): List<FormSection>
}

interface OutboxQueue {
    fun observe(): Flow<List<OutboxItem>>
    suspend fun enqueue(item: OutboxItem)
    suspend fun update(reportId: String, state: SyncState)
    suspend fun pending(): List<OutboxItem>
}

interface SyncManager {
    val isSyncing: StateFlow<Boolean>
    /** Begin observing connectivity; auto-flush the outbox when online. */
    fun start()
    suspend fun flushNow()
    suspend fun retry(reportId: String)
}

interface DownloadQueue {
    fun observe(): Flow<List<DownloadBundle>>
    suspend fun enqueue(bundle: DownloadBundle)
    suspend fun cancel(id: String)
}

interface CrisisRepository {
    fun observeActiveCrisis(): Flow<Crisis?>
    /** Active/proposed crises near a point, nearest first (location-first launch). */
    suspend fun crisesNear(lat: Double, lng: Double, radiusKm: Double = 50.0): List<Crisis>
    /** All active crises (for the "browse crises elsewhere" list). */
    suspend fun activeCrises(): List<Crisis>
}

interface ProfileRepository {
    /** Points are server-derived from verified reports only — the client never awards itself. */
    fun observeProfile(): Flow<Profile>
}
