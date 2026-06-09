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

/** Points awarded per submitted report — mirrors the "+10 community points" thank-you copy. */
private const val POINTS_PER_REPORT = 10

data class ProfileUiState(val profile: Profile? = null) : UiState

sealed interface ProfileIntent : UiIntent

class ProfileScreenModel(
    profileRepository: ProfileRepository,
    reportRepository: ReportRepository,
) : MviScreenModel<ProfileUiState, ProfileIntent, UiEffect>(ProfileUiState()) {

    private var cachedReports: List<Report> = emptyList()

    init {
        // Stats are derived from THIS device's reports (the optimistic outbox _mine), not the
        // one-shot server profile — so they go non-zero the instant a report is submitted, with
        // no dependency on the backend re-counting or awardPoints round-tripping. The server
        // profile still supplies identity (anonymousId/alias) and recognition badges.
        screenModelScope.launch {
            profileRepository.observeProfile()
                .combine(reportRepository.observeMyReports()) { profile, myReports ->
                    profile.copy(
                        reportCount = myReports.size,
                        buildingCount = myReports.mapNotNull { it.buildingId }.distinct().size,
                        points = myReports.size * POINTS_PER_REPORT,
                    )
                }
                .collect { p -> setState { copy(profile = p) } }
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
