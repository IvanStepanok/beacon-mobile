package com.stepanok.undp

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.stepanok.undp.designsystem.theme.BeaconTheme
import com.stepanok.undp.di.beaconModules
import com.stepanok.undp.feature.root.RootNavigator
import com.stepanok.undp.i18n.BeaconAppEnvironment
import org.koin.compose.KoinApplication

@Composable
@Preview
fun App() {
    KoinApplication(application = { modules(beaconModules()) }) {
        BeaconAppEnvironment {
            BeaconTheme {
                RootNavigator()
            }
        }
    }
}
