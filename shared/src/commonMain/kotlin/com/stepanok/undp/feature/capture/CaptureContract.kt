package com.stepanok.undp.feature.capture

import com.stepanok.undp.core.mvi.UiEffect
import com.stepanok.undp.core.mvi.UiIntent
import com.stepanok.undp.core.mvi.UiState
import com.stepanok.undp.domain.model.CrisisNature
import com.stepanok.undp.domain.model.DamageLevel
import com.stepanok.undp.domain.model.DamageTier
import com.stepanok.undp.domain.model.DebrisState
import com.stepanok.undp.domain.model.FormSection
import com.stepanok.undp.domain.model.InfraType
import com.stepanok.undp.domain.model.defaultFormSections
import com.stepanok.undp.core.media.RedactionResult

/** The in-progress report shared across all capture steps. */
data class CaptureDraft(
    val photoCaptured: Boolean = false,
    /** Local file path of the real captured/picked photo (null until taken). */
    val photoPath: String? = null,
    val photoSizeBytes: Long = 0L,
    /** On-device face/plate redaction outcome for the captured photo (drives Anonymization). */
    val redaction: RedactionResult = RedactionResult(),
    /** EMS-98 grade (set in either scale: the representative level for a chosen tier). */
    val damage: DamageLevel? = null,
    /** Required 3-tier choice (set when the global scale is tier3). */
    val damageTier: DamageTier? = null,
    val possiblyDamaged: Boolean = false,
    val lifeSafety: Boolean = false,
    val infra: Set<InfraType> = emptySet(),
    /** Optional name/details of the infrastructure — one field for ANY selected type. When
     *  OTHER is selected it doubles as the legacy "specify Other" detail on the wire. */
    val infraName: String = "",
    val crisis: Set<CrisisNature> = emptySet(),
    val debris: DebrisState? = null,
    /** Stable footprint id (fp-…) ONLY when a real footprint polygon was tapped; never fabricated. */
    val buildingId: String? = null,
    /** "footprint" when [buildingId] came from a tapped polygon (per the API contract). */
    val buildingSource: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    /** Open Location Code (Plus Code), computed on-device from the GPS fix. Empty until located. */
    val plusCode: String = "",
    val landmark: String = "",
    /** 0 = no fix yet (UI shows "Locating…"); set to the real horizontal accuracy on a fix. */
    val gpsAccuracyMeters: Double = 0.0,
    val description: String = "",
    // Modular sections — answered against the server-driven schema in [CaptureState.formSections].
    /** Selected option wire values per schema section key (one element for single-choice). */
    val modularAnswers: Map<String, Set<String>> = emptyMap(),
    /** "Other → specify" free texts per schema section key. */
    val modularOther: Map<String, String> = emptyMap(),
)

data class CaptureState(
    val draft: CaptureDraft = CaptureDraft(),
    val submitting: Boolean = false,
    val offline: Boolean = true,
    /** Global capture scale from the server: "tier3" (3 buttons) | "ems98" (5 buttons). */
    val damageScale: String = "tier3",
    /** Server-driven modular form (GET /form-schema, cached for offline); the built-in
     *  Appendix-1 default until loaded — so the step is never blank. */
    val formSections: List<FormSection> = defaultFormSections(),
    /** Set when the tapped building already has a recent report nearby (anti-duplication). */
    val duplicateWarning: String? = null,
) : UiState

sealed interface CaptureIntent : UiIntent {
    /** A real photo was captured/picked and saved to [path] ([sizeBytes] on disk). */
    data class PhotoCaptured(val path: String, val sizeBytes: Long, val redaction: RedactionResult = RedactionResult()) : CaptureIntent
    data class SetDamage(val level: DamageLevel) : CaptureIntent
    data class SetDamageTier(val tier: DamageTier) : CaptureIntent
    data class SetPossiblyDamaged(val flag: Boolean) : CaptureIntent
    data class SetLifeSafety(val flag: Boolean) : CaptureIntent
    data class ToggleInfra(val type: InfraType) : CaptureIntent
    data class SetInfraName(val text: String) : CaptureIntent
    data class ToggleCrisis(val nature: CrisisNature) : CaptureIntent
    data class SetDebris(val debris: DebrisState) : CaptureIntent
    data class SetBuilding(val id: String) : CaptureIntent
    /** A footprint was tapped at this point — set location + run anti-duplication check.
     *  [footprintId] is the stable id derived from the tapped polygon (feature id or hash of its
     *  normalized ring); null when this came from a bare map tap (no footprint), in which case the
     *  report carries coords only — buildingId stays null (never a fabricated coordinate-grid id). */
    data class SelectBuilding(val lat: Double, val lng: Double, val footprintId: String? = null) : CaptureIntent
    /** Pull the real device GPS fix into the draft (fired once location permission is granted). */
    data object RequestDeviceLocation : CaptureIntent
    data class SetLandmark(val text: String) : CaptureIntent
    data class SetDescription(val text: String) : CaptureIntent
    /** Single-choice modular section [key] answered with option wire value [value]. */
    data class SetModularChoice(val key: String, val value: String) : CaptureIntent
    /** Toggle option wire value [value] of multi-select modular section [key]. */
    data class ToggleModularOption(val key: String, val value: String) : CaptureIntent
    /** Free text for section [key]'s "Other → specify" detail. */
    data class SetModularOther(val key: String, val text: String) : CaptureIntent
    data object Submit : CaptureIntent
}

sealed interface CaptureEffect : UiEffect {
    data object Submitted : CaptureEffect
    data class Error(val message: String) : CaptureEffect
}
