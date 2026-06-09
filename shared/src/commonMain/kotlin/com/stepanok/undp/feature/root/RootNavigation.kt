package com.stepanok.undp.feature.root

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import com.stepanok.undp.core.storage.PrefKeys
import com.stepanok.undp.core.storage.Prefs
import com.stepanok.undp.feature.main.MainShellScreen
import com.stepanok.undp.feature.onboarding.OnboardingScreen

/** Root navigation host: onboarding (first launch only) → main shell. */
@Composable
fun RootNavigator() {
    val start: Screen = remember {
        if (Prefs.getBool(PrefKeys.ONBOARDING_SEEN, false)) MainShellScreen else OnboardingScreen
    }
    Navigator(start) { navigator ->
        SlideTransition(navigator)
    }
}
