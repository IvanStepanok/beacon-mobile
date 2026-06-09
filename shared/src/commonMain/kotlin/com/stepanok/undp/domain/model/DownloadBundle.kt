package com.stepanok.undp.domain.model

enum class DownloadType { MAP_TILES, FOOTPRINTS, CRISIS_BUNDLE, LANGUAGE_PACK }

sealed interface DownloadState {
    data object Queued : DownloadState
    data class Downloading(val bytesDone: Long, val bytesTotal: Long) : DownloadState {
        val fraction: Float get() = if (bytesTotal > 0) bytesDone.toFloat() / bytesTotal else 0f
    }
    data object Done : DownloadState
    data class Failed(val reason: String) : DownloadState
}

/** A tile-pyramid region (WGS84 bbox + zoom range) to materialise for offline use. */
data class GeoBox(
    val north: Double,
    val east: Double,
    val south: Double,
    val west: Double,
    val minZoom: Double = 11.0,
    val maxZoom: Double = 15.0,
)

/** A queued offline material (map region, footprints, crisis bundle, language pack).
 *  [region] is the actual bbox to download — computed around the user's location /
 *  active crisis, so the offline pack is always relevant (not a hardcoded city). */
data class DownloadBundle(
    val id: String,
    val title: String,
    val type: DownloadType,
    val bytesTotal: Long,
    val state: DownloadState,
    val region: GeoBox? = null,
)
