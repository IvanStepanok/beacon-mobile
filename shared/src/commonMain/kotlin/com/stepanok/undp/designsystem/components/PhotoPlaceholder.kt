package com.stepanok.undp.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stepanok.undp.designsystem.theme.BeaconTheme

/** Diagonal-striped stand-in for a damage photo until real capture is wired. */
@Composable
fun PhotoPlaceholder(
    modifier: Modifier = Modifier,
    label: String? = null,
    dark: Boolean = false,
) {
    val base = if (dark) Color(0xFF1A1A22) else Color(0xFFD6CDE6)
    val alt = if (dark) Color(0xFF20202A) else Color(0xFFCDC4DD)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().background(base)) {
            val stripe = 24f
            val step = stripe * 2
            var x = -size.height
            while (x < size.width + size.height) {
                drawLine(
                    color = alt,
                    start = androidx.compose.ui.geometry.Offset(x, 0f),
                    end = androidx.compose.ui.geometry.Offset(x + size.height, size.height),
                    strokeWidth = stripe,
                )
                x += step
            }
        }
        if (label != null) {
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(if (dark) Color(0x66000000) else Color(0xB3FFFFFF))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    label,
                    style = BeaconTheme.typography.mono.copy(fontSize = 11.sp),
                    color = if (dark) Color(0x80FFFFFF) else Color(0x80281446),
                )
            }
        }
    }
}
