package com.stepanok.undp.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stepanok.undp.designsystem.theme.BeaconTheme
import com.stepanok.undp.domain.model.DamageLevel

enum class ChipSize { Sm, Md }

@Composable
fun BeaconChip(
    text: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    container: Color = BeaconTheme.colors.primarySoft,
    contentColor: Color = BeaconTheme.colors.primaryInk,
    dotColor: Color? = null,
    size: ChipSize = ChipSize.Md,
) {
    val fontSize = if (size == ChipSize.Sm) 11.sp else 12.sp
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(container)
            .padding(horizontal = if (size == ChipSize.Sm) 8.dp else 12.dp, vertical = if (size == ChipSize.Sm) 4.dp else 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dotColor != null) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor))
        }
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, tint = contentColor, modifier = Modifier.size((fontSize.value + 2f).dp))
        }
        Text(text, color = contentColor, style = BeaconTheme.typography.caption.copy(fontSize = fontSize, fontWeight = FontWeight.SemiBold))
    }
}

/** Convenience chip for the 3-tier damage level using the theme's trauma-informed colors. */
@Composable
fun DamageChip(level: DamageLevel, label: String, modifier: Modifier = Modifier, size: ChipSize = ChipSize.Md) {
    val colors = BeaconTheme.colors
    BeaconChip(
        text = label,
        modifier = modifier,
        container = colors.damageSoft(level),
        contentColor = colors.damageColor(level),
        dotColor = colors.damageColor(level),
        size = size,
    )
}
