package com.stepanok.undp.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

val LocalBeaconColors = staticCompositionLocalOf { BeaconLightColors }
val LocalBeaconTypography = staticCompositionLocalOf<BeaconTypography> { error("BeaconTypography not provided") }
val LocalBeaconShapes = staticCompositionLocalOf { BeaconShapes() }

/** Single entry point for Beacon's design tokens. Read via `BeaconTheme.colors/typography/shapes`. */
object BeaconTheme {
    val colors: BeaconColors @Composable @ReadOnlyComposable get() = LocalBeaconColors.current
    val typography: BeaconTypography @Composable @ReadOnlyComposable get() = LocalBeaconTypography.current
    val shapes: BeaconShapes @Composable @ReadOnlyComposable get() = LocalBeaconShapes.current
}

@Composable
fun BeaconTheme(content: @Composable () -> Unit) {
    val colors = BeaconLightColors
    val typography = rememberBeaconTypography()
    val shapes = BeaconShapes()

    // Bridge into a Material3 scheme so raw M3 widgets (TextField, ModalBottomSheet, etc.) inherit.
    val material3Scheme = lightColorScheme(
        primary = colors.primary,
        onPrimary = colors.onPrimary,
        primaryContainer = colors.primarySoft,
        onPrimaryContainer = colors.primaryInk,
        secondary = colors.primary,
        background = colors.bg,
        onBackground = colors.ink,
        surface = colors.surface,
        onSurface = colors.ink,
        surfaceVariant = colors.surface2,
        onSurfaceVariant = colors.ink2,
        outline = colors.line,
        outlineVariant = colors.line,
        error = colors.complete,
    )

    CompositionLocalProvider(
        LocalBeaconColors provides colors,
        LocalBeaconTypography provides typography,
        LocalBeaconShapes provides shapes,
    ) {
        MaterialTheme(colorScheme = material3Scheme, content = content)
    }
}
