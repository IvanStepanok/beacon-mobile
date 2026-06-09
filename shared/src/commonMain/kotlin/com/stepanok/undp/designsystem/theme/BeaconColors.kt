package com.stepanok.undp.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.stepanok.undp.domain.model.DamageLevel

/**
 * Beacon's palette — richer than Material3's [androidx.compose.material3.ColorScheme] (it carries
 * soft/ink variants per role plus 3-step ink and surface ramps), so it is provided through a
 * CompositionLocal in [BeaconTheme]. Values are the OKLCH tokens from the prototype converted to
 * sRGB. Damage colors are trauma-informed: green / amber / muted terracotta (never pure red).
 */
@Immutable
data class BeaconColors(
    val primary: Color,
    val primaryPressed: Color,
    val primarySoft: Color,
    val primaryInk: Color,
    val onPrimary: Color,
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val surface3: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val line: Color,
    val ok: Color,
    val okSoft: Color,
    val slight: Color,
    val slightSoft: Color,
    val warn: Color,
    val warnSoft: Color,
    val severe: Color,
    val severeSoft: Color,
    val complete: Color,
    val completeSoft: Color,
    val info: Color,
) {
    // 5-level EMS-98 ordinal palette (trauma-informed: muted green → terracotta, never pure red).
    fun damageColor(level: DamageLevel): Color = when (level) {
        DamageLevel.NONE -> ok
        DamageLevel.SLIGHT -> slight
        DamageLevel.MODERATE -> warn
        DamageLevel.SEVERE -> severe
        DamageLevel.DESTROYED -> complete
    }

    fun damageSoft(level: DamageLevel): Color = when (level) {
        DamageLevel.NONE -> okSoft
        DamageLevel.SLIGHT -> slightSoft
        DamageLevel.MODERATE -> warnSoft
        DamageLevel.SEVERE -> severeSoft
        DamageLevel.DESTROYED -> completeSoft
    }
}

val BeaconLightColors = BeaconColors(
    primary = Color(0xFF6E4FC4),
    primaryPressed = Color(0xFF5F3FB3),
    primarySoft = Color(0xFFE6DCF6),
    primaryInk = Color(0xFF3A2470),
    onPrimary = Color(0xFFFFFFFF),
    bg = Color(0xFFFAF8FD),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFF2EEF8),
    surface3 = Color(0xFFE6DFF1),
    ink = Color(0xFF2C2733),
    ink2 = Color(0xFF5C5567),
    ink3 = Color(0xFF8B8497),
    line = Color(0xFFE4DFEA),
    ok = Color(0xFF3FA463),
    okSoft = Color(0xFFDCF1E3),
    slight = Color(0xFF8FB339),
    slightSoft = Color(0xFFEAF1D7),
    warn = Color(0xFFD49A2A),
    warnSoft = Color(0xFFF6ECD6),
    severe = Color(0xFFC8743C),
    severeSoft = Color(0xFFF6E3D7),
    complete = Color(0xFFB66250),
    completeSoft = Color(0xFFF4E6E2),
    info = Color(0xFF5687FF),
)
