@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.stepanok.undp.domain.model

import kotlin.time.Instant

data class Building(
    val id: String,
    val lat: Double,
    val lng: Double,
)

data class BuildingVersion(
    val reportId: String,
    val damage: DamageTier,
    val at: Instant,
    val note: String,
    val isCurrent: Boolean,
)

/** Damage history for one building — latest + the over-time timeline shown in report detail. */
data class BuildingTimeline(
    val buildingId: String,
    val current: DamageTier?,
    val versions: List<BuildingVersion>,
)

/** Reports grouped by area for prioritization. */
data class AreaGroup(
    val area: String,
    val count: Int,
    val worst: DamageTier,
)
