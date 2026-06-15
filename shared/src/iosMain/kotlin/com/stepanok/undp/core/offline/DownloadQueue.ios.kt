package com.stepanok.undp.core.offline

import MapLibre.MLNCoordinateBoundsMake
import MapLibre.MLNOfflinePack
import MapLibre.MLNOfflinePackProgressChangedNotification
import MapLibre.MLNOfflinePackStateComplete
import MapLibre.MLNOfflineStorage
import MapLibre.MLNTilePyramidOfflineRegion
import com.stepanok.undp.domain.model.DownloadBundle
import com.stepanok.undp.domain.model.DownloadState
import com.stepanok.undp.domain.model.DownloadType
import com.stepanok.undp.domain.model.GeoBox
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

    init {
        // Restore packs already in MapLibre's offline DB so a download from a prior session still
        // shows as "Ready" and feeds the map's offline-area fallback. (MLNOfflineStorage loads packs
        // asynchronously; on a cold start `packs` may still be nil here — acceptable for now, refine
        // with KVO/notification observation when the iOS build is wired up.)
        loadExisting()
    }

    override fun observe(): Flow<List<DownloadBundle>> = items.asStateFlow()

    @OptIn(ExperimentalForeignApi::class)
    private fun loadExisting() {
        val packs = storage.packs ?: return
        packs.forEach { p ->
            val pack = p as? MLNOfflinePack ?: return@forEach
            val region = pack.region as? MLNTilePyramidOfflineRegion ?: return@forEach
            val geoBox = region.bounds.useContents {
                GeoBox(
                    north = ne.latitude, east = ne.longitude,
                    south = sw.latitude, west = sw.longitude,
                    minZoom = region.minimumZoomLevel, maxZoom = region.maximumZoomLevel,
                )
            }
            // id + label both re-derived from the region's centre — matches areaPack()/cancel().
            val centerLat = (geoBox.north + geoBox.south) / 2.0
            val centerLng = (geoBox.east + geoBox.west) / 2.0
            val label = "${kotlin.math.round(centerLat * 100) / 100.0}, ${kotlin.math.round(centerLng * 100) / 100.0}"
            put(
                DownloadBundle(
                    id = OfflineBundles.areaId(centerLat, centerLng),
                    title = "Offline map · $label",
                    type = DownloadType.CRISIS_BUNDLE,
                    bytesTotal = 18_000_000,
                    state = DownloadState.Done,
                    region = geoBox,
                ),
            )
        }
    }

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
        // Remove the actual pack from MapLibre's offline storage (frees the tiles), matching by the
        // same centre-derived id used everywhere, then drop the card.
        val target = storage.packs?.firstOrNull { p ->
            val region = (p as? MLNOfflinePack)?.region as? MLNTilePyramidOfflineRegion
            region != null && region.bounds.useContents {
                OfflineBundles.areaId((ne.latitude + sw.latitude) / 2.0, (ne.longitude + sw.longitude) / 2.0) == id
            }
        } as? MLNOfflinePack
        if (target != null) storage.removePack(target, null)
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
