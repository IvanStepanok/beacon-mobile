package com.stepanok.undp.core.location

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLLocationAccuracyNearestTenMeters
import platform.Foundation.NSError
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume

/** Real GPS via CLLocationManager. The manager lives on the main queue (needs a run loop). */
@OptIn(ExperimentalForeignApi::class)
class IosLocationProvider : LocationProvider {

    override suspend fun lastKnown(): DeviceLocation? = suspendCancellableCoroutine { cont ->
        dispatch_async(dispatch_get_main_queue()) {
            val loc = CLLocationManager().location
            val result = loc?.coordinate?.useContents {
                DeviceLocation(latitude, longitude, loc.horizontalAccuracy)
            }
            if (cont.isActive) cont.resume(result)
        }
    }

    override suspend fun current(): DeviceLocation? = suspendCancellableCoroutine { cont ->
        dispatch_async(dispatch_get_main_queue()) {
            val manager = CLLocationManager()
            val delegate = LocationDelegate(manager) { result ->
                if (cont.isActive) cont.resume(result)
            }
            manager.delegate = delegate
            manager.desiredAccuracy = kCLLocationAccuracyNearestTenMeters
            manager.requestWhenInUseAuthorization()
            manager.requestLocation()
            cont.invokeOnCancellation {
                delegate.detach()
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class LocationDelegate(
    private val manager: CLLocationManager,
    private var onResult: ((DeviceLocation?) -> Unit)?,
) : NSObject(), CLLocationManagerDelegateProtocol {

    // CLLocationManager.delegate is weak — keep a strong self-reference until a callback fires.
    private var selfRef: LocationDelegate? = this

    override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
        val loc = didUpdateLocations.lastOrNull() as? CLLocation
        val result = loc?.coordinate?.useContents {
            DeviceLocation(latitude, longitude, loc.horizontalAccuracy)
        }
        finish(result)
    }

    override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
        finish(null)
    }

    private fun finish(result: DeviceLocation?) {
        manager.stopUpdatingLocation()
        onResult?.invoke(result)
        detach()
    }

    fun detach() {
        onResult = null
        selfRef = null
    }
}

actual fun createLocationProvider(): LocationProvider = IosLocationProvider()
