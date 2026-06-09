package com.stepanok.undp.core.media

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Renders a locally-stored captured photo (by file path), cropped to fill [modifier]. */
@Composable
expect fun CapturedImage(path: String, modifier: Modifier)
