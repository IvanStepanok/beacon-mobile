package com.stepanok.undp.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stepanok.undp.designsystem.theme.BeaconTheme

/**
 * Centered spinner (+ optional label) for any screen/section waiting on an async load — so a fetch
 * in flight never reads as a frozen blank screen. Fills its parent and centers; pass a [label] for
 * context ("Loading…", "Analyzing…").
 */
@Composable
fun BeaconLoading(modifier: Modifier = Modifier, label: String? = null) {
    val colors = BeaconTheme.colors
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(color = colors.primary, modifier = Modifier.size(34.dp))
            if (label != null) {
                Text(label, style = BeaconTheme.typography.caption, color = colors.ink2)
            }
        }
    }
}
