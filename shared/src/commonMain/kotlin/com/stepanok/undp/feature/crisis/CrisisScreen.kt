package com.stepanok.undp.feature.crisis

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import com.stepanok.undp.designsystem.icons.BeaconIcons
import com.stepanok.undp.designsystem.safeTopPadding
import com.stepanok.undp.designsystem.theme.BeaconTheme
import com.stepanok.undp.domain.model.DangerSeverity
import org.jetbrains.compose.resources.stringResource
import undp.shared.generated.resources.Res
import undp.shared.generated.resources.crisis_active_near_you
import undp.shared.generated.resources.crisis_alerts_sub
import undp.shared.generated.resources.crisis_alerts_title
import undp.shared.generated.resources.crisis_danger_zones
import undp.shared.generated.resources.crisis_no_danger
import undp.shared.generated.resources.crisis_none_nearby
import undp.shared.generated.resources.crisis_none_nearby_sub
import undp.shared.generated.resources.crisis_safety_tip
import undp.shared.generated.resources.crisis_safety_title
import undp.shared.generated.resources.crisis_started
import undp.shared.generated.resources.crisis_title
import undp.shared.generated.resources.danger_caution
import undp.shared.generated.resources.danger_critical
import undp.shared.generated.resources.danger_warning
import undp.shared.generated.resources.map_crisis_source

object CrisisScreen : Screen {
    @Composable
    override fun Content() {
        val model = koinScreenModel<CrisisScreenModel>()
        val state by model.state.collectAsState()
        val colors = BeaconTheme.colors

        Column(
            Modifier.fillMaxSize().background(colors.bg).safeTopPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(top = 14.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(Res.string.crisis_title), style = BeaconTheme.typography.titleL, color = colors.ink)

            // Active crisis card
            state.crisis?.let { crisis ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.completeSoft)
                        .border(1.dp, colors.complete.copy(alpha = 0.4f), RoundedCornerShape(20.dp)).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(colors.complete), contentAlignment = Alignment.Center) {
                        Icon(BeaconIcons.Warning, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(crisis.title, style = BeaconTheme.typography.titleM, color = colors.complete)
                        Text(crisis.area, style = BeaconTheme.typography.bodyS, color = colors.ink)
                        Text(
                            "${stringResource(Res.string.crisis_started, state.started)} · ${stringResource(Res.string.map_crisis_source)}",
                            style = BeaconTheme.typography.caption, color = colors.ink3,
                        )
                    }
                }
            }

            // Danger zones
            Text(stringResource(Res.string.crisis_danger_zones).uppercase(), style = BeaconTheme.typography.micro, color = colors.ink3)
            if (state.dangerZones.isEmpty()) {
                Text(stringResource(Res.string.crisis_no_danger), style = BeaconTheme.typography.bodyS, color = colors.ink3)
            } else {
                state.dangerZones.forEach { zone ->
                    val color = when (zone.severity) {
                        DangerSeverity.CRITICAL -> colors.complete
                        DangerSeverity.WARNING -> colors.warn
                        DangerSeverity.CAUTION -> colors.ink3
                    }
                    val severityLabel = when (zone.severity) {
                        DangerSeverity.CRITICAL -> stringResource(Res.string.danger_critical)
                        DangerSeverity.WARNING -> stringResource(Res.string.danger_warning)
                        DangerSeverity.CAUTION -> stringResource(Res.string.danger_caution)
                    }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surface)
                            .border(1.dp, colors.line, RoundedCornerShape(16.dp)).padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(color).padding(top = 4.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(zone.name, style = BeaconTheme.typography.titleS, color = colors.ink)
                                Text(severityLabel.uppercase(), style = BeaconTheme.typography.micro, color = color)
                            }
                            Text(zone.note, style = BeaconTheme.typography.bodyS, color = colors.ink2)
                        }
                    }
                }
            }

            // No crisis covering the user + nothing nearby → say so plainly (instead of
            // listing events thousands of km away, which is noise for a local reporter).
            if (state.crisis == null && state.activeCrises.isEmpty()) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surface2).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(BeaconIcons.Shield, contentDescription = null, tint = colors.ok, modifier = Modifier.size(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(Res.string.crisis_none_nearby), style = BeaconTheme.typography.titleS, color = colors.ink)
                        Text(stringResource(Res.string.crisis_none_nearby_sub), style = BeaconTheme.typography.caption, color = colors.ink3)
                    }
                }
            }

            // Active crises NEAR the user (within ~300 km), nearest first.
            if (state.activeCrises.isNotEmpty()) {
                Text(stringResource(Res.string.crisis_active_near_you), style = BeaconTheme.typography.micro, color = colors.ink3)
                state.activeCrises.forEach { c ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surface)
                            .border(1.dp, colors.line, RoundedCornerShape(16.dp)).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(colors.primarySoft),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(BeaconIcons.Warning, contentDescription = null, tint = colors.primary, modifier = Modifier.size(17.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(c.title, style = BeaconTheme.typography.titleS, color = colors.ink)
                            Text(c.area, style = BeaconTheme.typography.caption, color = colors.ink3)
                        }
                        c.distanceKm?.let {
                            Text("${it.toInt()} km", style = BeaconTheme.typography.caption, color = colors.ink2)
                        }
                    }
                }
            }

            // Crisis alerts toggle
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surface)
                    .border(1.dp, colors.line, RoundedCornerShape(16.dp)).clickable { model.onIntent(CrisisIntent.ToggleAlerts) }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(colors.primarySoft), contentAlignment = Alignment.Center) {
                    Icon(BeaconIcons.Bell, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(Res.string.crisis_alerts_title), style = BeaconTheme.typography.titleS, color = colors.ink)
                    Text(stringResource(Res.string.crisis_alerts_sub), style = BeaconTheme.typography.caption, color = colors.ink3)
                }
                Box(
                    Modifier.size(width = 40.dp, height = 22.dp).clip(CircleShape).background(if (state.alertsOn) colors.primary else colors.surface3).padding(2.dp),
                    contentAlignment = if (state.alertsOn) Alignment.CenterEnd else Alignment.CenterStart,
                ) { Box(Modifier.size(18.dp).clip(CircleShape).background(Color.White)) }
            }

            // Safety tip
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surface2).padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(BeaconIcons.Shield, contentDescription = null, tint = colors.ok, modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(Res.string.crisis_safety_title), style = BeaconTheme.typography.titleS, color = colors.ink)
                    Spacer(Modifier.height(2.dp))
                    Text(stringResource(Res.string.crisis_safety_tip), style = BeaconTheme.typography.bodyS, color = colors.ink2)
                }
            }
        }
    }
}
