@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.stepanok.undp.domain.model

import kotlin.time.Instant

/** An active crisis the reports are attached to (drives the home banner + suggested crisis nature). */
data class Crisis(
    val id: String,
    val title: String,        // "Earthquake M 6.4"
    val area: String,         // "Antakya district"
    val nature: CrisisNature,
    val startedAt: Instant,
    val centerLat: Double,
    val centerLng: Double,
    val source: String = "UNDP RAPIDA",
    val status: String = "active",      // active | proposed | closed | dismissed
    val radiusKm: Double = 0.0,
    val distanceKm: Double? = null,     // set on /crises/near responses
    val covers: Boolean = false,        // queried point is within this crisis's radius
)
