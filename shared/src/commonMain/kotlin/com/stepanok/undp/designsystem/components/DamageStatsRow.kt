package com.stepanok.undp.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stepanok.undp.designsystem.labels.damageLabel
import com.stepanok.undp.designsystem.theme.BeaconTheme
import com.stepanok.undp.domain.model.DamageTier

/** The home-map stat pill row: a colored-dot count per 3-tier damage classification. */
@Composable
fun DamageStatsRow(
    counts: Map<DamageTier, Int>,
    modifier: Modifier = Modifier,
) {
    val colors = BeaconTheme.colors
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.line, shape)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        DamageTier.entries.forEach { tier ->
            StatCell(damageLabel(tier), counts[tier] ?: 0, colors.damageColor(tier))
        }
    }
}

@Composable
private fun RowScope.StatCell(label: String, value: Int, dot: Color) {
    val colors = BeaconTheme.colors
    // Number (+dot) on top so all three align regardless of label length; the label
    // sits below in a compact single line so longer tier labels don't wrap.
    Column(
        modifier = Modifier.weight(1f).padding(vertical = 4.dp, horizontal = 1.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
            Text(value.toString(), style = BeaconTheme.typography.titleM, color = colors.ink)
        }
        Text(
            label,
            style = BeaconTheme.typography.caption.copy(fontSize = 10.sp, lineHeight = 12.sp),
            color = colors.ink3,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
