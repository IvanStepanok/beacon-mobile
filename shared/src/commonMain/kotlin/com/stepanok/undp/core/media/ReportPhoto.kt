package com.stepanok.undp.core.media

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.stepanok.undp.designsystem.components.PhotoPlaceholder
import com.stepanok.undp.domain.model.PhotoRef

/**
 * Renders a report's photo the same way the capture Review step does — by decoding the
 * local file via [CapturedImage] — falling back to the striped [PhotoPlaceholder] when there
 * is no real photo on disk (e.g. seed/mock "mock://" paths or remote-only refs we can't decode).
 *
 * Prefers [PhotoRef.localPath]; falls back to [PhotoRef.remoteUrl] when it is a local-style path.
 */
@Composable
fun ReportPhoto(
    photo: PhotoRef?,
    modifier: Modifier = Modifier,
    placeholderLabel: String? = null,
    placeholderDark: Boolean = false,
) {
    val path = photo?.bestLocalPath()
    if (path != null) {
        CapturedImage(path, modifier)
    } else {
        PhotoPlaceholder(modifier = modifier, label = placeholderLabel, dark = placeholderDark)
    }
}

/** The path [CapturedImage] can actually decode, or null for synthetic/remote-only refs. */
private fun PhotoRef.bestLocalPath(): String? {
    localPath.takeIf { it.isLoadableFilePath() }?.let { return it }
    return remoteUrl?.takeIf { it.isLoadableFilePath() }
}

/** True for a real on-device file path; false for blank or synthetic schemes like "mock://". */
private fun String.isLoadableFilePath(): Boolean =
    isNotBlank() && !startsWith("mock://")
