package com.stepanok.undp.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

@Immutable
data class BeaconShapes(
    val sm: Shape = RoundedCornerShape(12.dp),
    val card: Shape = RoundedCornerShape(18.dp),
    val md: Shape = RoundedCornerShape(20.dp),
    val lg: Shape = RoundedCornerShape(28.dp),
    val xl: Shape = RoundedCornerShape(36.dp),
    val pill: Shape = RoundedCornerShape(percent = 50),
    val sheet: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
)
