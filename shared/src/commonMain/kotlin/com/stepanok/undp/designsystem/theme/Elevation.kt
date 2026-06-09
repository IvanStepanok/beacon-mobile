package com.stepanok.undp.designsystem.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** Soft, violet-tinted card shadow approximating the prototype's `--shadow-card`. */
fun Modifier.beaconCardShadow(shape: Shape): Modifier =
    shadow(elevation = 8.dp, shape = shape, clip = false, ambientColor = Color(0x10141224), spotColor = Color(0x2E28185A))

/** Pronounced violet shadow for FABs / sheets / success bursts (`--shadow-pop`). */
fun Modifier.beaconPopShadow(shape: Shape): Modifier =
    shadow(elevation = 20.dp, shape = shape, clip = false, ambientColor = Color(0x18141224), spotColor = Color(0x593C1E82))
