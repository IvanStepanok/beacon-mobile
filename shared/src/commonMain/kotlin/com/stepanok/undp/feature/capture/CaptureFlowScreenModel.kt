@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.stepanok.undp.feature.capture

import cafe.adriel.voyager.core.model.screenModelScope
import com.stepanok.undp.core.AppClock
import com.stepanok.undp.core.IdGenerator
import com.stepanok.undp.core.connectivity.ConnectivityObserver
import com.stepanok.undp.core.connectivity.ConnectivityStatus
import com.stepanok.undp.core.location.LocationProvider
import com.stepanok.undp.core.location.PlusCode
import com.stepanok.undp.core.ml.DamageClassifier
import com.stepanok.undp.core.mvi.MviScreenModel
import com.stepanok.undp.domain.model.Anonymization
import com.stepanok.undp.domain.model.DebrisState
import com.stepanok.undp.domain.model.FORM_OTHER_VALUE
import com.stepanok.undp.domain.model.FormSection
import com.stepanok.undp.domain.model.FormSectionType
import com.stepanok.undp.domain.model.InfraType
import com.stepanok.undp.domain.model.ModularSections
import com.stepanok.undp.domain.model.PhotoRef
import com.stepanok.undp.domain.model.Report
import com.stepanok.undp.domain.model.ReportDescription
import com.stepanok.undp.domain.model.ReportLocation
import com.stepanok.undp.domain.model.SyncState
import com.stepanok.undp.domain.repository.ReportRepository
import com.stepanok.undp.domain.repository.SyncManager
import com.stepanok.undp.core.format.relativeTime
import com.stepanok.undp.translation.LanguageDetector
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

class CaptureFlowScreenModel(
    private val reportRepository: ReportRepository,
    private val syncManager: SyncManager,
    private val connectivity: ConnectivityObserver,
    private val languageDetector: LanguageDetector,
    private val clock: AppClock,
    private val ids: IdGenerator,
    private val locationProvider: LocationProvider,
    private val damageClassifier: DamageClassifier,
) : MviScreenModel<CaptureState, CaptureIntent, CaptureEffect>(CaptureState()) {

    init {
        screenModelScope.launch {
            connectivity.status.collect { status ->
                setState { copy(offline = status == ConnectivityStatus.OFFLINE) }
            }
        }
        // Server-driven modular form (cached for offline; built-in Appendix-1 default fallback) —
        // a section UNDP adds server-side renders + submits without an app update.
        screenModelScope.launch {
            val sections = reportRepository.formSections()
            setState { copy(formSections = sections) }
        }
    }

    override fun onIntent(intent: CaptureIntent) {
        when (intent) {
            is CaptureIntent.PhotoCaptured -> onPhotoCaptured(intent)
            is CaptureIntent.DamageSuggested -> setState {
                copy(draft = draft.copy(suggesting = false, suggestedTier = intent.tier, suggestedConfidence = intent.confidence))
            }
            is CaptureIntent.SetDamageTier -> setState { copy(draft = draft.copy(damageTier = intent.tier)) }
            is CaptureIntent.SetPossiblyDamaged -> setState { copy(draft = draft.copy(possiblyDamaged = intent.flag)) }
            is CaptureIntent.ToggleInfra -> setState { copy(draft = draft.copy(infra = draft.infra.toggle(intent.type))) }
            is CaptureIntent.SetInfraName -> setState { copy(draft = draft.copy(infraName = intent.text)) }
            is CaptureIntent.ToggleCrisis -> setState { copy(draft = draft.copy(crisis = draft.crisis.toggle(intent.nature))) }
            is CaptureIntent.SetDebris -> setState { copy(draft = draft.copy(debris = intent.debris)) }
            is CaptureIntent.SetBuilding -> setState { copy(draft = draft.copy(buildingId = intent.id)) }
            is CaptureIntent.SelectBuilding -> selectBuilding(intent.lat, intent.lng, intent.footprintId)
            CaptureIntent.RequestDeviceLocation -> requestDeviceLocation()
            is CaptureIntent.SetLandmark -> setState { copy(draft = draft.copy(landmark = intent.text)) }
            is CaptureIntent.SetDescription -> setState { copy(draft = draft.copy(description = intent.text)) }
            is CaptureIntent.SetModularChoice -> setState {
                copy(draft = draft.copy(modularAnswers = draft.modularAnswers + (intent.key to setOf(intent.value))))
            }
            is CaptureIntent.ToggleModularOption -> setState {
                val next = draft.modularAnswers[intent.key].orEmpty().toggle(intent.value)
                copy(
                    draft = draft.copy(
                        modularAnswers = if (next.isEmpty()) draft.modularAnswers - intent.key else draft.modularAnswers + (intent.key to next),
                    ),
                )
            }
            is CaptureIntent.SetModularOther -> setState {
                copy(draft = draft.copy(modularOther = draft.modularOther + (intent.key to intent.text)))
            }
            CaptureIntent.Submit -> submit()
        }
    }

    /** True once the reporter taps a building — auto-GPS must then never move the pin. */
    private var userPinned = false

    /** In-flight advisory classification (cancelled if the reporter re-captures). */
    private var classifyJob: Job? = null

    /** Store the photo, then fire on-device, OFFLINE advisory classification (B2). Inference runs
     *  while the reporter reads the Damage step (the next step); the result pre-highlights a tier
     *  but never auto-selects. Cancels any prior run; never blocks the flow (classify never throws). */
    private fun onPhotoCaptured(intent: CaptureIntent.PhotoCaptured) {
        setState {
            copy(
                draft = draft.copy(
                    photoCaptured = true,
                    photoPath = intent.path,
                    photoSizeBytes = intent.sizeBytes,
                    redaction = intent.redaction,
                    // Reset any prior suggestion — inference for THIS photo starts now.
                    suggesting = true,
                    suggestedTier = null,
                    suggestedConfidence = 0,
                ),
            )
        }
        classifyJob?.cancel()
        classifyJob = screenModelScope.launch {
            val s = damageClassifier.classify(intent.path)
            onIntent(CaptureIntent.DamageSuggested(s.tier, s.confidencePercent))
        }
    }

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
                    // No footprint was tapped here — buildingId stays null. Fabricating a
                    // coordinate-grid id would defeat the server's 25m/10min near-dup guard.
                ),
            )
        }
    }

    private fun selectBuilding(lat: Double, lng: Double, footprintId: String?) {
        userPinned = true
        setState {
            copy(
                draft = draft.copy(
                    // Stable id from the tapped polygon (feature id / ring hash) ONLY. A bare map
                    // tap with no footprint underneath carries coords alone — never a fabricated id.
                    buildingId = footprintId,
                    buildingSource = footprintId?.let { "footprint" },
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
        val tier = draft.damageTier ?: run {
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
                // Detect the source language and store the original verbatim. We do NOT translate
                // on-device — the server (LibreTranslate) translates for analysts after submit.
                ReportDescription(
                    original = text,
                    originalLang = languageDetector.detectLanguage(text) ?: "auto",
                    translated = null,
                    translatedLang = null,
                )
            }
            val report = Report(
                id = ids.newId(),
                idempotencyKey = ids.newId(),
                photos = draft.photoPath?.let { listOf(PhotoRef(localPath = it, sizeBytes = draft.photoSizeBytes)) }
                    ?: emptyList(),
                damage = tier,
                // Advisory on-device classifier (B2): store the model's suggestion alongside the
                // human grade (both, so analysts see agreement). Null when the model abstained.
                aiLevel = draft.suggestedTier,
                aiConfidence = draft.suggestedTier?.let { draft.suggestedConfidence },
                possiblyDamaged = draft.possiblyDamaged,
                infraTypes = draft.infra,
                infraName = draft.infraName.ifBlank { null },
                // The single name field doubles as the legacy "specify Other" detail when OTHER is picked.
                infraOtherDetail = draft.infraName.ifBlank { null }.takeIf { InfraType.OTHER in draft.infra },
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
                    buildingSource = draft.buildingSource,
                    plusCode = draft.plusCode.ifBlank { null },
                    landmark = draft.landmark.ifBlank { null },
                    gpsAccuracyMeters = draft.gpsAccuracyMeters.takeIf { it > 0.0 },
                ),
                description = description,
                modular = buildModular(draft, state.value.formSections),
                // Honest flags: true only when the on-device redactor actually ran on this photo.
                anonymization = Anonymization(
                    facesBlurred = draft.redaction.facesRedacted,
                    platesBlurred = draft.redaction.platesRedacted,
                ),
                capturedAt = clock.now(),
                buildingId = draft.buildingId,
                sync = SyncState.Queued,
                // No fabricated place label — lists fall back to Plus Code / landmark / coords.
                place = "",
                isMine = true,
            )
            reportRepository.submit(report)
            if (!state.value.offline) syncManager.flushNow()
            setState { copy(submitting = false) }
            postEffect(CaptureEffect.Submitted)
        }
    }

    /** Resolve the draft's generic answers against the schema: single-choice sections carry one
     *  value, multi-select a list (schema option order); an "Other → specify" text only counts
     *  while the section's "other" option is actually selected. Null when nothing was answered. */
    private fun buildModular(draft: CaptureDraft, sections: List<FormSection>): ModularSections? {
        val single = mutableMapOf<String, String>()
        val multi = mutableMapOf<String, List<String>>()
        val others = mutableMapOf<String, String>()
        sections.forEach { section ->
            val selected = draft.modularAnswers[section.key].orEmpty()
            if (selected.isEmpty()) return@forEach
            when (section.type) {
                FormSectionType.SINGLE -> single[section.key] = selected.first()
                FormSectionType.MULTI -> multi[section.key] = section.options.map { it.value }.filter { it in selected }
            }
            if (section.allowOtherText && FORM_OTHER_VALUE in selected) {
                draft.modularOther[section.key]?.ifBlank { null }?.let { others[section.key] = it }
            }
        }
        return ModularSections(single, multi, others).takeUnless { it.isEmpty }
    }
}

private fun <T> Set<T>.toggle(item: T): Set<T> = if (contains(item)) this - item else this + item
