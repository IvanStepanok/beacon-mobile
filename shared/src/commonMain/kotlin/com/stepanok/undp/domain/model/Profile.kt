package com.stepanok.undp.domain.model

/** Anonymous reporter identity + anti-gaming recognition (points reward quality, not volume). */
data class Profile(
    val anonymousId: String,      // "A4-92K"
    val alias: String? = null,
    val reportCount: Int,
    val buildingCount: Int,
    val points: Int,
    val badges: List<Badge>,
)

data class Badge(
    val id: String,
    val name: String,
    val earned: Boolean,
    val progressLabel: String? = null,   // "12/50"
)
