@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.stepanok.undp.feature.syncqueue

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
import com.stepanok.undp.domain.model.SyncState
import com.stepanok.undp.domain.repository.ReportRepository
import com.stepanok.undp.domain.repository.SyncManager
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class QueueItemUi(
    val id: String,
    val time: String,
    val damage: DamageLevel,
    val sizeMb: Double,
    val sync: SyncState,
)

data class SyncUiState(
    val items: ImmutableList<QueueItemUi> = persistentListOf(),
    val online: Boolean = false,
    val totalMb: Double = 0.0,
    val isSyncing: Boolean = false,
) : UiState

sealed interface SyncIntent : UiIntent {
    /** Manually flush the device outbox now (real upload of any queued reports). */
    data object SyncNow : SyncIntent
}

class SyncQueueScreenModel(
    reportRepository: ReportRepository,
    connectivity: ConnectivityObserver,
    private val syncManager: SyncManager,
    private val clock: AppClock,
) : MviScreenModel<SyncUiState, SyncIntent, UiEffect>(SyncUiState()) {

    init {
        screenModelScope.launch {
            combine(
                reportRepository.observeMyReports(),
                connectivity.status,
                syncManager.isSyncing,
            ) { all, conn, syncing ->
                val now = clock.now()
                val pending = all.filter { !it.isSynced }
                SyncUiState(
                    items = pending.map { r ->
                        QueueItemUi(
                            id = r.id,
                            time = relativeTime(now, r.capturedAt),
                            damage = r.damage,
                            sizeMb = r.photos.sumOf { it.sizeBytes } / 1_000_000.0,
                            sync = r.sync,
                        )
                    }.toImmutableList(),
                    online = conn == ConnectivityStatus.ONLINE,
                    totalMb = pending.sumOf { it.photos.sumOf { p -> p.sizeBytes } } / 1_000_000.0,
                    isSyncing = syncing,
                )
            }.collect { newState -> setState { newState } }
        }
    }

    override fun onIntent(intent: SyncIntent) {
        when (intent) {
            SyncIntent.SyncNow -> screenModelScope.launch { syncManager.flushNow() }
        }
    }
}
