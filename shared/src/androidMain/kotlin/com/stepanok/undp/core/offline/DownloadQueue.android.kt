package com.stepanok.undp.core.offline

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.stepanok.undp.core.android.AndroidAppContext
import com.stepanok.undp.domain.model.DownloadBundle
import com.stepanok.undp.domain.model.DownloadState
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

    override fun observe(): Flow<List<DownloadBundle>> = items.asStateFlow()

    override suspend fun enqueue(bundle: DownloadBundle) {
        put(bundle.copy(state = DownloadState.Queued))
        // OfflineManager + its callbacks must be used on the main thread.
        main.post { startRegionDownload(bundle) }
    }

    override suspend fun cancel(id: String) {
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
        val metadata = bundle.id.encodeToByteArray()

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
