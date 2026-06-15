package com.stepanok.undp.core.offline

import com.stepanok.undp.domain.model.DownloadBundle
import com.stepanok.undp.domain.model.DownloadState
import com.stepanok.undp.domain.model.DownloadType
import com.stepanok.undp.domain.repository.DownloadQueue

/**
 * Definitions for the offline materials Beacon can pre-download. The crisis pack is a real
 * MapLibre offline region (style + vector tiles for the affected bbox).
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

    /**
     * Zoom span every pack covers. minZoom is deliberately LOW (regional) even though the bbox is
     * small: for a ~15 km box, z8–z10 is only a handful of tiles (≈1 tile at z8), so the cost is
     * trivial — but it means the downloaded region renders the whole way from regional context down
     * to building level offline. Without this, a z11-only pack left everything wider than city zoom
     * (and the whole zoom-in journey from the world view) BLANK offline, which read as "the map I
     * downloaded isn't there". maxZoom 15 overzooms cleanly to building zoom (the OpenMapTiles
     * source caps at z14). [GeoBox.zoomFloor]/[zoomCeil] in the bundle let the map clamp to this span.
     */
    const val PACK_MIN_ZOOM = 8.0
    const val PACK_MAX_ZOOM = 15.0

    /** Central Antakya (the active crisis area). */
    val ANTAKYA = Region(north = 36.2600, east = 36.2200, south = 36.1500, west = 36.1100, minZoom = PACK_MIN_ZOOM, maxZoom = PACK_MAX_ZOOM)

    const val CRISIS_PACK_ID = "area-crisis"

    /** Stable id for a pack, derived from its CENTRE (rounded to ~100 m). The same formula is used
     *  when reconstructing packs from the offline DB (from the region's bounds) and when deleting,
     *  so a pack downloaded for one spot is distinct from another — the user can cache several
     *  regions as they move around, and the list/delete target each one individually. */
    fun areaId(lat: Double, lng: Double): String {
        fun r(v: Double) = (kotlin.math.round(v * 1000) / 1000.0).toString()
        return "area:${r(lat)},${r(lng)}"
    }

    /** Offline map pack for a bbox around [lat]/[lng] (the user's location or the
     *  crisis they're in), labelled with [areaLabel]. */
    fun areaPack(lat: Double, lng: Double, areaLabel: String, halfDeg: Double = 0.07) = DownloadBundle(
        id = areaId(lat, lng),
        title = "Offline map · $areaLabel",
        type = DownloadType.CRISIS_BUNDLE,
        bytesTotal = 18_000_000,
        state = DownloadState.Queued,
        region = com.stepanok.undp.domain.model.GeoBox(
            north = lat + halfDeg, east = lng + halfDeg,
            south = lat - halfDeg, west = lng - halfDeg,
            minZoom = PACK_MIN_ZOOM, maxZoom = PACK_MAX_ZOOM,
        ),
    )

    fun region(id: String): Region? = if (id == CRISIS_PACK_ID) ANTAKYA else null

    /** Initial list: empty — only packs the user actually downloads appear here. */
    fun seed(): List<DownloadBundle> = emptyList()
}

/** Platform-backed offline download queue (Android OfflineManager / iOS MLNOfflineStorage). */
expect fun createDownloadQueue(): DownloadQueue
