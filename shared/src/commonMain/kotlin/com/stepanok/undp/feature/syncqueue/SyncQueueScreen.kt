package com.stepanok.undp.feature.syncqueue

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import com.stepanok.undp.designsystem.components.BeaconProgressBar
import com.stepanok.undp.designsystem.components.PhotoPlaceholder
import com.stepanok.undp.designsystem.safeTopPadding
import com.stepanok.undp.designsystem.icons.BeaconIcons
import com.stepanok.undp.designsystem.labels.damageLabel
import com.stepanok.undp.designsystem.theme.BeaconTheme
import com.stepanok.undp.domain.model.SyncState
import org.jetbrains.compose.resources.stringResource
import undp.shared.generated.resources.Res
import undp.shared.generated.resources.sync_all_caught_up
import undp.shared.generated.resources.sync_awaiting
import undp.shared.generated.resources.sync_in_progress
import undp.shared.generated.resources.sync_now
import undp.shared.generated.resources.sync_offline
import undp.shared.generated.resources.sync_offline_msg
import undp.shared.generated.resources.sync_online
import undp.shared.generated.resources.sync_online_msg
import undp.shared.generated.resources.sync_title
import undp.shared.generated.resources.sync_uploading
import undp.shared.generated.resources.sync_waiting
import kotlin.math.roundToInt

object SyncQueueScreen : Screen {
    @Composable
    override fun Content() {
        val model = koinScreenModel<SyncQueueScreenModel>()
        val state by model.state.collectAsState()
        val colors = BeaconTheme.colors

        Column(Modifier.fillMaxSize().background(colors.bg)) {
            Column(Modifier.safeTopPadding().padding(horizontal = 20.dp).padding(top = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(if (state.online) BeaconIcons.CloudUp else BeaconIcons.CloudOff, contentDescription = null, tint = colors.primary, modifier = Modifier.size(26.dp))
                    Text(stringResource(Res.string.sync_title), style = BeaconTheme.typography.titleL, color = colors.ink)
                }
                Text(
                    stringResource(if (state.online) Res.string.sync_online_msg else Res.string.sync_offline_msg),
                    style = BeaconTheme.typography.bodyS, color = colors.ink2, modifier = Modifier.padding(top = 6.dp),
                )

                // Status card
                Row(
                    Modifier.fillMaxWidth().padding(top = 14.dp).clip(RoundedCornerShape(22.dp)).background(colors.primarySoft).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("${state.items.size}", style = BeaconTheme.typography.numXL, color = colors.primaryInk)
                        Text(stringResource(Res.string.sync_awaiting), style = BeaconTheme.typography.bodyS, color = colors.primaryInk)
                        Text(
                            "~${(state.totalMb * 10).roundToInt() / 10.0} MB",
                            style = BeaconTheme.typography.caption, color = colors.ink3, modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Box(Modifier.size(72.dp).clip(CircleShape).background(colors.surface.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
                        Icon(if (state.online) BeaconIcons.CloudUp else BeaconIcons.CloudOff, contentDescription = null, tint = colors.primary, modifier = Modifier.size(34.dp))
                    }
                }

                // Live connectivity + manual flush (reports also auto-upload when online)
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(14.dp)).background(colors.surface).border(1.dp, colors.line, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(if (state.online) BeaconIcons.CloudUp else BeaconIcons.CloudOff, contentDescription = null, tint = if (state.online) colors.ok else colors.ink2, modifier = Modifier.size(18.dp))
                        Text(stringResource(if (state.online) Res.string.sync_online else Res.string.sync_offline), style = BeaconTheme.typography.label, color = colors.ink)
                    }
                    if (state.online && state.items.isNotEmpty()) {
                        Box(
                            Modifier.clip(CircleShape)
                                .background(if (state.isSyncing) colors.surface3 else colors.primary)
                                .clickable(enabled = !state.isSyncing) { model.onIntent(SyncIntent.SyncNow) }
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                        ) {
                            Text(
                                stringResource(if (state.isSyncing) Res.string.sync_in_progress else Res.string.sync_now),
                                style = BeaconTheme.typography.label,
                                color = if (state.isSyncing) colors.ink2 else colors.onPrimary,
                            )
                        }
                    }
                }

                Text(
                    stringResource(if (state.online) Res.string.sync_uploading else Res.string.sync_waiting).uppercase(),
                    style = BeaconTheme.typography.micro, color = colors.ink3, modifier = Modifier.padding(top = 16.dp),
                )
            }

            if (state.items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(Res.string.sync_all_caught_up), style = BeaconTheme.typography.bodyS, color = colors.ink3)
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.items, key = { it.id }) { item -> QueueRow(item) }
                }
            }
        }
    }
}

@Composable
private fun QueueRow(item: QueueItemUi) {
    val colors = BeaconTheme.colors
    val syncing = item.sync as? SyncState.Syncing
    val progress = syncing?.fraction ?: 0f
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surface).border(1.dp, colors.line, RoundedCornerShape(18.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PhotoPlaceholder(modifier = Modifier.size(50.dp).clip(RoundedCornerShape(12.dp)))
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("#${item.id}", style = BeaconTheme.typography.titleS, color = colors.ink)
                Text(
                    if (syncing != null) "${(progress * 100).roundToInt()}%" else "${(item.sizeMb * 10).roundToInt() / 10.0} MB",
                    style = BeaconTheme.typography.caption,
                    color = if (syncing != null) colors.primary else colors.ink3,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text("${damageLabel(item.damage)} · ${item.time}", style = BeaconTheme.typography.caption, color = colors.ink3, modifier = Modifier.padding(top = 1.dp))
            Spacer(Modifier.height(6.dp))
            BeaconProgressBar(progress = progress, color = colors.damageColor(item.damage), height = 3.dp)
        }
    }
}
