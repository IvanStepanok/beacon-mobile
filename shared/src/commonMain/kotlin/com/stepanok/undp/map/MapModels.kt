package com.stepanok.undp.map

import com.stepanok.undp.domain.model.DamageLevel

data class GeoPoint(val lat: Double, val lng: Double)

/** A damage report reduced to what the map needs to render a pin. */
data class ReportPin(
    val id: String,
    val lat: Double,
    val lng: Double,
    val level: DamageLevel,
)

object MapDefaults {
    const val OPEN_FREE_MAP_LIBERTY = "https://tiles.openfreemap.org/styles/liberty"
    val ANTAKYA = GeoPoint(36.2021, 36.1601)
    /** Neutral initial view before the user's location resolves (no region bias). */
    val WORLD = GeoPoint(25.0, 10.0)
    const val WORLD_ZOOM = 1.5
    const val CITY_ZOOM = 13.5
    const val NEIGHBORHOOD_ZOOM = 14.5
    const val BUILDING_ZOOM = 17.0
}
