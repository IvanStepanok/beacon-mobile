@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.stepanok.undp.feature.crisis

import cafe.adriel.voyager.core.model.screenModelScope
import com.stepanok.undp.core.AppClock
import com.stepanok.undp.core.format.relativeTime
import com.stepanok.undp.core.location.LocationProvider
import com.stepanok.undp.core.mvi.MviScreenModel
import com.stepanok.undp.core.mvi.UiEffect
import com.stepanok.undp.core.mvi.UiIntent
import com.stepanok.undp.core.mvi.UiState
import com.stepanok.undp.domain.model.Crisis
import com.stepanok.undp.domain.model.DangerZone
import com.stepanok.undp.domain.repository.CrisisRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

data class CrisisUiState(
    /** The crisis covering the user's location (the headline), or null if none nearby. */
    val crisis: Crisis? = null,
    val started: String = "",
    val dangerZones: ImmutableList<DangerZone> = persistentListOf(),
    val alertsOn: Boolean = true,
    /** Active crises worldwide, nearest first — the "browse" list lives here, not on the map. */
    val activeCrises: ImmutableList<Crisis> = persistentListOf(),
) : UiState

sealed interface CrisisIntent : UiIntent {
    data object ToggleAlerts : CrisisIntent
}

class CrisisScreenModel(
    private val crisisRepository: CrisisRepository,
    private val clock: AppClock,
    private val locationProvider: LocationProvider,
) : MviScreenModel<CrisisUiState, CrisisIntent, UiEffect>(CrisisUiState()) {

    private val alerts = MutableStateFlow(true)

    init {
        screenModelScope.launch { alerts.collect { on -> setState { copy(alertsOn = on) } } }
        resolve()
    }

    /** Location-aware + NEARBY-only: the headline crisis (covering the user) and the
     *  list are crises within [NEARBY_KM] of the user — not the global firehose of
     *  events thousands of km away (which is noise for a local reporter). */
    fun resolve() {
        screenModelScope.launch {
            val loc = locationProvider.lastKnown() ?: locationProvider.current()
            // crisesNear returns active/proposed within range, sorted by distance, with covers + distanceKm.
            val near = if (loc != null) crisisRepository.crisesNear(loc.lat, loc.lng, NEARBY_KM) else emptyList()
            val covering = near.firstOrNull { it.covers }
            val zones = covering?.let { crisisRepository.dangerZones(it.id) } ?: emptyList()
            setState {
                copy(
                    crisis = covering,
                    started = covering?.let { relativeTime(clock.now(), it.startedAt) }.orEmpty(),
                    dangerZones = zones.toImmutableList(),
                    activeCrises = near.filter { it.id != covering?.id }.toImmutableList(),
                )
            }
        }
    }

    private companion object {
        const val NEARBY_KM = 300.0
    }

    override fun onIntent(intent: CrisisIntent) {
        when (intent) {
            CrisisIntent.ToggleAlerts -> alerts.value = !alerts.value
        }
    }
}
