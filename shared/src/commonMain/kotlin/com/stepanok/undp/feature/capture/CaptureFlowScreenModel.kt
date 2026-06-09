@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.stepanok.undp.feature.capture

import cafe.adriel.voyager.core.model.screenModelScope
import com.stepanok.undp.core.AppClock
import com.stepanok.undp.core.IdGenerator
import com.stepanok.undp.core.connectivity.ConnectivityObserver
import com.stepanok.undp.core.connectivity.ConnectivityStatus
import com.stepanok.undp.core.location.LocationProvider
import com.stepanok.undp.core.location.PlusCode
import com.stepanok.undp.core.mvi.MviScreenModel
import com.stepanok.undp.domain.model.Anonymization
import com.stepanok.undp.domain.model.DebrisState
import com.stepanok.undp.domain.model.ModularSections
import com.stepanok.undp.domain.model.PhotoRef
import com.stepanok.undp.domain.model.Report
import com.stepanok.undp.domain.model.ReportDescription
import com.stepanok.undp.domain.model.ReportLocation
import com.stepanok.undp.domain.model.SyncState
import com.stepanok.undp.domain.model.representativeLevel
import com.stepanok.undp.domain.repository.ProfileRepository
import com.stepanok.undp.domain.repository.ReportRepository
import com.stepanok.undp.domain.repository.SyncManager
import com.stepanok.undp.core.format.relativeTime
import com.stepanok.undp.translation.Translator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

class CaptureFlowScreenModel(
    private val reportRepository: ReportRepository,
    private val syncManager: SyncManager,
    private val profileRepository: ProfileRepository,
    private val connectivity: ConnectivityObserver,
    private val translator: Translator,
    private val clock: AppClock,
    private val ids: IdGenerator,
    private val locationProvider: LocationProvider,
) : MviScreenModel<CaptureState, CaptureIntent, CaptureEffect>(CaptureState()) {

    init {
        screenModelScope.launch {
            connectivity.status.collect { status ->
                setState { copy(offline = status == ConnectivityStatus.OFFLINE) }
            }
        }
        // Server-driven capture scale (3-tier default, or EMS-98 if an analyst flipped it).
        screenModelScope.launch {
            val scale = reportRepository.damageScale()
            setState { copy(damageScale = scale) }
        }
    }

    override fun onIntent(intent: CaptureIntent) {
        when (intent) {
            is CaptureIntent.PhotoCaptured -> setState {
                copy(draft = draft.copy(photoCaptured = true, photoPath = intent.path, photoSizeBytes = intent.sizeBytes))
            }
            is CaptureIntent.SetDamage -> setState { copy(draft = draft.copy(damage = intent.level, damageTier = null)) }
            is CaptureIntent.SetDamageTier -> setState {
                // Store the tier + a representative 5-level grade so the rest of the flow/display works.
                copy(draft = draft.copy(damageTier = intent.tier, damage = intent.tier.representativeLevel()))
            }
            is CaptureIntent.SetPossiblyDamaged -> setState { copy(draft = draft.copy(possiblyDamaged = intent.flag)) }
            is CaptureIntent.SetLifeSafety -> setState { copy(draft = draft.copy(lifeSafety = intent.flag)) }
            is CaptureIntent.ToggleInfra -> setState { copy(draft = draft.copy(infra = draft.infra.toggle(intent.type))) }
            is CaptureIntent.SetInfraOther -> setState { copy(draft = draft.copy(infraOther = intent.text)) }
            is CaptureIntent.ToggleCrisis -> setState { copy(draft = draft.copy(crisis = draft.crisis.toggle(intent.nature))) }
            is CaptureIntent.SetDebris -> setState { copy(draft = draft.copy(debris = intent.debris)) }
            is CaptureIntent.SetBuilding -> setState { copy(draft = draft.copy(buildingId = intent.id)) }
            is CaptureIntent.SelectBuilding -> selectBuilding(intent.lat, intent.lng, intent.footprintId)
            CaptureIntent.RequestDeviceLocation -> requestDeviceLocation()
            is CaptureIntent.SetLandmark -> setState { copy(draft = draft.copy(landmark = intent.text)) }
            is CaptureIntent.SetDescription -> setState { copy(draft = draft.copy(description = intent.text)) }
            is CaptureIntent.SetElectricity -> setState { copy(draft = draft.copy(electricity = intent.value)) }
            is CaptureIntent.SetHealth -> setState { copy(draft = draft.copy(health = intent.value)) }
            is CaptureIntent.ToggleNeed -> setState { copy(draft = draft.copy(needs = draft.needs.toggle(intent.value))) }
            CaptureIntent.Submit -> submit()
        }
    }

    /** True once the reporter taps a building — auto-GPS must then never move the pin. */
    private var userPinned = false

    /** Pre-fill the draft with the device fix: the cached last-known INSTANTLY (so the
     *  map snaps to the user, no Antakya flash / 8 s wait), then the precise fresh fix. */
    private fun requestDeviceLocation() {
        screenModelScope.launch {
            locationProvider.lastKnown()?.let { applyAutoFix(it.lat, it.lng, it.accuracyMeters) }
            locationProvider.current()?.let { applyAutoFix(it.lat, it.lng, it.accuracyMeters) }
        }
    }

    private fun applyAutoFix(lat: Double, lng: Double, accuracy: Double) {
        if (userPinned) return // the user tapped a building — respect their choice
        setState {
            copy(
                draft = draft.copy(
                    lat = lat,
                    lng = lng,
                    gpsAccuracyMeters = accuracy.takeIf { it > 0 } ?: draft.gpsAccuracyMeters,
                    plusCode = PlusCode.encode(lat, lng),
                    // No footprint was tapped here — a coordinate-derived fallback id, NOT a stable
                    // building identity (those come from selectBuilding's polygon-derived footprintId).
                    buildingId = "b-${(lat * 10000).toInt()}-${(lng * 10000).toInt()}",
                ),
            )
        }
    }

    private fun selectBuilding(lat: Double, lng: Double, footprintId: String?) {
        userPinned = true
        setState {
            copy(
                draft = draft.copy(
                    // Stable id from the tapped polygon (feature id / ring hash). The b-<grid>
                    // value is only a fallback for bare map taps with no footprint underneath.
                    buildingId = footprintId ?: "b-${(lat * 10000).toInt()}-${(lng * 10000).toInt()}",
                    lat = lat,
                    lng = lng,
                    plusCode = PlusCode.encode(lat, lng),
                ),
            )
        }
        screenModelScope.launch {
            // Only resolved reports (with a real point) can be a spatial duplicate of the tapped building.
            val near = reportRepository.observeReports().first()
                .mapNotNull { r -> val rl = r.location.lat; val rg = r.location.lng; if (rl != null && rg != null) Triple(r, rl, rg) else null }
                .filter { (_, rl, rg) -> abs(rl - lat) < 0.0006 && abs(rg - lng) < 0.0006 }
                .minByOrNull { (_, rl, rg) -> abs(rl - lat) + abs(rg - lng) }
                ?.first
            setState {
                copy(duplicateWarning = near?.let { relativeTime(clock.now(), it.capturedAt) })
            }
        }
    }

    private fun submit() {
        val draft = state.value.draft
        val damage = draft.damage ?: run {
            postEffect(CaptureEffect.Error("Pick a damage level first")); return
        }
        // A report MUST carry a real location: a GPS fix, a tapped footprint, or (failing both)
        // a non-blank landmark. Never fabricate a point — the Location step's UI guard mirrors this.
        val hasGeoFix = draft.lat != null && draft.lng != null
        if (!hasGeoFix && draft.landmark.isBlank()) {
            postEffect(CaptureEffect.Error("Pin the building on the map or describe the location first")); return
        }
        screenModelScope.launch {
            setState { copy(submitting = true) }
            val description = draft.description.ifBlank { null }?.let { text ->
                // Detect the source language and store the original verbatim. We do NOT fake a
                // translation — machine translation for responders is a documented roadmap item.
                ReportDescription(
                    original = text,
                    originalLang = translator.detectLanguage(text) ?: "auto",
                    translated = null,
                    translatedLang = null,
                )
            }
            val report = Report(
                id = ids.newId(),
                idempotencyKey = ids.newId(),
                photos = draft.photoPath?.let { listOf(PhotoRef(localPath = it, sizeBytes = draft.photoSizeBytes)) }
                    ?: emptyList(),
                damage = damage,
                damageTier = draft.damageTier,
                possiblyDamaged = draft.possiblyDamaged,
                lifeSafety = draft.lifeSafety,
                infraTypes = draft.infra,
                infraOtherDetail = draft.infraOther.ifBlank { null },
                crisisNature = draft.crisis,
                debris = draft.debris ?: DebrisState.UNSURE,
                location = ReportLocation(
                    // Real coordinates only: a GPS fix or the footprint the reporter tapped. We never
                    // fabricate a point — landmark-only reports carry null lat/lng + locationResolved=false
                    // (the Location-step guard blocks submit unless there's a fix or a landmark).
                    lat = draft.lat,
                    lng = draft.lng,
                    locationResolved = hasGeoFix,
                    buildingId = draft.buildingId,
                    what3words = draft.plusCode.ifBlank { null },
                    landmark = draft.landmark.ifBlank { null },
                    gpsAccuracyMeters = draft.gpsAccuracyMeters.takeIf { it > 0.0 },
                ),
                description = description,
                modular = takeIf {
                    draft.electricity != null || draft.health != null || draft.needs.isNotEmpty()
                }?.let { ModularSections(draft.electricity, draft.health, draft.needs) },
                anonymization = Anonymization(),
                capturedAt = clock.now(),
                buildingId = draft.buildingId,
                sync = SyncState.Queued,
                place = "Your location",
                isMine = true,
            )
            reportRepository.submit(report)
            profileRepository.awardPoints(10, reason = "report submitted")
            if (!state.value.offline) syncManager.flushNow()
            setState { copy(submitting = false) }
            postEffect(CaptureEffect.Submitted)
        }
    }
}

private fun <T> Set<T>.toggle(item: T): Set<T> = if (contains(item)) this - item else this + item
