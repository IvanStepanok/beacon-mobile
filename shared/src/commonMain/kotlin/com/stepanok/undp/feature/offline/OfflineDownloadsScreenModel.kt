package com.stepanok.undp.feature.offline

import cafe.adriel.voyager.core.model.screenModelScope
import com.stepanok.undp.core.mvi.MviScreenModel
import com.stepanok.undp.core.mvi.UiEffect
import com.stepanok.undp.core.mvi.UiIntent
import com.stepanok.undp.core.mvi.UiState
import com.stepanok.undp.core.location.LocationProvider
import com.stepanok.undp.core.offline.OfflineBundles
import com.stepanok.undp.domain.model.DownloadBundle
import com.stepanok.undp.domain.repository.CrisisRepository
import com.stepanok.undp.domain.repository.DownloadQueue
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

data class OfflineUiState(val bundles: ImmutableList<DownloadBundle> = persistentListOf()) : UiState

sealed interface OfflineIntent : UiIntent {
    data object DownloadCrisisPack : OfflineIntent
}

class OfflineDownloadsScreenModel(
    private val downloadQueue: DownloadQueue,
    private val locationProvider: LocationProvider,
    private val crisisRepository: CrisisRepository,
) : MviScreenModel<OfflineUiState, OfflineIntent, UiEffect>(OfflineUiState()) {

    init {
        screenModelScope.launch {
            downloadQueue.observe().collect { list -> setState { copy(bundles = list.toImmutableList()) } }
        }
    }

    override fun onIntent(intent: OfflineIntent) {
        when (intent) {
            OfflineIntent.DownloadCrisisPack -> screenModelScope.launch {
                // The offline pack follows the USER: the crisis covering them, else a
                // box around their location, else the nearest active crisis as fallback.
                val loc = locationProvider.lastKnown() ?: locationProvider.current()
                val pack = when {
                    loc != null -> {
                        val covering = crisisRepository.crisesNear(loc.lat, loc.lng).firstOrNull { it.covers }
                        if (covering != null) OfflineBundles.areaPack(covering.centerLat, covering.centerLng, covering.area)
                        else OfflineBundles.areaPack(loc.lat, loc.lng, "your area")
                    }
                    else -> crisisRepository.activeCrises().firstOrNull()
                        ?.let { OfflineBundles.areaPack(it.centerLat, it.centerLng, it.area) }
                        ?: OfflineBundles.areaPack(0.0, 0.0, "current area")
                }
                downloadQueue.enqueue(pack)
            }
        }
    }
}
