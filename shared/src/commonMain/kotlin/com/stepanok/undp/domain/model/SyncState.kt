@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.stepanok.undp.domain.model

import kotlin.time.Instant

/** Visible per-report sync state — the trust-building offline UX competitors don't surface. */
sealed interface SyncState {
    data object Queued : SyncState

    data class Syncing(val bytesSent: Long, val bytesTotal: Long) : SyncState {
        val fraction: Float get() = if (bytesTotal > 0) bytesSent.toFloat() / bytesTotal else 0f
    }

    data object Synced : SyncState

    /**
     * The report row reached the server, but its photo upload has not yet succeeded.
     * The item stays in the durable outbox and the photo is retried on the next flush,
     * so a failed photo POST is never silently treated as a clean [Synced].
     */
    data object PhotoPending : SyncState

    data class Failed(
        val attempt: Int,
        val nextRetryAt: Instant,
        val reason: String,
    ) : SyncState
}

/** A queued upload tracked by the outbox. */
data class OutboxItem(
    val reportId: String,
    val idempotencyKey: String,
    val sizeBytes: Long,
    val state: SyncState,
)
