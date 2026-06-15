package com.stepanok.undp.feature.offline

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.stepanok.undp.designsystem.components.BeaconLoading
import com.stepanok.undp.designsystem.components.BeaconProgressBar
import com.stepanok.undp.designsystem.components.ScreenHeader
import com.stepanok.undp.designsystem.icons.BeaconIcons
import com.stepanok.undp.designsystem.theme.BeaconTheme
import com.stepanok.undp.domain.model.DownloadBundle
import com.stepanok.undp.domain.model.DownloadState
import com.stepanok.undp.domain.model.DownloadType
import org.jetbrains.compose.resources.stringResource
import undp.shared.generated.resources.Res
import undp.shared.generated.resources.common_loading
import undp.shared.generated.resources.offline_delete_body
import undp.shared.generated.resources.offline_delete_confirm
import undp.shared.generated.resources.offline_delete_title
import undp.shared.generated.resources.offline_downloading
import undp.shared.generated.resources.offline_empty
import undp.shared.generated.resources.offline_queued
import undp.shared.generated.resources.offline_ready
import undp.shared.generated.resources.offline_sub
import undp.shared.generated.resources.offline_title
import undp.shared.generated.resources.withdraw_cancel
import kotlin.math.roundToInt

/** [autoStart] = immediately kick off the "download my area" pack — set when arriving from the
 *  map's one-time offline prompt, so the user doesn't have to tap Download a second time. */
data class OfflineDownloadsScreen(val autoStart: Boolean = false) : Screen {
    @Composable
    override fun Content() {
        val model = koinScreenModel<OfflineDownloadsScreenModel>()
        val state by model.state.collectAsState()
        val colors = BeaconTheme.colors
        val nav = LocalNavigator.currentOrThrow

        // The pending-deletion target (non-null → the confirm dialog is shown).
        var pendingDelete by remember { mutableStateOf<DownloadBundle?>(null) }

        // Auto-trigger the area download when the user accepted the map's offline prompt.
        LaunchedEffect(Unit) { if (autoStart) model.onIntent(OfflineIntent.DownloadCrisisPack) }

        Column(Modifier.fillMaxSize().background(colors.bg)) {
            ScreenHeader(title = stringResource(Res.string.offline_title), onBack = { nav.pop() })
            Text(
                stringResource(Res.string.offline_sub),
                style = BeaconTheme.typography.bodyS, color = colors.ink2,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(8.dp))
            when {
                state.bundles.isEmpty() && state.resolvingPack ->
                    BeaconLoading(label = stringResource(Res.string.common_loading))
                state.bundles.isEmpty() ->
                    Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(BeaconIcons.Map, contentDescription = null, tint = colors.ink3, modifier = Modifier.size(40.dp))
                            Spacer(Modifier.height(12.dp))
                            Text(
                                stringResource(Res.string.offline_empty),
                                style = BeaconTheme.typography.bodyS, color = colors.ink3, textAlign = TextAlign.Center,
                            )
                        }
                    }
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.bundles, key = { it.id }) { bundle ->
                        BundleCard(bundle, onDelete = { pendingDelete = bundle })
                    }
                }
            }
        }

        // Delete confirmation — "are you sure?" before freeing the pack.
        pendingDelete?.let { target ->
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                containerColor = colors.surface,
                title = { Text(stringResource(Res.string.offline_delete_title), style = BeaconTheme.typography.titleM, color = colors.ink) },
                text = { Text(stringResource(Res.string.offline_delete_body), style = BeaconTheme.typography.bodyS, color = colors.ink2) },
                confirmButton = {
                    TextButton(onClick = { model.onIntent(OfflineIntent.Delete(target.id)); pendingDelete = null }) {
                        Text(stringResource(Res.string.offline_delete_confirm), color = colors.complete, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) {
                        Text(stringResource(Res.string.withdraw_cancel), color = colors.ink2)
                    }
                },
            )
        }
    }
}

@Composable
private fun BundleCard(bundle: DownloadBundle, onDelete: () -> Unit) {
    val colors = BeaconTheme.colors
    val icon: ImageVector = when (bundle.type) {
        DownloadType.MAP_TILES -> BeaconIcons.Map
        DownloadType.FOOTPRINTS -> BeaconIcons.Building
        DownloadType.CRISIS_BUNDLE -> BeaconIcons.Map
        DownloadType.LANGUAGE_PACK -> BeaconIcons.Language
    }
    val downloading = bundle.state as? DownloadState.Downloading
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surface).border(1.dp, colors.line, RoundedCornerShape(18.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(colors.primarySoft), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(bundle.title, style = BeaconTheme.typography.titleS, color = colors.ink)
                Text("${(bundle.bytesTotal / 1_000_000.0).roundToInt()} MB", style = BeaconTheme.typography.caption, color = colors.ink3)
            }
            Spacer(Modifier.height(4.dp))
            when (val s = bundle.state) {
                is DownloadState.Done -> StatusLine(stringResource(Res.string.offline_ready), colors.ok, BeaconIcons.Check)
                is DownloadState.Queued -> StatusLine(stringResource(Res.string.offline_queued), colors.ink3, BeaconIcons.CloudOff)
                is DownloadState.Downloading -> {
                    Text("${stringResource(Res.string.offline_downloading)} · ${(s.fraction * 100).roundToInt()}%", style = BeaconTheme.typography.caption, color = colors.primary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    BeaconProgressBar(progress = s.fraction, height = 3.dp)
                }
                is DownloadState.Failed -> StatusLine(s.reason, colors.complete, BeaconIcons.Warning)
            }
            if (downloading == null && bundle.state is DownloadState.Done) {
                Spacer(Modifier.height(4.dp))
                BeaconProgressBar(progress = 1f, color = colors.ok, height = 3.dp, animated = false)
            }
        }
        // Delete this pack (frees its tiles). Confirmation handled by the caller.
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(colors.surface2).clickable(onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) {
            Icon(BeaconIcons.Close, contentDescription = "Delete offline map", tint = colors.ink3, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun StatusLine(text: String, color: androidx.compose.ui.graphics.Color, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
        Text(text, style = BeaconTheme.typography.caption, color = color, fontWeight = FontWeight.Bold)
    }
}
