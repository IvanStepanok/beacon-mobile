package com.stepanok.undp.feature.profile

import cafe.adriel.voyager.core.model.screenModelScope
import com.stepanok.undp.core.mvi.MviScreenModel
import com.stepanok.undp.core.mvi.UiEffect
import com.stepanok.undp.core.mvi.UiIntent
import com.stepanok.undp.core.mvi.UiState
import com.stepanok.undp.domain.export.ReportExporter
import com.stepanok.undp.domain.model.Profile
import com.stepanok.undp.domain.model.Report
import com.stepanok.undp.domain.repository.ProfileRepository
import com.stepanok.undp.domain.repository.ReportRepository
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class ProfileUiState(
    val profile: Profile? = null,
    /** True until the profile flow emits — drives the loading spinner vs. blank screen. */
    val loading: Boolean = true,
) : UiState

sealed interface ProfileIntent : UiIntent

class ProfileScreenModel(
    profileRepository: ProfileRepository,
    reportRepository: ReportRepository,
) : MviScreenModel<ProfileUiState, ProfileIntent, UiEffect>(ProfileUiState()) {

    private var cachedReports: List<Report> = emptyList()

    init {
        // Report/building counts are derived from THIS device's reports (the optimistic outbox
        // _mine), not the one-shot server profile — so they go non-zero the instant a report is
        // submitted. POINTS are deliberately NOT derived locally: the server awards +10 only once
        // a report is VERIFIED (mirroring the thank-you copy), so we show its count as-is — the
        // repository falls back to the last-known server value (or 0) when offline. The server
        // profile also supplies identity (anonymousId/alias) and recognition badges.
        screenModelScope.launch {
            profileRepository.observeProfile()
                .combine(reportRepository.observeMyReports()) { profile, myReports ->
                    profile.copy(
                        reportCount = myReports.size,
                        buildingCount = myReports.mapNotNull { it.buildingId }.distinct().size,
                    )
                }
                .collect { p -> setState { copy(profile = p, loading = false) } }
        }
        // "Export my reports" serializes the device's own reports (_mine), not the community feed.
        screenModelScope.launch {
            reportRepository.observeMyReports().collect { cachedReports = it }
        }
    }

    fun exportGeoJson(): String = ReportExporter.toGeoJson(cachedReports)
    fun exportCsv(): String = ReportExporter.toCsv(cachedReports)

    override fun onIntent(intent: ProfileIntent) {}
}
