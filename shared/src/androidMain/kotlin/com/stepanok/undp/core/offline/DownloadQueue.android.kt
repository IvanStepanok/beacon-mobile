package com.stepanok.undp.core.offline

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.stepanok.undp.core.android.AndroidAppContext
import com.stepanok.undp.domain.model.DownloadBundle
import com.stepanok.undp.domain.model.DownloadState
import com.stepanok.undp.domain.model.DownloadType
import com.stepanok.undp.domain.model.GeoBox
import com.stepanok.undp.domain.repository.DownloadQueue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition

/**
 * Real offline-region downloads via MapLibre's OfflineManager. Tiles are fetched into MapLibre's
 * own offline database, so the basemap renders without a network once a pack reports complete.
 */
class AndroidDownloadQueue(private val context: Context) : DownloadQueue {

    private val items = MutableStateFlow(OfflineBundles.seed())
    private val main = Handler(Looper.getMainLooper())

    private val offlineManager: OfflineManager by lazy {
        MapLibre.getInstance(context)
        OfflineManager.getInstance(context)
    }

    init {
        // Re-populate the list from regions already in MapLibre's offline DB, so a pack downloaded
        // in a PREVIOUS session still shows as "Ready" (the Offline screen) and still feeds the
        // map's offline-area fallback. Without this the in-memory list started empty on every launch,
        // so the app "forgot" downloads even though their tiles were intact on disk.
        main.post { loadExisting() }
    }

    override fun observe(): Flow<List<DownloadBundle>> = items.asStateFlow()

    override suspend fun enqueue(bundle: DownloadBundle) {
        put(bundle.copy(state = DownloadState.Queued))
        // OfflineManager + its callbacks must be used on the main thread.
        main.post { startRegionDownload(bundle) }
    }

    override suspend fun cancel(id: String) {
        // Delete the actual region from MapLibre's offline DB (frees the tiles), then drop the card.
        // Match by the same centre-derived id used everywhere, so we delete exactly the tapped pack.
        main.post {
            offlineManager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
                override fun onList(offlineRegions: Array<OfflineRegion>?) {
                    val target = offlineRegions?.firstOrNull { region ->
                        val b = (region.definition as? OfflineTilePyramidRegionDefinition)?.bounds
                        b != null && OfflineBundles.areaId(
                            (b.latitudeNorth + b.latitudeSouth) / 2.0,
                            (b.longitudeEast + b.longitudeWest) / 2.0,
                        ) == id
                    }
                    if (target != null) {
                        target.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                            override fun onDelete() = dropFromItems(id)
                            override fun onError(error: String) = dropFromItems(id)
                        })
                    } else {
                        dropFromItems(id)
                    }
                }

                override fun onError(error: String) = dropFromItems(id)
            })
        }
    }

    private fun dropFromItems(id: String) {
        items.value = items.value.filterNot { it.id == id }
    }

    private fun startRegionDownload(bundle: DownloadBundle) {
        // Prefer the bundle's own (user-relative) bbox; fall back to a static region.
        val region = bundle.region
            ?: OfflineBundles.region(bundle.id)?.let {
                com.stepanok.undp.domain.model.GeoBox(it.north, it.east, it.south, it.west, it.minZoom, it.maxZoom)
            }
            ?: return
        val definition = OfflineTilePyramidRegionDefinition(
            OfflineBundles.STYLE_URL,
            LatLngBounds.from(region.north, region.east, region.south, region.west),
            region.minZoom,
            region.maxZoom,
            context.resources.displayMetrics.density,
        )
        // Persist the human label so loadExisting() can restore the pack's title after a relaunch
        // (the id itself is re-derived from the region's centre, so it need not be stored).
        val metadata = bundle.title.encodeToByteArray()

        offlineManager.createOfflineRegion(
            definition,
            metadata,
            object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(offlineRegion: OfflineRegion) {
                    offlineRegion.setObserver(object : OfflineRegion.OfflineRegionObserver {
                        override fun onStatusChanged(status: OfflineRegionStatus) {
                            if (status.isComplete) {
                                put(bundle.copy(state = DownloadState.Done))
                            } else {
                                val required = status.requiredResourceCount.coerceAtLeast(1)
                                val fraction = (status.completedResourceCount.toDouble() / required).coerceIn(0.0, 1.0)
                                put(
                                    bundle.copy(
                                        state = DownloadState.Downloading(
                                            (fraction * bundle.bytesTotal).toLong(),
                                            bundle.bytesTotal,
                                        ),
                                    ),
                                )
                            }
                        }

                        override fun onError(error: OfflineRegionError) {
                            put(bundle.copy(state = DownloadState.Failed(error.message ?: "download failed")))
                        }

                        override fun mapboxTileCountLimitExceeded(limit: Long) {
                            put(bundle.copy(state = DownloadState.Failed("offline tile limit reached")))
                        }
                    })
                    offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
                }

                override fun onError(error: String) {
                    put(bundle.copy(state = DownloadState.Failed(error)))
                }
            },
        )
    }

    /** Reconstruct DownloadBundles from the regions already stored in MapLibre's offline DB. */
    private fun loadExisting() {
        offlineManager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                offlineRegions?.forEach { region ->
                    val def = region.definition as? OfflineTilePyramidRegionDefinition ?: return@forEach
                    val b = def.bounds ?: return@forEach
                    val title = region.metadata?.decodeToString()?.takeIf { it.isNotBlank() } ?: "Offline map"
                    // id re-derived from the region's centre — matches areaPack()/cancel() exactly.
                    val id = OfflineBundles.areaId(
                        (b.latitudeNorth + b.latitudeSouth) / 2.0,
                        (b.longitudeEast + b.longitudeWest) / 2.0,
                    )
                    // Tiles are on disk → show the restored pack as ready. (A partially-downloaded
                    // pack is the rare case; "Ready" is the correct state for a completed one.)
                    put(
                        DownloadBundle(
                            id = id,
                            title = title,
                            type = DownloadType.CRISIS_BUNDLE,
                            bytesTotal = 18_000_000,
                            state = DownloadState.Done,
                            region = GeoBox(b.latitudeNorth, b.longitudeEast, b.latitudeSouth, b.longitudeWest, def.minZoom, def.maxZoom),
                        ),
                    )
                }
            }

            override fun onError(error: String) { /* no existing regions readable → keep current list */ }
        })
    }

    private fun put(bundle: DownloadBundle) {
        val current = items.value
        items.value = if (current.any { it.id == bundle.id }) {
            current.map { if (it.id == bundle.id) bundle else it }
        } else {
            current + bundle
        }
    }
}

actual fun createDownloadQueue(): DownloadQueue = AndroidDownloadQueue(AndroidAppContext.require())
