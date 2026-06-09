package com.stepanok.undp.feature.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.stepanok.undp.designsystem.components.BeaconChip
import com.stepanok.undp.designsystem.components.BeaconProgressBar
import com.stepanok.undp.designsystem.safeTopPadding
import com.stepanok.undp.designsystem.components.ChipSize
import com.stepanok.undp.designsystem.components.DamageChip
import com.stepanok.undp.core.media.ReportPhoto
import com.stepanok.undp.designsystem.icons.BeaconIcons
import com.stepanok.undp.designsystem.labels.damageLabel
import com.stepanok.undp.designsystem.theme.BeaconTheme
import com.stepanok.undp.domain.model.DamageLevel
import com.stepanok.undp.domain.model.SyncState
import com.stepanok.undp.feature.reportdetail.ReportDetailScreen
import org.jetbrains.compose.resources.stringResource
import undp.shared.generated.resources.Res
import undp.shared.generated.resources.filter_all
import undp.shared.generated.resources.reports_empty
import undp.shared.generated.resources.reports_summary
import undp.shared.generated.resources.reports_title
import undp.shared.generated.resources.status_queued
import undp.shared.generated.resources.status_synced
import undp.shared.generated.resources.sync_offline
import undp.shared.generated.resources.sync_online

object ReportsScreen : Screen {
    @Composable
    override fun Content() {
        val model = koinScreenModel<ReportsScreenModel>()
        val state by model.state.collectAsState()
        val colors = BeaconTheme.colors
        val nav = LocalNavigator.currentOrThrow

        Column(Modifier.fillMaxSize().background(colors.bg)) {
            Column(Modifier.safeTopPadding().padding(horizontal = 20.dp).padding(top = 14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(Res.string.reports_title), style = BeaconTheme.typography.titleL, color = colors.ink)
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(colors.surface).clickable { },
                        contentAlignment = Alignment.Center,
                    ) { Icon(BeaconIcons.Download, contentDescription = "Export", tint = colors.ink2, modifier = Modifier.size(18.dp)) }
                }
                Text(
                    stringResource(Res.string.reports_summary, state.total, state.syncedCount),
                    style = BeaconTheme.typography.caption, color = colors.ink3,
                    modifier = Modifier.padding(top = 4.dp),
                )
                SyncHeader(
                    online = state.online,
                    queued = state.queuedCount,
                    isSyncing = state.isSyncing,
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(stringResource(Res.string.filter_all), state.total, state.filter == null) { model.onIntent(ReportsIntent.SetFilter(null)) }
                    DamageLevel.entries.forEach { level ->
                        FilterChip(damageLabel(level), state.damageCounts[level] ?: 0, state.filter == level) {
                            model.onIntent(ReportsIntent.SetFilter(level))
                        }
                    }
                }
            }

            if (state.rows.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(Res.string.reports_empty), style = BeaconTheme.typography.bodyS, color = colors.ink3)
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.rows, key = { it.id }) { row ->
                        ReportRow(row, onClick = { nav.push(ReportDetailScreen(row.id)) })
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, count: Int, active: Boolean, onClick: () -> Unit) {
    val colors = BeaconTheme.colors
    Row(
        Modifier
            .clip(CircleShape)
            .background(if (active) colors.primary else colors.surface)
            .then(if (active) Modifier else Modifier.border(1.dp, colors.line, CircleShape))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text("$label · $count", style = BeaconTheme.typography.label, color = if (active) colors.onPrimary else colors.ink2)
    }
}

@Composable
private fun ReportRow(row: ReportRowUi, onClick: () -> Unit) {
    val colors = BeaconTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surface).border(1.dp, colors.line, RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ReportPhoto(photo = row.photo, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("#${row.id}", style = BeaconTheme.typography.titleS, color = colors.ink)
                if (row.synced) {
                    BeaconChip(stringResource(Res.string.status_synced), leadingIcon = BeaconIcons.Check, container = colors.okSoft, contentColor = colors.ok, size = ChipSize.Sm)
                } else {
                    BeaconChip(stringResource(Res.string.status_queued), leadingIcon = BeaconIcons.CloudOff, container = colors.surface3, contentColor = colors.ink2, size = ChipSize.Sm)
                }
            }
            Text("${row.time} · ${row.place}", style = BeaconTheme.typography.caption, color = colors.ink3, modifier = Modifier.padding(top = 2.dp))
            Spacer(Modifier.height(6.dp))
            DamageChip(level = row.damage, label = damageLabel(row.damage), size = ChipSize.Sm)
            (row.sync as? SyncState.Syncing)?.let { s ->
                Spacer(Modifier.height(6.dp))
                BeaconProgressBar(progress = s.fraction, color = colors.damageColor(row.damage), height = 3.dp)
            }
        }
        Icon(BeaconIcons.ChevronRight, contentDescription = null, tint = colors.ink3, modifier = Modifier.size(16.dp))
    }
}

/** Passive live connectivity + outbox indicator. Queued reports auto-upload when online. */
@Composable
private fun SyncHeader(online: Boolean, queued: Int, isSyncing: Boolean) {
    val colors = BeaconTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(14.dp))
            .background(if (online) colors.primarySoft else colors.surface2)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val statusBase = stringResource(if (online) Res.string.sync_online else Res.string.sync_offline)
        val statusText = statusBase +
            (if (queued > 0) " · $queued queued" else "") +
            (if (isSyncing) " · syncing…" else "")
        Icon(
            if (online) BeaconIcons.CloudUp else BeaconIcons.CloudOff,
            contentDescription = null,
            tint = if (online) colors.primary else colors.ink2,
            modifier = Modifier.size(18.dp),
        )
        Text(statusText, style = BeaconTheme.typography.label, color = colors.ink)
    }
}
