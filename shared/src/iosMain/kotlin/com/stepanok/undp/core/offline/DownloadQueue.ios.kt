package com.stepanok.undp.core.offline

import MapLibre.MLNCoordinateBoundsMake
import MapLibre.MLNOfflinePack
import MapLibre.MLNOfflinePackProgressChangedNotification
import MapLibre.MLNOfflinePackStateComplete
import MapLibre.MLNOfflineStorage
import MapLibre.MLNTilePyramidOfflineRegion
import com.stepanok.undp.domain.model.DownloadBundle
import com.stepanok.undp.domain.model.DownloadState
import com.stepanok.undp.domain.repository.DownloadQueue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.Foundation.NSData
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.darwin.NSObjectProtocol

/**
 * Real offline-region downloads via MapLibre iOS (MLNOfflineStorage). Tiles for the crisis bbox
 * are fetched into MapLibre's offline database, so the basemap renders with no network.
 */
@OptIn(ExperimentalForeignApi::class)
class IosDownloadQueue : DownloadQueue {

    private val items = MutableStateFlow(OfflineBundles.seed())
    private val storage = MLNOfflineStorage.sharedOfflineStorage()
    private var observer: NSObjectProtocol? = null

    override fun observe(): Flow<List<DownloadBundle>> = items.asStateFlow()

    override suspend fun enqueue(bundle: DownloadBundle) {
        put(bundle.copy(state = DownloadState.Queued))
        // Prefer the bundle's own (user-relative) bbox; fall back to a static region.
        val region = bundle.region
            ?: OfflineBundles.region(bundle.id)?.let {
                com.stepanok.undp.domain.model.GeoBox(it.north, it.east, it.south, it.west, it.minZoom, it.maxZoom)
            }
            ?: return
        val bounds = MLNCoordinateBoundsMake(
            CLLocationCoordinate2DMake(region.south, region.west),
            CLLocationCoordinate2DMake(region.north, region.east),
        )
        val mlnRegion = MLNTilePyramidOfflineRegion(
            styleURL = NSURL(string = OfflineBundles.STYLE_URL),
            bounds = bounds,
            fromZoomLevel = region.minZoom,
            toZoomLevel = region.maxZoom,
        )
        val context = ("beacon:" + bundle.id).encodeToByteArray().toNSData()
        storage.addPackForRegion(mlnRegion, context) { pack, error ->
            if (pack == null || error != null) {
                put(bundle.copy(state = DownloadState.Failed(error?.localizedDescription ?: "download failed")))
            } else {
                observePack(bundle, pack)
                pack.resume()
            }
        }
    }

    private fun observePack(bundle: DownloadBundle, pack: MLNOfflinePack) {
        observer = NSNotificationCenter.defaultCenter.addObserverForName(
            name = MLNOfflinePackProgressChangedNotification,
            `object` = pack,
            queue = NSOperationQueue.mainQueue,
        ) { _: NSNotification? ->
            if (pack.state == MLNOfflinePackStateComplete) {
                put(bundle.copy(state = DownloadState.Done))
            } else {
                val fraction = pack.progress.useContents {
                    val expected = countOfResourcesExpected.toDouble().coerceAtLeast(1.0)
                    (countOfResourcesCompleted.toDouble() / expected).coerceIn(0.0, 1.0)
                }
                put(bundle.copy(state = DownloadState.Downloading((fraction * bundle.bytesTotal).toLong(), bundle.bytesTotal)))
            }
        }
    }

    override suspend fun cancel(id: String) {
        items.value = items.value.filterNot { it.id == id }
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

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
    NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
}

actual fun createDownloadQueue(): DownloadQueue = IosDownloadQueue()
