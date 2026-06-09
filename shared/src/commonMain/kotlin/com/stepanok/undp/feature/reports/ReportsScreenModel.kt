@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.stepanok.undp.feature.reports

import cafe.adriel.voyager.core.model.screenModelScope
import com.stepanok.undp.core.AppClock
import com.stepanok.undp.core.connectivity.ConnectivityObserver
import com.stepanok.undp.core.connectivity.ConnectivityStatus
import com.stepanok.undp.core.format.relativeTime
import com.stepanok.undp.core.mvi.MviScreenModel
import com.stepanok.undp.core.mvi.UiEffect
import com.stepanok.undp.core.mvi.UiIntent
import com.stepanok.undp.core.mvi.UiState
import com.stepanok.undp.domain.model.DamageLevel
import com.stepanok.undp.domain.model.PhotoRef
import com.stepanok.undp.domain.model.SyncState
import com.stepanok.undp.domain.repository.ReportRepository
import com.stepanok.undp.domain.repository.SyncManager
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class ReportRowUi(
    val id: String,
    val place: String,
    val time: String,
    val damage: DamageLevel,
    val synced: Boolean,
    val sync: SyncState,
    val photo: PhotoRef? = null,
)

data class ReportsUiState(
    val rows: ImmutableList<ReportRowUi> = persistentListOf(),
    val filter: DamageLevel? = null,
    val total: Int = 0,
    val syncedCount: Int = 0,
    val queuedCount: Int = 0,
    val damageCounts: Map<DamageLevel, Int> = emptyMap(),
    val online: Boolean = false,
    val isSyncing: Boolean = false,
) : UiState

sealed interface ReportsIntent : UiIntent {
    data class SetFilter(val level: DamageLevel?) : ReportsIntent
    /** Manually flush the device outbox now (real upload of any queued reports). */
    data object SyncNow : ReportsIntent
}

class ReportsScreenModel(
    reportRepository: ReportRepository,
    connectivity: ConnectivityObserver,
    private val syncManager: SyncManager,
    private val clock: AppClock,
) : MviScreenModel<ReportsUiState, ReportsIntent, UiEffect>(ReportsUiState()) {

    private val filter = MutableStateFlow<DamageLevel?>(null)

    init {
        screenModelScope.launch {
            combine(
                reportRepository.observeMyReports(),
                filter,
                connectivity.status,
                syncManager.isSyncing,
            ) { mine, f, conn, syncing ->
                val now = clock.now()
                val rows = mine
                    .filter { f == null || it.damage == f }
                    .map { ReportRowUi(it.id, it.place, relativeTime(now, it.capturedAt), it.damage, it.isSynced, it.sync, it.photos.firstOrNull()) }
                ReportsUiState(
                    rows = rows.toImmutableList(),
                    filter = f,
                    total = mine.size,
                    syncedCount = mine.count { it.isSynced },
                    queuedCount = mine.count { !it.isSynced },
                    damageCounts = mine.groupingBy { it.damage }.eachCount(),
                    online = conn == ConnectivityStatus.ONLINE,
                    isSyncing = syncing,
                )
            }.collect { newState -> setState { newState } }
        }
    }

    override fun onIntent(intent: ReportsIntent) {
        when (intent) {
            is ReportsIntent.SetFilter -> filter.value = intent.level
            ReportsIntent.SyncNow -> screenModelScope.launch { syncManager.flushNow() }
        }
    }
}
