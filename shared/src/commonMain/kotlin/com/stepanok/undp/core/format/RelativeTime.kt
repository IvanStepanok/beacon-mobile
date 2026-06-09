@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.stepanok.undp.core.format

import kotlin.time.Instant

/** Lightweight relative-time label. (Localization of these strings lands with full i18n.) */
fun relativeTime(now: Instant, then: Instant): String {
    val d = now - then
    return when {
        d.inWholeMinutes < 1 -> "just now"
        d.inWholeHours < 1 -> "${d.inWholeMinutes} min ago"
        d.inWholeDays < 1 -> "${d.inWholeHours} hr ago"
        else -> "${d.inWholeDays} d ago"
    }
}
