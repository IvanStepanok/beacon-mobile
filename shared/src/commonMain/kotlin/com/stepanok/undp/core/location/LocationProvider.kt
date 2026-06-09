package com.stepanok.undp.core.location

/** A single device GPS fix. */
data class DeviceLocation(
    val lat: Double,
    val lng: Double,
    val accuracyMeters: Double,
)

/** Real device location (Android LocationManager / iOS CLLocationManager). */
interface LocationProvider {
    /** Best available current fix, or null if unavailable / permission denied. */
    suspend fun current(): DeviceLocation?

    /**
     * Cached last-known fix, returned IMMEDIATELY (no GPS wait). Used to centre the
     * map on launch / recenter instantly; [current] then refines it in the background.
     * Null if there is no cached fix or permission is denied.
     */
    suspend fun lastKnown(): DeviceLocation?
}

expect fun createLocationProvider(): LocationProvider
