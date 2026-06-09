package com.stepanok.undp.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.stepanok.undp.designsystem.theme.BeaconTheme

@Composable
fun BeaconProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = BeaconTheme.colors.primary,
    track: Color = BeaconTheme.colors.surface3,
    height: Dp = 4.dp,
    animated: Boolean = true,
) {
    val target = progress.coerceIn(0f, 1f)
    val width by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = if (animated) 400 else 0),
        label = "progress",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(track),
    ) {
        Box(
            Modifier
                .fillMaxWidth(width)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(color),
        )
    }
}
