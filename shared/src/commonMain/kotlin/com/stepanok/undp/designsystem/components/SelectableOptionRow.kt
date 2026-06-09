package com.stepanok.undp.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.stepanok.undp.designsystem.icons.BeaconIcons
import com.stepanok.undp.designsystem.theme.BeaconTheme

enum class OptionTrailing { Radio, Checkbox, None }

/**
 * The repeated "icon tile + title + description + selection indicator" row used across the capture
 * steps (damage / debris / infra / crisis) and settings. [accent] colors the selected state.
 */
@Composable
fun SelectableOptionRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    accent: Color = BeaconTheme.colors.primary,
    softAccent: Color = BeaconTheme.colors.primarySoft,
    trailing: OptionTrailing = OptionTrailing.Radio,
) {
    val colors = BeaconTheme.colors
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) softAccent else colors.surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) accent else colors.line, shape)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (icon != null) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (selected) Color.White.copy(alpha = 0.7f) else colors.surface2),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = BeaconTheme.typography.titleS, color = colors.ink)
            if (description != null) {
                Text(description, style = BeaconTheme.typography.bodyS, color = colors.ink2)
            }
        }
        when (trailing) {
            OptionTrailing.Radio -> RadioDot(selected, accent, colors.line)
            OptionTrailing.Checkbox -> CheckBadge(selected, accent, colors.line)
            OptionTrailing.None -> {}
        }
    }
}

@Composable
private fun RadioDot(selected: Boolean, accent: Color, line: Color) {
    Box(
        Modifier.size(24.dp).clip(CircleShape).border(2.dp, if (selected) accent else line, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(12.dp).clip(CircleShape).background(accent))
    }
}

@Composable
private fun CheckBadge(selected: Boolean, accent: Color, line: Color) {
    Box(
        Modifier.size(24.dp).clip(CircleShape)
            .background(if (selected) accent else Color.Transparent)
            .border(if (selected) 0.dp else 2.dp, line, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(BeaconIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
    }
}
