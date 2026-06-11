package com.stepanok.undp.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.stepanok.undp.core.share.rememberShareHandler
import com.stepanok.undp.designsystem.components.BeaconBottomSheet
import com.stepanok.undp.designsystem.components.BeaconButton
import com.stepanok.undp.designsystem.safeTopPadding
import com.stepanok.undp.designsystem.components.BeaconButtonVariant
import com.stepanok.undp.designsystem.icons.BeaconIcons
import com.stepanok.undp.designsystem.theme.BeaconTheme
import com.stepanok.undp.domain.model.Badge
import com.stepanok.undp.feature.offline.OfflineDownloadsScreen
import com.stepanok.undp.i18n.UnLanguage
import com.stepanok.undp.i18n.appLocaleOverride
import org.jetbrains.compose.resources.stringResource
import undp.shared.generated.resources.Res
import undp.shared.generated.resources.menu_about
import undp.shared.generated.resources.menu_about_sub
import undp.shared.generated.resources.export_csv
import undp.shared.generated.resources.export_geojson
import undp.shared.generated.resources.language_sheet_sub
import undp.shared.generated.resources.language_sheet_title
import undp.shared.generated.resources.language_system
import undp.shared.generated.resources.menu_export
import undp.shared.generated.resources.menu_export_sub
import undp.shared.generated.resources.menu_language
import undp.shared.generated.resources.menu_offline
import undp.shared.generated.resources.menu_offline_sub
import undp.shared.generated.resources.menu_privacy
import undp.shared.generated.resources.menu_privacy_sub
import undp.shared.generated.resources.profile_add_alias
import undp.shared.generated.resources.profile_anon
import undp.shared.generated.resources.profile_buildings
import undp.shared.generated.resources.profile_points
import undp.shared.generated.resources.profile_recognition
import undp.shared.generated.resources.profile_reports
import undp.shared.generated.resources.profile_title

object ProfileScreen : Screen {
    @Composable
    override fun Content() {
        val model = koinScreenModel<ProfileScreenModel>()
        val state by model.state.collectAsState()
        val colors = BeaconTheme.colors
        val nav = LocalNavigator.currentOrThrow
        val shareHandler = rememberShareHandler()
        var showLangSheet by remember { mutableStateOf(false) }
        var showExport by remember { mutableStateOf(false) }
        val profile = state.profile ?: return
        val currentLangLabel = appLocaleOverride?.let { UnLanguage.fromTag(it).nativeName }
            ?: stringResource(Res.string.language_system)

        Column(Modifier.fillMaxSize().background(colors.bg).verticalScroll(rememberScrollState())) {
            Row(
                Modifier.fillMaxWidth().safeTopPadding().padding(horizontal = 20.dp).padding(top = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(Res.string.profile_title), style = BeaconTheme.typography.titleL, color = colors.ink)
                Box(Modifier.size(40.dp).clip(CircleShape).background(colors.surface).clickable { }, contentAlignment = Alignment.Center) {
                    Icon(BeaconIcons.Settings, contentDescription = null, tint = colors.ink2, modifier = Modifier.size(18.dp))
                }
            }

            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Identity card
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(colors.surface).border(1.dp, colors.line, RoundedCornerShape(22.dp)).clickable { }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(colors.primary), contentAlignment = Alignment.Center) {
                        Text(profile.anonymousId.take(2), style = BeaconTheme.typography.titleM, color = colors.onPrimary)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("${stringResource(Res.string.profile_anon)} · ${profile.anonymousId}", style = BeaconTheme.typography.titleS, color = colors.ink)
                        Text(profile.alias ?: stringResource(Res.string.profile_add_alias), style = BeaconTheme.typography.caption, color = colors.ink3)
                    }
                    Icon(BeaconIcons.ChevronRight, contentDescription = null, tint = colors.ink3, modifier = Modifier.size(18.dp))
                }

                // Stats grid
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard("${profile.reportCount}", stringResource(Res.string.profile_reports), colors.primary, Modifier.weight(1f))
                    StatCard("${profile.buildingCount}", stringResource(Res.string.profile_buildings), colors.ink, Modifier.weight(1f))
                    StatCard("${profile.points}", stringResource(Res.string.profile_points), colors.ok, Modifier.weight(1f))
                }

                // Recognition
                Text(stringResource(Res.string.profile_recognition).uppercase(), style = BeaconTheme.typography.micro, color = colors.ink3)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    profile.badges.take(3).forEach { badge ->
                        BadgeCard(badge, Modifier.weight(1f))
                    }
                }

                // Menu
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surface).border(1.dp, colors.line, RoundedCornerShape(18.dp))) {
                    MenuRow(BeaconIcons.Download, stringResource(Res.string.menu_export), stringResource(Res.string.menu_export_sub), onClick = { showExport = true })
                    Divider()
                    MenuRow(BeaconIcons.Map, stringResource(Res.string.menu_offline), stringResource(Res.string.menu_offline_sub), onClick = { nav.push(OfflineDownloadsScreen) })
                    Divider()
                    MenuRow(BeaconIcons.Language, stringResource(Res.string.menu_language), currentLangLabel, onClick = { showLangSheet = true })
                    Divider()
                    MenuRow(BeaconIcons.Shield, stringResource(Res.string.menu_privacy), stringResource(Res.string.menu_privacy_sub))
                    Divider()
                    MenuRow(BeaconIcons.Info, stringResource(Res.string.menu_about), stringResource(Res.string.menu_about_sub))
                }
            }
        }

        if (showLangSheet) {
            LanguageSheet(onDismiss = { showLangSheet = false })
        }
        if (showExport) {
            ExportSheet(
                onGeoJson = { shareHandler.share("beacon-reports.geojson", "application/geo+json", model.exportGeoJson()); showExport = false },
                onCsv = { shareHandler.share("beacon-reports.csv", "text/csv", model.exportCsv()); showExport = false },
                onDismiss = { showExport = false },
            )
        }
    }
}

@Composable
private fun ExportSheet(onGeoJson: () -> Unit, onCsv: () -> Unit, onDismiss: () -> Unit) {
    val colors = BeaconTheme.colors
    BeaconBottomSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(stringResource(Res.string.menu_export), style = BeaconTheme.typography.titleM, color = colors.ink)
            Text(stringResource(Res.string.menu_export_sub), style = BeaconTheme.typography.bodyS, color = colors.ink2)
            Spacer(Modifier.height(16.dp))
            BeaconButton(
                text = stringResource(Res.string.export_geojson),
                onClick = onGeoJson,
                leadingIcon = BeaconIcons.Download,
                fullWidth = true,
            )
            Spacer(Modifier.height(10.dp))
            BeaconButton(
                text = stringResource(Res.string.export_csv),
                onClick = onCsv,
                variant = BeaconButtonVariant.Secondary,
                leadingIcon = BeaconIcons.Download,
                fullWidth = true,
            )
        }
    }
}

@Composable
private fun LanguageSheet(onDismiss: () -> Unit) {
    val colors = BeaconTheme.colors
    BeaconBottomSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            Text(stringResource(Res.string.language_sheet_title), style = BeaconTheme.typography.titleM, color = colors.ink)
            Text(stringResource(Res.string.language_sheet_sub), style = BeaconTheme.typography.bodyS, color = colors.ink2)
            Spacer(Modifier.height(12.dp))
            LanguageRow(stringResource(Res.string.language_system), selected = appLocaleOverride == null) {
                appLocaleOverride = null; onDismiss()
            }
            UnLanguage.entries.forEach { lang ->
                LanguageRow(lang.nativeName, selected = appLocaleOverride == lang.tag) {
                    appLocaleOverride = lang.tag; onDismiss()
                }
            }
        }
    }
}

@Composable
private fun LanguageRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = BeaconTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = BeaconTheme.typography.titleS, color = colors.ink)
        if (selected) Icon(BeaconIcons.Check, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun StatCard(value: String, label: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    val colors = BeaconTheme.colors
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(colors.surface).border(1.dp, colors.line, RoundedCornerShape(16.dp)).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = BeaconTheme.typography.titleM, color = color)
        Text(label, style = BeaconTheme.typography.caption, color = colors.ink3, textAlign = TextAlign.Center)
    }
}

@Composable
private fun BadgeCard(badge: Badge, modifier: Modifier = Modifier) {
    val colors = BeaconTheme.colors
    val icon: ImageVector = when (badge.id) {
        "first-responder" -> BeaconIcons.Medal
        "map-maker" -> BeaconIcons.Map
        else -> BeaconIcons.Award
    }
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(colors.surface).border(1.dp, colors.line, RoundedCornerShape(16.dp)).padding(12.dp).alpha(if (badge.earned) 1f else 0.55f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(if (badge.earned) colors.warn else colors.surface3),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = if (badge.earned) androidx.compose.ui.graphics.Color.White else colors.ink3, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.height(6.dp))
        Text(badge.name, style = BeaconTheme.typography.caption, color = colors.ink, textAlign = TextAlign.Center)
        if (badge.progressLabel != null) {
            Text(badge.progressLabel, style = BeaconTheme.typography.micro, color = colors.ink3)
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, title: String, sub: String, onClick: () -> Unit = {}) {
    val colors = BeaconTheme.colors
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(colors.primarySoft), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = BeaconTheme.typography.titleS, color = colors.ink)
            Text(sub, style = BeaconTheme.typography.caption, color = colors.ink3)
        }
        Icon(BeaconIcons.ChevronRight, contentDescription = null, tint = colors.ink3, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(BeaconTheme.colors.line))
}
