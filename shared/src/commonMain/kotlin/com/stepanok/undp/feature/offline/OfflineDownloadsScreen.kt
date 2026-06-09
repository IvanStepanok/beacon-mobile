package com.stepanok.undp.feature.offline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.stepanok.undp.designsystem.components.BeaconButton
import com.stepanok.undp.designsystem.components.BeaconButtonSize
import com.stepanok.undp.designsystem.components.BeaconProgressBar
import com.stepanok.undp.designsystem.components.ScreenHeader
import com.stepanok.undp.designsystem.icons.BeaconIcons
import com.stepanok.undp.designsystem.theme.BeaconTheme
import com.stepanok.undp.domain.model.DownloadBundle
import com.stepanok.undp.domain.model.DownloadState
import com.stepanok.undp.domain.model.DownloadType
import org.jetbrains.compose.resources.stringResource
import undp.shared.generated.resources.Res
import undp.shared.generated.resources.offline_download_area
import undp.shared.generated.resources.offline_downloading
import undp.shared.generated.resources.offline_queued
import undp.shared.generated.resources.offline_ready
import undp.shared.generated.resources.offline_sub
import undp.shared.generated.resources.offline_title
import kotlin.math.roundToInt

object OfflineDownloadsScreen : Screen {
    @Composable
    override fun Content() {
        val model = koinScreenModel<OfflineDownloadsScreenModel>()
        val state by model.state.collectAsState()
        val colors = BeaconTheme.colors
        val nav = LocalNavigator.currentOrThrow

        Column(Modifier.fillMaxSize().background(colors.bg)) {
            ScreenHeader(title = stringResource(Res.string.offline_title), onBack = { nav.pop() })
            Text(
                stringResource(Res.string.offline_sub),
                style = BeaconTheme.typography.bodyS, color = colors.ink2,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(14.dp))
            BeaconButton(
                text = stringResource(Res.string.offline_download_area),
                onClick = { model.onIntent(OfflineIntent.DownloadCrisisPack) },
                leadingIcon = BeaconIcons.Download,
                fullWidth = true,
                size = BeaconButtonSize.Lg,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.bundles, key = { it.id }) { bundle -> BundleCard(bundle) }
            }
        }
    }
}

@Composable
private fun BundleCard(bundle: DownloadBundle) {
    val colors = BeaconTheme.colors
    val icon: ImageVector = when (bundle.type) {
        DownloadType.MAP_TILES -> BeaconIcons.Map
        DownloadType.FOOTPRINTS -> BeaconIcons.Building
        DownloadType.CRISIS_BUNDLE -> BeaconIcons.Warning
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
    }
}

@Composable
private fun StatusLine(text: String, color: androidx.compose.ui.graphics.Color, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
        Text(text, style = BeaconTheme.typography.caption, color = color, fontWeight = FontWeight.Bold)
    }
}
