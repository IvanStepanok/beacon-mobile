package com.stepanok.undp.core.offline

import com.stepanok.undp.domain.model.DownloadBundle
import com.stepanok.undp.domain.model.DownloadState
import com.stepanok.undp.domain.model.DownloadType
import com.stepanok.undp.domain.repository.DownloadQueue

/**
 * Definitions for the offline materials Beacon can pre-download. The crisis pack is a real
 * MapLibre offline region (style + vector tiles for the affected bbox); building footprints
 * ship bundled with the app, so they are available offline from first launch.
 */
object OfflineBundles {
    /** Same basemap the live map uses, so the cached region matches what's on screen. */
    const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

    /** A tile-pyramid region to materialise for offline use. */
    data class Region(
        val north: Double,
        val east: Double,
        val south: Double,
        val west: Double,
        val minZoom: Double,
        val maxZoom: Double,
    )

    /** Central Antakya (the active crisis area). */
    val ANTAKYA = Region(north = 36.2600, east = 36.2200, south = 36.1500, west = 36.1100, minZoom = 11.0, maxZoom = 15.0)

    const val CRISIS_PACK_ID = "area-crisis"

    /** Offline map pack for a bbox around [lat]/[lng] (the user's location or the
     *  crisis they're in), labelled with [areaLabel]. Replaces the hardcoded city. */
    fun areaPack(lat: Double, lng: Double, areaLabel: String, halfDeg: Double = 0.07) = DownloadBundle(
        id = CRISIS_PACK_ID,
        title = "Offline map · $areaLabel",
        type = DownloadType.CRISIS_BUNDLE,
        bytesTotal = 18_000_000,
        state = DownloadState.Queued,
        region = com.stepanok.undp.domain.model.GeoBox(
            north = lat + halfDeg, east = lng + halfDeg,
            south = lat - halfDeg, west = lng - halfDeg,
            minZoom = 11.0, maxZoom = 15.0,
        ),
    )

    fun footprintsBundle() = DownloadBundle(
        id = "antakya-footprints",
        title = "Building footprints",
        type = DownloadType.FOOTPRINTS,
        bytesTotal = 4_200_000,
        state = DownloadState.Done, // ships bundled in the app
    )

    fun region(id: String): Region? = if (id == CRISIS_PACK_ID) ANTAKYA else null

    /** Initial list: footprints are already available offline; the crisis map is downloadable. */
    fun seed(): List<DownloadBundle> = listOf(footprintsBundle())
}

/** Platform-backed offline download queue (Android OfflineManager / iOS MLNOfflineStorage). */
expect fun createDownloadQueue(): DownloadQueue
