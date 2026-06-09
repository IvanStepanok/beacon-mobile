package com.stepanok.undp.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Type scale ported 1:1 from the prototype (px → sp). DM Sans / DM Mono are wired in
 * [rememberBeaconTypography] via the [sans]/[mono] families; until the bundled fonts land they
 * default to the system families, so swapping in DM Sans + Noto fallbacks is a one-call change.
 */
@Immutable
data class BeaconTypography(
    val display: TextStyle,
    val titleL: TextStyle,
    val titleM: TextStyle,
    val titleS: TextStyle,
    val body: TextStyle,
    val bodyS: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
    val micro: TextStyle,
    val mono: TextStyle,
    val numXL: TextStyle,
)

@Composable
fun rememberBeaconTypography(
    sans: FontFamily = FontFamily.Default,
    mono: FontFamily = FontFamily.Monospace,
): BeaconTypography = remember(sans, mono) {
    BeaconTypography(
        display = TextStyle(fontFamily = sans, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 33.sp, letterSpacing = (-0.02).em),
        titleL = TextStyle(fontFamily = sans, fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = (-0.02).em),
        titleM = TextStyle(fontFamily = sans, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = (-0.01).em),
        titleS = TextStyle(fontFamily = sans, fontWeight = FontWeight.Bold, fontSize = 16.sp),
        body = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
        bodyS = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
        label = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = (-0.005).em),
        caption = TextStyle(fontFamily = sans, fontWeight = FontWeight.Medium, fontSize = 12.sp),
        micro = TextStyle(fontFamily = sans, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.1.em),
        mono = TextStyle(fontFamily = mono, fontWeight = FontWeight.Medium, fontSize = 14.sp),
        numXL = TextStyle(fontFamily = sans, fontWeight = FontWeight.Bold, fontSize = 36.sp, letterSpacing = (-0.03).em),
    )
}
