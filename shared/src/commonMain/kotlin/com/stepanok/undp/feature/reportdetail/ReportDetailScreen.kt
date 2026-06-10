@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.stepanok.undp.feature.reportdetail

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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.stepanok.undp.core.format.relativeTime
import com.stepanok.undp.designsystem.components.DamageChip
import com.stepanok.undp.core.media.ReportPhoto
import com.stepanok.undp.designsystem.safeTopPadding
import com.stepanok.undp.designsystem.icons.BeaconIcons
import com.stepanok.undp.designsystem.labels.crisisLabel
import com.stepanok.undp.designsystem.labels.damageLabel
import com.stepanok.undp.designsystem.labels.debrisLabel
import com.stepanok.undp.designsystem.labels.infraLabel
import com.stepanok.undp.designsystem.labels.rejectionReasonLabel
import com.stepanok.undp.designsystem.theme.BeaconTheme
import com.stepanok.undp.domain.model.BuildingVersion
import com.stepanok.undp.domain.model.Report
import com.stepanok.undp.domain.model.SyncState
import com.stepanok.undp.map.BeaconMap
import com.stepanok.undp.map.GeoPoint
import com.stepanok.undp.map.ReportPin
import org.jetbrains.compose.resources.stringResource
import org.koin.core.parameter.parametersOf
import undp.shared.generated.resources.Res
import undp.shared.generated.resources.detail_anonymised
import undp.shared.generated.resources.detail_description
import undp.shared.generated.resources.detail_location_unresolved
import undp.shared.generated.resources.detail_meta_captured
import undp.shared.generated.resources.detail_meta_crisis
import undp.shared.generated.resources.detail_meta_debris
import undp.shared.generated.resources.detail_meta_type
import undp.shared.generated.resources.detail_this_building
import undp.shared.generated.resources.detail_timeline
import undp.shared.generated.resources.detail_translated_from
import undp.shared.generated.resources.report_label
import undp.shared.generated.resources.status_rejected

data class ReportDetailScreen(val reportId: String) : Screen {
    @Composable
    override fun Content() {
        val model = koinScreenModel<ReportDetailScreenModel> { parametersOf(reportId) }
        val state by model.state.collectAsState()
        val colors = BeaconTheme.colors
        val nav = LocalNavigator.currentOrThrow
        val report = state.report ?: return

        Column(Modifier.fillMaxSize().background(colors.bg).verticalScroll(rememberScrollState())) {
            // Photo header
            Box(Modifier.fillMaxWidth().height(260.dp)) {
                ReportPhoto(photo = report.photos.firstOrNull(), modifier = Modifier.fillMaxSize(), placeholderLabel = "report photo")
                Box(
                    Modifier.align(Alignment.TopStart).safeTopPadding().padding(14.dp).size(40.dp).clip(CircleShape).background(colors.surface.copy(alpha = 0.95f)).clickable { nav.pop() },
                    contentAlignment = Alignment.Center,
                ) { Icon(BeaconIcons.ArrowLeft, contentDescription = "Back", tint = colors.ink, modifier = Modifier.size(18.dp)) }
                Row(
                    Modifier.align(Alignment.BottomStart).padding(14.dp).clip(CircleShape).background(Color(0x99000000)).padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(BeaconIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                    Text(stringResource(Res.string.detail_anonymised), style = BeaconTheme.typography.caption, color = Color.White)
                }
            }

            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(stringResource(Res.string.report_label), style = BeaconTheme.typography.micro, color = colors.ink3)
                        Text("#${report.id}", style = BeaconTheme.typography.titleM, color = colors.ink)
                    }
                    DamageChip(level = report.damage, label = damageLabel(report.damage))
                }

                // The server permanently rejected the reporter's own report — show the honest
                // outcome + reason here too, not just the list chip.
                (report.sync as? SyncState.Rejected)?.takeIf { report.isMine }?.let { rejected ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.warnSoft).padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(BeaconIcons.Warning, contentDescription = null, tint = colors.warn, modifier = Modifier.size(16.dp))
                        Text(
                            "${stringResource(Res.string.status_rejected)} · ${rejectionReasonLabel(rejected)}",
                            style = BeaconTheme.typography.label, color = colors.warn,
                        )
                    }
                }

                // Mini-map — only when the report has a resolved point. Landmark-only (unresolved)
                // reports show a textual placeholder instead of centering the map on 0,0.
                val loc = report.location
                val lat = loc.lat
                val lng = loc.lng
                if (loc.locationResolved && lat != null && lng != null) {
                    Box(Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(16.dp)).border(1.dp, colors.line, RoundedCornerShape(16.dp))) {
                        BeaconMap(
                            reports = listOf(ReportPin(report.id, lat, lng, report.damage)),
                            center = GeoPoint(lat, lng),
                            zoom = 16.0,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(BeaconIcons.Pin, contentDescription = null, tint = colors.primary, modifier = Modifier.size(14.dp))
                        Text(loc.plusCode?.takeIf { it.isNotBlank() && it != "garden.tribe.sparkle" } ?: "—", style = BeaconTheme.typography.mono, color = colors.ink2)
                        loc.gpsAccuracyMeters?.let {
                            Text("· ±${it.toInt()} m", style = BeaconTheme.typography.caption, color = colors.ink3)
                        }
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surface).border(1.dp, colors.line, RoundedCornerShape(16.dp)).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(BeaconIcons.Pin, contentDescription = null, tint = colors.ink3, modifier = Modifier.size(14.dp))
                        Text(
                            stringResource(Res.string.detail_location_unresolved, loc.landmark?.takeIf { it.isNotBlank() } ?: "—"),
                            style = BeaconTheme.typography.bodyS, color = colors.ink2,
                        )
                    }
                }

                // Meta grid
                val infraText = buildList { for (t in report.infraTypes) add(infraLabel(t)) }.joinToString().ifBlank { "—" }
                val crisisText = buildList { for (n in report.crisisNature) add(crisisLabel(n)) }.joinToString().ifBlank { "—" }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetaCell(stringResource(Res.string.detail_meta_type), infraText, Modifier.weight(1f))
                    MetaCell(stringResource(Res.string.detail_meta_crisis), crisisText, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetaCell(stringResource(Res.string.detail_meta_debris), debrisLabel(report.debris), Modifier.weight(1f))
                    MetaCell(stringResource(Res.string.detail_meta_captured), relativeTime(model.now(), report.capturedAt), Modifier.weight(1f))
                }

                // Description
                report.description?.let { desc ->
                    Column {
                        Text(stringResource(Res.string.detail_description).uppercase(), style = BeaconTheme.typography.micro, color = colors.ink3)
                        Spacer(Modifier.height(6.dp))
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surface).border(1.dp, colors.line, RoundedCornerShape(14.dp)).padding(12.dp)) {
                            Text(desc.translated ?: desc.original, style = BeaconTheme.typography.bodyS, color = colors.ink)
                            if (desc.translated != null && desc.originalLang != "auto") {
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(BeaconIcons.Language, contentDescription = null, tint = colors.ink3, modifier = Modifier.size(12.dp))
                                    Text(stringResource(Res.string.detail_translated_from, desc.originalLang.uppercase()), style = BeaconTheme.typography.caption, color = colors.ink3)
                                }
                            }
                        }
                    }
                }

                // Damage timeline
                state.timeline?.takeIf { it.versions.size > 1 }?.let { tl ->
                    Column {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(Res.string.detail_timeline).uppercase(), style = BeaconTheme.typography.micro, color = colors.ink3)
                            Text(stringResource(Res.string.detail_this_building), style = BeaconTheme.typography.caption, color = colors.ink3)
                        }
                        Spacer(Modifier.height(8.dp))
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surface).border(1.dp, colors.line, RoundedCornerShape(16.dp)).padding(14.dp)) {
                            tl.versions.reversed().forEachIndexed { i, v ->
                                TimelineRow(v, now = model.now(), isLast = i == tl.versions.lastIndex)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaCell(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = BeaconTheme.colors
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(colors.surface).border(1.dp, colors.line, RoundedCornerShape(12.dp)).padding(10.dp)) {
        Text(label.uppercase(), style = BeaconTheme.typography.micro, color = colors.ink3)
        Spacer(Modifier.height(2.dp))
        Text(value, style = BeaconTheme.typography.bodyS, color = colors.ink, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TimelineRow(version: BuildingVersion, now: kotlin.time.Instant, isLast: Boolean) {
    val colors = BeaconTheme.colors
    val color = colors.damageColor(version.damage)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.padding(top = 4.dp).size(if (version.isCurrent) 14.dp else 10.dp).clip(CircleShape).background(color))
            if (!isLast) Box(Modifier.width(1.dp).height(28.dp).background(colors.line))
        }
        Column(Modifier.weight(1f).padding(bottom = if (isLast) 0.dp else 6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(damageLabel(version.damage), style = BeaconTheme.typography.label, color = color)
                Text(relativeTime(now, version.at), style = BeaconTheme.typography.caption, color = colors.ink3)
            }
            Text(version.note, style = BeaconTheme.typography.caption, color = colors.ink2)
        }
    }
}
