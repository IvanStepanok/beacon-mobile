@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.stepanok.undp.data.remote

import com.stepanok.undp.core.AppClock
import com.stepanok.undp.core.connectivity.ConnectivityObserver
import com.stepanok.undp.core.connectivity.ConnectivityStatus
import com.stepanok.undp.core.io.readFileBytes
import com.stepanok.undp.core.network.DeviceId
import com.stepanok.undp.data.outbox.DurableOutbox
import com.stepanok.undp.domain.model.AreaGroup
import com.stepanok.undp.domain.model.BuildingTimeline
import com.stepanok.undp.domain.model.Crisis
import com.stepanok.undp.domain.model.DangerZone
import com.stepanok.undp.domain.model.Profile
import com.stepanok.undp.domain.model.Report
import com.stepanok.undp.domain.model.SyncState
import com.stepanok.undp.domain.repository.CrisisRepository
import com.stepanok.undp.domain.repository.MapBounds
import com.stepanok.undp.domain.repository.ProfileRepository
import com.stepanok.undp.domain.repository.ReportRepository
import com.stepanok.undp.domain.repository.SyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch

/**
 * Live ReportRepository + SyncManager over the backend.
 *
 * - The community feed (latest-per-building) + area groups are held in StateFlows,
 *   refreshed on a 20s poll and after each successful submit, so screens stay reactive.
 * - "My reports" doubles as the real device outbox: a submit is enqueued optimistically
 *   as [SyncState.Queued], uploaded immediately if online, or flushed automatically the
 *   moment real connectivity returns. No simulated byte-progress — state transitions
 *   reflect actual POST results (Queued → Syncing → Synced / Failed).
 */
class RemoteReportRepository(
    private val api: BeaconApi,
    private val connectivity: ConnectivityObserver,
    private val clock: AppClock,
    // Disk-backed durable outbox so "upload now, send later" survives process death.
    private val outbox: DurableOutbox = DurableOutbox(),
) : ReportRepository, SyncManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _all = MutableStateFlow<List<Report>>(emptyList())
    private val _areas = MutableStateFlow<List<AreaGroup>>(emptyList())
    private val _mine = MutableStateFlow<List<Report>>(emptyList())
    private val _isSyncing = MutableStateFlow(false)

    // Location-first scope: nothing loads until the map sets it (no hardcoded crisis).
    private var scopeCrisisId: String? = null
    private var scopeBounds: MapBounds? = null

    override val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    init {
        // Hydrate the durable outbox FIRST so a relaunch immediately shows pending reports
        // and resumes their uploads — before any loop or screen reads _mine.
        runCatching { outbox.load() }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { restored ->
            _mine.value = restored
        }
        scope.launch {
            while (true) {
                refresh()
                delay(20_000)
            }
        }
        // Resume any pending uploads left over from a previous session (if already online).
        scope.launch { flushNow() }
        // Auto-flush the outbox whenever real connectivity returns.
        scope.launch {
            connectivity.status.collect { status ->
                if (status == ConnectivityStatus.ONLINE) flushNow()
            }
        }
    }

    override suspend fun setMapScope(crisisId: String?, bounds: MapBounds?) {
        scopeCrisisId = crisisId
        scopeBounds = bounds
        refresh()
    }

    private suspend fun refresh() {
        val cid = scopeCrisisId
        val bbox = scopeBounds
        if (cid == null && bbox == null) return // no scope yet → keep feed empty
        runCatching { api.latestPerBuilding(cid, bbox?.toQuery()).map { it.toDomain() } }
            .onSuccess { _all.value = it }
        // Area hotspots are crisis-scoped; without a crisis there is nothing to group.
        if (cid != null) {
            runCatching { api.areaGroups(cid).map { it.toDomain() } }.onSuccess { _areas.value = it }
        } else {
            _areas.value = emptyList()
        }
    }

    override fun observeReports(): Flow<List<Report>> = _all.asStateFlow()
    override fun observeLatestPerBuilding(): Flow<List<Report>> = _all.asStateFlow()
    override fun observeMyReports(): Flow<List<Report>> = _mine.asStateFlow()
    override fun observeAreaGroups(): Flow<List<AreaGroup>> = _areas.asStateFlow()

    override fun observeReport(id: String): Flow<Report?> = flow {
        emit(
            _mine.value.firstOrNull { it.id == id }
                ?: _all.value.firstOrNull { it.id == id }
                ?: api.report(id)?.toDomain(),
        )
    }.catch { emit(_mine.value.firstOrNull { it.id == id } ?: _all.value.firstOrNull { it.id == id }) }

    override fun observeBuildingTimeline(buildingId: String): Flow<BuildingTimeline> = flow {
        emit(api.buildingTimeline(buildingId)?.toDomain() ?: BuildingTimeline(buildingId, null, emptyList()))
    }.catch { emit(BuildingTimeline(buildingId, null, emptyList())) }

    override suspend fun submit(report: Report) {
        // Enqueue optimistically so the report is never lost, then try to upload now.
        val queued = report.copy(sync = SyncState.Queued, isMine = true)
        upsertMine(queued)
        uploadOne(queued)
    }

    /** Real upload of one queued report; updates its visible sync state from the POST result. */
    private suspend fun uploadOne(report: Report) {
        if (connectivity.status.value != ConnectivityStatus.ONLINE) {
            // Don't clobber a richer durable state (PhotoPending/Failed) just because we're offline.
            if (report.sync is SyncState.Queued || report.sync is SyncState.Syncing) {
                upsertMine(report.copy(sync = SyncState.Queued))
            }
            return
        }
        // Row already accepted by the server; only the photo is outstanding → retry photo alone.
        if (report.sync is SyncState.PhotoPending) {
            upsertMine(report.copy(sync = if (uploadPhoto(report)) SyncState.Synced else SyncState.PhotoPending))
            return
        }
        upsertMine(report.copy(sync = SyncState.Syncing(0, 1)))
        runCatching { api.submit(report.toSubmitDto()) }
            .onSuccess {
                // The report row reached the server. Now the captured photo (if any) must follow;
                // a failed photo POST is NOT a clean Synced — keep the item as PhotoPending and
                // retry the photo on the next flush so it is never silently dropped.
                val photoOk = uploadPhoto(report)
                upsertMine(report.copy(sync = if (photoOk) SyncState.Synced else SyncState.PhotoPending))
                refresh()
            }
            .onFailure { e ->
                upsertMine(
                    report.copy(
                        sync = SyncState.Failed(
                            attempt = (report.sync as? SyncState.Failed)?.attempt?.plus(1) ?: 1,
                            nextRetryAt = clock.now(),
                            reason = e.message ?: "upload failed",
                        ),
                    ),
                )
            }
    }

    /**
     * Uploads the report's captured photo. Returns true when there is nothing to upload OR the
     * POST succeeded; false when a photo exists but its upload failed (so the caller keeps the
     * item in the outbox as [SyncState.PhotoPending]).
     */
    private suspend fun uploadPhoto(report: Report): Boolean {
        val path = report.photos.firstOrNull()?.localPath ?: return true
        val bytes = readFileBytes(path) ?: return false // file gone/unreadable → still pending
        return runCatching { api.uploadPhoto(report.id, bytes) }.isSuccess
    }

    // ---- SyncManager ----

    override fun start() { /* auto-started in init */ }

    override suspend fun flushNow() {
        if (connectivity.status.value != ConnectivityStatus.ONLINE) return
        val pending = _mine.value.filter { !it.isSynced }
        if (pending.isEmpty()) return
        _isSyncing.value = true
        for (report in pending) uploadOne(report)
        _isSyncing.value = false
    }

    override suspend fun retry(reportId: String) {
        val report = _mine.value.firstOrNull { it.id == reportId } ?: return
        uploadOne(report)
    }

    override suspend fun updateSync(reportId: String, state: SyncState) {
        val report = _mine.value.firstOrNull { it.id == reportId } ?: return
        upsertMine(report.copy(sync = state)) // persist the new state to disk too
    }

    override suspend fun damageScale(): String =
        runCatching { api.config().damageScale }.getOrDefault("tier3")

    private fun upsertMine(report: Report) {
        val updated = _mine.updateAndGet { current ->
            if (current.any { it.id == report.id }) {
                current.map { if (it.id == report.id) report else it }
            } else {
                listOf(report) + current
            }
        }
        // Persist the whole outbox on every mutation so a Queued/Failed/PhotoPending item
        // survives the app being killed. Atomic write; best-effort (ignore IO failure).
        runCatching { outbox.save(updated) }
    }
}

class RemoteCrisisRepository(private val api: BeaconApi) : CrisisRepository {
    override fun observeActiveCrisis(): Flow<Crisis?> =
        flow { emit(api.activeCrisis()?.toDomain()) }.catch { emit(null) }
    override fun observeDangerZones(): Flow<List<DangerZone>> =
        flow { emit(api.dangerZones().map { it.toDomain() }) }.catch { emit(emptyList()) }
    override suspend fun crisesNear(lat: Double, lng: Double, radiusKm: Double): List<Crisis> =
        runCatching { api.crisesNear(lat, lng, radiusKm).map { it.toDomain() } }.getOrDefault(emptyList())
    override suspend fun activeCrises(): List<Crisis> =
        runCatching { api.crises("active").map { it.toDomain() } }.getOrDefault(emptyList())
    override suspend fun dangerZones(crisisId: String): List<DangerZone> =
        runCatching { api.dangerZones(crisisId).map { it.toDomain() } }.getOrDefault(emptyList())
}

class RemoteProfileRepository(private val api: BeaconApi) : ProfileRepository {
    override fun observeProfile(): Flow<Profile> =
        flow { emit(api.profile().toDomain()) }
            // When the profile fetch fails (offline, transient error, or a rate-limit returning a
            // non-JSON body) fall back to the stable per-install identity instead of crashing the
            // collecting ScreenModel (it runs on Dispatchers.Main with no exception handler). The
            // device id IS what the server uses as anonymousId, so the identity card stays correct;
            // live report/building/point counts are derived from the local outbox upstream.
            .catch { emit(offlineProfile()) }
    override suspend fun awardPoints(points: Int, reason: String) {
        runCatching { api.awardPoints(points, reason) }
    }

    private fun offlineProfile() = Profile(
        anonymousId = DeviceId.get(),
        reportCount = 0,
        buildingCount = 0,
        points = 0,
        badges = emptyList(),
    )
}
