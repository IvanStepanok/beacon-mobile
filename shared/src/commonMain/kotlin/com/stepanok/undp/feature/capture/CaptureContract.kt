package com.stepanok.undp.feature.capture

import com.stepanok.undp.core.mvi.UiEffect
import com.stepanok.undp.core.mvi.UiIntent
import com.stepanok.undp.core.mvi.UiState
import com.stepanok.undp.domain.model.CrisisNature
import com.stepanok.undp.domain.model.DamageLevel
import com.stepanok.undp.domain.model.DamageTier
import com.stepanok.undp.domain.model.DebrisState
import com.stepanok.undp.domain.model.ElectricityCondition
import com.stepanok.undp.domain.model.HealthServices
import com.stepanok.undp.domain.model.InfraType
import com.stepanok.undp.domain.model.PressingNeed

/** The in-progress report shared across all capture steps. */
data class CaptureDraft(
    val photoCaptured: Boolean = false,
    /** Local file path of the real captured/picked photo (null until taken). */
    val photoPath: String? = null,
    val photoSizeBytes: Long = 0L,
    /** EMS-98 grade (set in either scale: the representative level for a chosen tier). */
    val damage: DamageLevel? = null,
    /** Required 3-tier choice (set when the global scale is tier3). */
    val damageTier: DamageTier? = null,
    val possiblyDamaged: Boolean = false,
    val lifeSafety: Boolean = false,
    val infra: Set<InfraType> = emptySet(),
    val infraOther: String = "",
    val crisis: Set<CrisisNature> = emptySet(),
    val debris: DebrisState? = null,
    val buildingId: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    /** Open Location Code (Plus Code), computed on-device from the GPS fix. Empty until located. */
    val plusCode: String = "",
    val landmark: String = "",
    /** 0 = no fix yet (UI shows "Locating…"); set to the real horizontal accuracy on a fix. */
    val gpsAccuracyMeters: Double = 0.0,
    val description: String = "",
    // Optional modular sections
    val electricity: ElectricityCondition? = null,
    val health: HealthServices? = null,
    val needs: Set<PressingNeed> = emptySet(),
)

data class CaptureState(
    val draft: CaptureDraft = CaptureDraft(),
    val submitting: Boolean = false,
    val offline: Boolean = true,
    /** Global capture scale from the server: "tier3" (3 buttons) | "ems98" (5 buttons). */
    val damageScale: String = "tier3",
    /** Set when the tapped building already has a recent report nearby (anti-duplication). */
    val duplicateWarning: String? = null,
) : UiState

sealed interface CaptureIntent : UiIntent {
    /** A real photo was captured/picked and saved to [path] ([sizeBytes] on disk). */
    data class PhotoCaptured(val path: String, val sizeBytes: Long) : CaptureIntent
    data class SetDamage(val level: DamageLevel) : CaptureIntent
    data class SetDamageTier(val tier: DamageTier) : CaptureIntent
    data class SetPossiblyDamaged(val flag: Boolean) : CaptureIntent
    data class SetLifeSafety(val flag: Boolean) : CaptureIntent
    data class ToggleInfra(val type: InfraType) : CaptureIntent
    data class SetInfraOther(val text: String) : CaptureIntent
    data class ToggleCrisis(val nature: CrisisNature) : CaptureIntent
    data class SetDebris(val debris: DebrisState) : CaptureIntent
    data class SetBuilding(val id: String) : CaptureIntent
    /** A footprint was tapped at this point — set location + run anti-duplication check.
     *  [footprintId] is the stable id derived from the tapped polygon (feature id or hash of its
     *  normalized ring); null when this came from a bare map tap (no footprint), in which case a
     *  coordinate-derived fallback id is used. */
    data class SelectBuilding(val lat: Double, val lng: Double, val footprintId: String? = null) : CaptureIntent
    /** Pull the real device GPS fix into the draft (fired once location permission is granted). */
    data object RequestDeviceLocation : CaptureIntent
    data class SetLandmark(val text: String) : CaptureIntent
    data class SetDescription(val text: String) : CaptureIntent
    data class SetElectricity(val value: ElectricityCondition) : CaptureIntent
    data class SetHealth(val value: HealthServices) : CaptureIntent
    data class ToggleNeed(val value: PressingNeed) : CaptureIntent
    data object Submit : CaptureIntent
}

sealed interface CaptureEffect : UiEffect {
    data object Submitted : CaptureEffect
    data class Error(val message: String) : CaptureEffect
}
