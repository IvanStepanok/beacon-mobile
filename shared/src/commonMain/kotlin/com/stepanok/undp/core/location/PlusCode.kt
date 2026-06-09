package com.stepanok.undp.core.location

import kotlin.math.floor

/**
 * Open Location Code (Google "Plus Code") encoder — a free, offline, open-standard way to turn a
 * lat/lng into a short human-shareable location code (e.g. `8FVC9G8F+6W`). Replaces the paid
 * what3words API: computed entirely on-device, no network, no key. An 11-character code is
 * ~3 m × 3 m precision. See plus.codes / github.com/google/open-location-code.
 */
object PlusCode {
    private const val ALPHABET = "23456789CFGHJMPQRVWX" // 20 symbols
    private const val SEPARATOR_POSITION = 8

    /** Encodes [latitude]/[longitude] to an 11-digit Plus Code (~3 m precision). */
    fun encode(latitude: Double, longitude: Double): String {
        val lat = latitude.coerceIn(-90.0, 89.999999)
        var lng = longitude
        while (lng < -180.0) lng += 360.0
        while (lng >= 180.0) lng -= 360.0

        var latVal = lat + 90.0
        var lngVal = lng + 180.0
        val digits = StringBuilder()

        // 5 pairs (10 digits): each pair resolves lat then lng, dividing the place by 20 each time.
        var latPlace = 20.0
        var lngPlace = 20.0
        repeat(5) {
            val latDigit = floor(latVal / latPlace).toInt().coerceIn(0, 19)
            latVal -= latDigit * latPlace
            latPlace /= 20.0
            val lngDigit = floor(lngVal / lngPlace).toInt().coerceIn(0, 19)
            lngVal -= lngDigit * lngPlace
            lngPlace /= 20.0
            digits.append(ALPHABET[latDigit]).append(ALPHABET[lngDigit])
        }

        // 1 grid digit (11th): the residual 0.000125° cell split into 5 rows × 4 cols.
        val latGrid = 0.000125 / 5.0
        val lngGrid = 0.000125 / 4.0
        val row = floor(latVal / latGrid).toInt().coerceIn(0, 4)
        val col = floor(lngVal / lngGrid).toInt().coerceIn(0, 3)
        digits.append(ALPHABET[row * 4 + col])

        val s = digits.toString()
        return s.substring(0, SEPARATOR_POSITION) + "+" + s.substring(SEPARATOR_POSITION)
    }
}
