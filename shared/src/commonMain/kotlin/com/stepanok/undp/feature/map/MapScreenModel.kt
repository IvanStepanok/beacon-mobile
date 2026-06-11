@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.stepanok.undp.feature.map

import cafe.adriel.voyager.core.model.screenModelScope
import com.stepanok.undp.core.AppClock
import com.stepanok.undp.core.connectivity.ConnectivityObserver
import com.stepanok.undp.core.connectivity.ConnectivityStatus
import com.stepanok.undp.core.format.relativeTime
import com.stepanok.undp.core.location.LocationProvider
import com.stepanok.undp.core.mvi.MviScreenModel
import com.stepanok.undp.core.mvi.UiEffect
import com.stepanok.undp.core.mvi.UiIntent
import com.stepanok.undp.core.mvi.UiState
import com.stepanok.undp.domain.model.DamageTier
import com.stepanok.undp.domain.model.Report
import com.stepanok.undp.domain.repository.CrisisRepository
import com.stepanok.undp.domain.repository.MapBounds
import com.stepanok.undp.domain.repository.ReportRepository
import com.stepanok.undp.map.ReportPin
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Whether the map is resolving location, inside a crisis, or in an area with none. */
enum class MapMode { LOADING, IN_CRISIS, NO_CRISIS }

data class MapUiState(
    val pins: ImmutableList<ReportPin> = persistentListOf(),
    val damageCounts: Map<DamageTier, Int> = emptyMap(),
    val offline: Boolean = true,
    val queueCount: Int = 0,
    val mode: MapMode = MapMode.LOADING,
    val crisisTitle: String? = null,
    val crisisSubtitle: String? = null,
    val crisisDismissed: Boolean = false,
    /** Where the map should centre (user location, or the covering crisis). */
    val focusLat: Double? = null,
    val focusLng: Double? = null,
    /** Human label of the current area/crisis. */
    val areaLabel: String? = null,
) : UiState

sealed interface MapIntent : UiIntent {
    data object DismissCrisis : MapIntent
}

sealed interface MapEffect : UiEffect

/** Lightweight summary shown in the map's tap-preview bottom sheet. */
data class ReportPreview(
    val id: String,
    val place: String,
    val time: String,
    val damage: DamageTier,
    val plusCode: String,
)

class MapScreenModel(
    private val reportRepository: ReportRepository,
    private val crisisRepository: CrisisRepository,
    connectivity: ConnectivityObserver,
    private val clock: AppClock,
    private val locationProvider: LocationProvider,
) : MviScreenModel<MapUiState, MapIntent, MapEffect>(MapUiState()) {

    private val selectedId = MutableStateFlow<String?>(null)

    private val _filter = MutableStateFlow<DamageTier?>(null)
    /** Active map filter by damage tier (null = show all). */
    val filter: StateFlow<DamageTier?> = _filter
    fun setFilter(tier: DamageTier?) { _filter.value = tier }

    /** Areas grouped + ranked for the hotspots sheet. */
    val hotspots: StateFlow<List<com.stepanok.undp.domain.model.AreaGroup>> =
        reportRepository.observeAreaGroups()
            .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** The report previewed in the bottom sheet (null = sheet hidden). */
    val selectedPreview: StateFlow<ReportPreview?> =
        combine(reportRepository.observeReports(), selectedId) { all, id ->
            all.firstOrNull { it.id == id }?.let {
                ReportPreview(it.id, it.placeLabel, relativeTime(clock.now(), it.capturedAt), it.damage, it.location.plusCode.orEmpty())
            }
        }.stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun selectReport(id: String) { selectedId.value = id }
    fun dismissPreview() { selectedId.value = null }

    init {
        // Reactive feed: pins + connectivity + own-queue. These never clobber the
        // location-driven fields (mode/crisis/focus) — those are set in resolveLocation().
        screenModelScope.launch {
            combine(
                reportRepository.observeLatestPerBuilding(),
                connectivity.status,
                reportRepository.observeMyReports(),
            ) { latest, conn, mine ->
                Triple(latest, conn, mine)
            }.collect { (latest, conn, mine) ->
                // Always surface the user's OWN reports as pins, even before they've synced to the
                // server and re-entered the community feed (_all). The community feed wins on id
                // collisions so we never double-render or double-count a report the server already
                // reflects. Keeps the reporter's contribution visible offline / pre-sync.
                val byId = LinkedHashMap<String, Report>(latest.size + mine.size)
                for (r in latest) byId[r.id] = r
                for (r in mine) if (!byId.containsKey(r.id)) byId[r.id] = r
                val merged = byId.values
                setState {
                    copy(
                        // Only resolved reports get a pin; landmark-only (unresolved) reports have no
                        // coordinate and must never land at 0,0 / Null Island.
                        pins = merged.mapNotNull { r ->
                            val lat = r.location.lat
                            val lng = r.location.lng
                            if (!r.location.locationResolved || lat == null || lng == null) null
                            else ReportPin(r.id, lat, lng, r.damage)
                        }.toImmutableList(),
                        damageCounts = merged.groupingBy { it.damage }.eachCount(),
                        offline = conn == ConnectivityStatus.OFFLINE,
                        // Terminal rejections are NOT queued — they will never upload.
                        queueCount = mine.count { !it.isSynced && !it.isRejected },
                    )
                }
            }
        }
        resolveLocation()
    }

    /** Location-first: centre on the user (instant last-known → refined), then decide
     *  crisis-mode vs no-crisis-nearby and scope the feed accordingly. The "browse
     *  active crises worldwide" list lives in the Crisis tab, not on the map. */
    fun resolveLocation() {
        screenModelScope.launch {
            // Instant centre from the cached fix, then refine with a fresh one.
            locationProvider.lastKnown()?.let { setState { copy(focusLat = it.lat, focusLng = it.lng) } }
            val loc = locationProvider.current() ?: locationProvider.lastKnown()
            if (loc == null) {
                setState { copy(mode = MapMode.NO_CRISIS) }
                return@launch
            }
            setState { copy(focusLat = loc.lat, focusLng = loc.lng) }

            val covering = crisisRepository.crisesNear(loc.lat, loc.lng).firstOrNull { it.covers }
            if (covering != null) {
                reportRepository.setMapScope(covering.id, null)
                setState {
                    copy(
                        mode = MapMode.IN_CRISIS,
                        crisisTitle = covering.title,
                        crisisSubtitle = "${covering.area} · ${relativeTime(clock.now(), covering.startedAt)} · ${covering.source}",
                        areaLabel = covering.area,
                        focusLat = covering.centerLat,
                        focusLng = covering.centerLng,
                        crisisDismissed = false,
                    )
                }
            } else {
                // Nothing covering the user → scope pins to the viewport around them.
                reportRepository.setMapScope(null, boundsAround(loc.lat, loc.lng))
                setState {
                    copy(
                        mode = MapMode.NO_CRISIS,
                        crisisTitle = null,
                        crisisSubtitle = null,
                        areaLabel = null,
                    )
                }
            }
        }
    }

    private fun boundsAround(lat: Double, lng: Double, d: Double = 0.45): MapBounds =
        MapBounds(minLng = lng - d, minLat = lat - d, maxLng = lng + d, maxLat = lat + d)

    override fun onIntent(intent: MapIntent) {
        when (intent) {
            MapIntent.DismissCrisis -> setState { copy(crisisDismissed = true) }
        }
    }
}
