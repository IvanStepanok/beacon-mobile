package com.stepanok.undp.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.stepanok.undp.designsystem.icons.BeaconIcons
import com.stepanok.undp.designsystem.safeTopPadding
import com.stepanok.undp.designsystem.theme.BeaconTheme

@Composable
fun ScreenHeader(
    modifier: Modifier = Modifier,
    title: String? = null,
    onBack: (() -> Unit)? = null,
    dark: Boolean = false,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = BeaconTheme.colors
    Row(
        modifier = modifier.fillMaxWidth().safeTopPadding().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (onBack != null) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (dark) Color(0x1AFFFFFF) else colors.surface2)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(BeaconIcons.ArrowLeft, contentDescription = "Back", tint = if (dark) Color.White else colors.ink, modifier = Modifier.size(20.dp))
            }
        } else {
            Box(Modifier.size(40.dp))
        }
        if (title != null) {
            Text(title, style = BeaconTheme.typography.titleS, color = if (dark) Color.White else colors.ink)
        }
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            action?.invoke()
        }
    }
}
