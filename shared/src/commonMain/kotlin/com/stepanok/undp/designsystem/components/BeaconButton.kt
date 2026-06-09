package com.stepanok.undp.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stepanok.undp.designsystem.theme.BeaconTheme

enum class BeaconButtonVariant { Primary, Secondary, Ghost, Outline, Danger }
enum class BeaconButtonSize { Sm, Md, Lg }

@Composable
fun BeaconButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: BeaconButtonVariant = BeaconButtonVariant.Primary,
    size: BeaconButtonSize = BeaconButtonSize.Md,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    fullWidth: Boolean = false,
    loading: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = BeaconTheme.colors
    val height = when (size) {
        BeaconButtonSize.Sm -> 36.dp
        BeaconButtonSize.Md -> 48.dp
        BeaconButtonSize.Lg -> 56.dp
    }
    val radius = when (size) {
        BeaconButtonSize.Sm -> 12.dp
        BeaconButtonSize.Md -> 16.dp
        BeaconButtonSize.Lg -> 20.dp
    }
    val fontSize = when (size) {
        BeaconButtonSize.Sm -> 13.sp
        BeaconButtonSize.Md -> 15.sp
        BeaconButtonSize.Lg -> 16.sp
    }
    val iconSize = (fontSize.value + 3f).dp

    val bg: Color
    val fg: Color
    var border: Color? = null
    when (variant) {
        BeaconButtonVariant.Primary -> { bg = colors.primary; fg = colors.onPrimary }
        BeaconButtonVariant.Secondary -> { bg = colors.primarySoft; fg = colors.primaryInk }
        BeaconButtonVariant.Ghost -> { bg = Color.Transparent; fg = colors.ink2 }
        BeaconButtonVariant.Outline -> { bg = Color.Transparent; fg = colors.ink; border = colors.line }
        BeaconButtonVariant.Danger -> { bg = colors.complete; fg = Color.White }
    }

    val shape = RoundedCornerShape(radius)
    val interaction = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .height(height)
            .clip(shape)
            .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier)
            .background(bg)
            .clickable(
                enabled = enabled && !loading,
                interactionSource = interaction,
                indication = ripple(),
                onClick = onClick,
            )
            .padding(horizontal = if (size == BeaconButtonSize.Sm) 14.dp else 18.dp)
            .alpha(if (enabled) 1f else 0.5f),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = fg, strokeWidth = 2.dp)
        } else {
            if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, tint = fg, modifier = Modifier.size(iconSize))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, color = fg, style = BeaconTheme.typography.label.copy(fontSize = fontSize, fontWeight = FontWeight.SemiBold))
            if (trailingIcon != null) {
                Spacer(Modifier.width(8.dp))
                Icon(trailingIcon, contentDescription = null, tint = fg, modifier = Modifier.size(iconSize))
            }
        }
    }
}
