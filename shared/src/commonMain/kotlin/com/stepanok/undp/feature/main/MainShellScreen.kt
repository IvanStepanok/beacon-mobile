package com.stepanok.undp.feature.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.zIndex
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import cafe.adriel.voyager.navigator.tab.TabNavigator
import com.stepanok.undp.designsystem.components.BeaconBottomBar
import com.stepanok.undp.designsystem.components.BeaconTab
import com.stepanok.undp.designsystem.theme.BeaconTheme
import com.stepanok.undp.feature.capture.CaptureFlowScreen
import com.stepanok.undp.feature.crisis.CrisisScreen
import com.stepanok.undp.feature.map.MapScreen
import com.stepanok.undp.feature.profile.ProfileScreen
import com.stepanok.undp.feature.reports.ReportsScreen
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import undp.shared.generated.resources.Res
import undp.shared.generated.resources.nav_crisis
import undp.shared.generated.resources.nav_map
import undp.shared.generated.resources.nav_profile
import undp.shared.generated.resources.nav_reports

object MapTab : Tab {
    override val options: TabOptions @Composable get() = TabOptions(index = 0u, title = stringResource(Res.string.nav_map))
    @Composable override fun Content() = Navigator(MapScreen)
}

object ReportsTab : Tab {
    override val options: TabOptions @Composable get() = TabOptions(index = 1u, title = stringResource(Res.string.nav_reports))
    @Composable override fun Content() = Navigator(ReportsScreen)
}

object CrisisTab : Tab {
    override val options: TabOptions @Composable get() = TabOptions(index = 2u, title = stringResource(Res.string.nav_crisis))
    @Composable override fun Content() = Navigator(CrisisScreen)
}

object ProfileTab : Tab {
    override val options: TabOptions @Composable get() = TabOptions(index = 3u, title = stringResource(Res.string.nav_profile))
    @Composable override fun Content() = Navigator(ProfileScreen)
}

private fun BeaconTab.toTab(): Tab = when (this) {
    BeaconTab.Map -> MapTab
    BeaconTab.Reports -> ReportsTab
    BeaconTab.Crisis -> CrisisTab
    BeaconTab.Profile -> ProfileTab
}

private fun Tab.toBeaconTab(): BeaconTab = when (this) {
    ReportsTab -> BeaconTab.Reports
    CrisisTab -> BeaconTab.Crisis
    ProfileTab -> BeaconTab.Profile
    else -> BeaconTab.Map
}

/** Localized bottom-tab labels — the nav_* keys exist in all 6 locales. */
private fun BeaconTab.labelRes(): StringResource = when (this) {
    BeaconTab.Map -> Res.string.nav_map
    BeaconTab.Reports -> Res.string.nav_reports
    BeaconTab.Crisis -> Res.string.nav_crisis
    BeaconTab.Profile -> Res.string.nav_profile
}

private val ALL_TABS = listOf(MapTab, ReportsTab, CrisisTab, ProfileTab)

object MainShellScreen : Screen {
    @Composable
    override fun Content() {
        val rootNav = LocalNavigator.currentOrThrow
        TabNavigator(MapTab) {
            val tabNav = LocalTabNavigator.current
            Scaffold(
                containerColor = BeaconTheme.colors.bg,
                // We handle safe-area insets explicitly per screen (map/camera stay full-bleed),
                // so the Scaffold itself adds none — only the bottom-bar height pads the content.
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    // Resolve the localized labels here — BeaconBottomBar's label lambda is not composable.
                    val labels = BeaconTab.entries.associateWith { stringResource(it.labelRes()) }
                    BeaconBottomBar(
                        active = tabNav.current.toBeaconTab(),
                        label = { labels.getValue(it) },
                        onSelect = { tabNav.current = it.toTab() },
                        onCapture = { rootNav.push(CaptureFlowScreen) },
                    )
                },
            ) { padding ->
                // Keep every tab composed so switching back doesn't reload the map / lose scroll.
                // Active tab is drawn on top and opaque; inactive stay alive but hidden underneath.
                Box(Modifier.fillMaxSize().padding(padding)) {
                    ALL_TABS.forEach { tab ->
                        val active = tabNav.current.key == tab.key
                        Box(
                            Modifier
                                .fillMaxSize()
                                .zIndex(if (active) 1f else 0f)
                                .then(if (active) Modifier else Modifier.alpha(0f)),
                        ) {
                            tab.Content()
                        }
                    }
                }
            }
        }
    }
}
