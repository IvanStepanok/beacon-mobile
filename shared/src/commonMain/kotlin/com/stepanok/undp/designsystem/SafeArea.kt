package com.stepanok.undp.designsystem

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Modifier

/**
 * Safe-area helpers. The app draws edge-to-edge (map/camera bleed under the notch and home
 * indicator); these pad just the chrome so controls clear the iOS Dynamic Island / status bar
 * (top) and the home indicator / Android nav bar (bottom). Both delegate to the non-composable
 * inset Modifier factories so they can be used anywhere in a modifier chain.
 */

/** Pads the top by the status-bar / Dynamic-Island safe-area inset. */
fun Modifier.safeTopPadding(): Modifier = statusBarsPadding()

/** Pads the bottom by the navigation-bar / home-indicator safe-area inset. */
fun Modifier.safeBottomPadding(): Modifier = navigationBarsPadding()
