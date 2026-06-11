package com.stepanok.undp.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stepanok.undp.designsystem.icons.BeaconIcons
import com.stepanok.undp.designsystem.safeBottomPadding
import com.stepanok.undp.designsystem.theme.BeaconTheme
import com.stepanok.undp.designsystem.theme.beaconPopShadow

enum class BeaconTab(val icon: ImageVector) {
    Map(BeaconIcons.Map),
    Reports(BeaconIcons.Reports),
    Profile(BeaconIcons.Profile),
}

@Composable
fun BeaconBottomBar(
    active: BeaconTab,
    label: (BeaconTab) -> String,
    onSelect: (BeaconTab) -> Unit,
    onCapture: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BeaconTheme.colors
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .safeBottomPadding()
                .padding(top = 6.dp, bottom = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TabCell(BeaconTab.Map, active, label, onSelect)
            TabCell(BeaconTab.Reports, active, label, onSelect)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                CaptureFab(onCapture)
            }
            TabCell(BeaconTab.Profile, active, label, onSelect)
        }
    }
}

@Composable
private fun RowScope.TabCell(
    tab: BeaconTab,
    active: BeaconTab,
    label: (BeaconTab) -> String,
    onSelect: (BeaconTab) -> Unit,
) {
    val colors = BeaconTheme.colors
    val isActive = tab == active
    val tint = if (isActive) colors.primary else colors.ink3
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false),
            ) { onSelect(tab) }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier
                .clip(CircleShape)
                .background(if (isActive) colors.primarySoft else androidx.compose.ui.graphics.Color.Transparent)
                .padding(horizontal = 14.dp, vertical = 4.dp),
        ) {
            Icon(tab.icon, contentDescription = label(tab), tint = tint, modifier = Modifier.size(20.dp))
        }
        Text(
            label(tab),
            style = BeaconTheme.typography.caption.copy(fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium),
            color = tint,
        )
    }
}

@Composable
private fun CaptureFab(onClick: () -> Unit) {
    val colors = BeaconTheme.colors
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier
            .offset(y = (-16).dp)
            .size(56.dp)
            .beaconPopShadow(shape)
            .clip(shape)
            .background(colors.primary)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(BeaconIcons.Plus, contentDescription = "New report", tint = colors.onPrimary, modifier = Modifier.size(26.dp))
    }
}
