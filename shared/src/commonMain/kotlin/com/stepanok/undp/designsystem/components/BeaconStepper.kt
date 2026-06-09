package com.stepanok.undp.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.stepanok.undp.designsystem.theme.BeaconTheme

/** Equal-weight progress segments (filled up to and including [current]). Mirrors automatically in RTL. */
@Composable
fun BeaconStepper(current: Int, total: Int, modifier: Modifier = Modifier) {
    val colors = BeaconTheme.colors
    Row(
        modifier = modifier.fillMaxWidth().height(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(total) { i ->
            val filled = i <= current
            val color by animateColorAsState(if (filled) colors.primary else colors.surface3, label = "seg")
            Segment(color)
        }
    }
}

@Composable
private fun RowScope.Segment(color: androidx.compose.ui.graphics.Color) {
    androidx.compose.foundation.layout.Box(
        Modifier
            .weight(1f)
            .height(4.dp)
            .clip(CircleShape)
            .background(color),
    )
}
