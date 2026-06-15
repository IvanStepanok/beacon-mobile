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
    /** True until the report flow emits its first value — drives the loading spinner vs. blank screen. */
    val loading: Boolean = true,
    val report: Report? = null,
    val timeline: BuildingTimeline? = null,
    /** A withdrawal request is in flight (disables the button). */
    val withdrawing: Boolean = false,
    /** The report was erased server-side → the screen should close. */
    val withdrawn: Boolean = false,
) : UiState

sealed interface ReportDetailIntent : UiIntent

class ReportDetailScreenModel(
    private val reportId: String,
    private val reportRepository: ReportRepository,
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
                    setState { copy(loading = false, report = report, timeline = timeline) }
                }
        }
    }

    fun now() = clock.now()

    /** Reporter-initiated takedown of their own report. On a confirmed server erasure the
     *  report is removed from My Reports and [ReportDetailUiState.withdrawn] flips so the
     *  screen closes; a failure just clears the in-flight flag (the report stays). */
    fun withdraw() {
        if (state.value.withdrawing || state.value.withdrawn) return
        screenModelScope.launch {
            setState { copy(withdrawing = true) }
            val ok = reportRepository.withdraw(reportId)
            setState { if (ok) copy(withdrawn = true) else copy(withdrawing = false) }
        }
    }

    override fun onIntent(intent: ReportDetailIntent) {}
}
