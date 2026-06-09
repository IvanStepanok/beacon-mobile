@file:OptIn(kotlin.time.ExperimentalTime::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.stepanok.undp.feature.reportdetail

import cafe.adriel.voyager.core.model.screenModelScope
import com.stepanok.undp.core.AppClock
import com.stepanok.undp.core.mvi.MviScreenModel
import com.stepanok.undp.core.mvi.UiEffect
import com.stepanok.undp.core.mvi.UiIntent
import com.stepanok.undp.core.mvi.UiState
import com.stepanok.undp.domain.model.BuildingTimeline
import com.stepanok.undp.domain.model.Report
import com.stepanok.undp.domain.repository.ReportRepository
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class ReportDetailUiState(
    val report: Report? = null,
    val timeline: BuildingTimeline? = null,
) : UiState

sealed interface ReportDetailIntent : UiIntent

class ReportDetailScreenModel(
    reportId: String,
    reportRepository: ReportRepository,
    private val clock: AppClock,
) : MviScreenModel<ReportDetailUiState, ReportDetailIntent, UiEffect>(ReportDetailUiState()) {

    init {
        screenModelScope.launch {
            reportRepository.observeReport(reportId)
                .flatMapLatest { report ->
                    val buildingId = report?.buildingId
                    if (buildingId != null) {
                        reportRepository.observeBuildingTimeline(buildingId).map { report to it }
                    } else {
                        flowOf(report to null)
                    }
                }
                .collect { (report, timeline) ->
                    setState { copy(report = report, timeline = timeline) }
                }
        }
    }

    fun now() = clock.now()

    override fun onIntent(intent: ReportDetailIntent) {}
}
