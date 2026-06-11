package com.stepanok.undp.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.stepanok.undp.domain.model.DamageTier

/**
 * Beacon's palette — richer than Material3's [androidx.compose.material3.ColorScheme] (it carries
 * soft/ink variants per role plus 3-step ink and surface ramps), so it is provided through a
 * CompositionLocal in [BeaconTheme]. Values are aligned to the UNDP Design System
 * (design.undp.org): brand blue #006EB5, the neutral gray ramp (#FAFAFA→#232E3D), and the
 * semantic green/yellow/red used for the three damage tiers — matching the analyst dashboard
 * and the exported damage classification.
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
    // 3-tier palette — UNDP semantic green → yellow → red (matches the dashboard/export).
    fun damageColor(tier: DamageTier): Color = when (tier) {
        DamageTier.MINIMAL -> ok
        DamageTier.PARTIAL -> warn
        DamageTier.COMPLETE -> complete
    }

    fun damageSoft(tier: DamageTier): Color = when (tier) {
        DamageTier.MINIMAL -> okSoft
        DamageTier.PARTIAL -> warnSoft
        DamageTier.COMPLETE -> completeSoft
    }
}

// UNDP Design System primitives (undp/design-system): blue-600 brand, gray ramp,
// semantic green-600 / yellow-600 / red-600, azure-600 accent.
val BeaconLightColors = BeaconColors(
    primary = Color(0xFF006EB5),        // blue-600 (UNDP brand)
    primaryPressed = Color(0xFF1F5A95), // blue-700
    primarySoft = Color(0xFFD7E9F9),    // light blue tint
    primaryInk = Color(0xFF1F5A95),     // blue-700 (text on soft)
    onPrimary = Color(0xFFFFFFFF),
    bg = Color(0xFFFAFAFA),             // gray-100
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFF7F7F7),       // gray-200
    surface3 = Color(0xFFEDEFF0),       // gray-300
    ink = Color(0xFF232E3D),            // gray-700 (near-black body text)
    ink2 = Color(0xFF55606E),           // gray-600
    ink3 = Color(0xFF84929D),           // gray-530
    line = Color(0xFFD4D6D8),           // gray-400 (visible border)
    ok = Color(0xFF59BA47),             // green-600  (minimal)
    okSoft = Color(0xFFE7F6E4),
    slight = Color(0xFF6DE354),         // green-400 (legacy slot; unused by 3-tier)
    slightSoft = Color(0xFFE7F6E4),
    warn = Color(0xFFFBC412),           // yellow-600 (partial)
    warnSoft = Color(0xFFFFF4D1),
    severe = Color(0xFFEE402D),         // red-400 (legacy slot; unused by 3-tier)
    severeSoft = Color(0xFFFFE3DD),
    complete = Color(0xFFD12800),       // red-600 (complete)
    completeSoft = Color(0xFFFFE3DD),
    info = Color(0xFF00C1FF),           // azure-600 (accent)
)
