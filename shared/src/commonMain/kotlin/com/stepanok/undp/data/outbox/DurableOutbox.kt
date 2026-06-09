@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.stepanok.undp.data.outbox

import com.stepanok.undp.core.io.outboxFilePath
import com.stepanok.undp.core.io.readFileBytes
import com.stepanok.undp.core.io.writeFileBytes
import com.stepanok.undp.data.remote.SubmitReportDto
import com.stepanok.undp.domain.model.Report
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Disk-backed durable outbox (JSON file, deliberately NOT SQLDelight to avoid build risk).
 *
 * We persist a small [OutboxEntry] projection rather than the full [Report] graph: it wraps the
 * already-`@Serializable` [SubmitReportDto] (the exact submit payload) plus the handful of display
 * fields "My Reports" needs and the durable sync bookkeeping. A relaunch hydrates these entries and
 * rebuilds [Report]s so pending uploads resume — a Queued/Failed/PhotoPending item is never lost.
 */
class DurableOutbox(
    private val path: String = outboxFilePath(),
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Load + rebuild reports from disk; empty if no file / unreadable / corrupt. */
    fun load(): List<Report> {
        val bytes = readFileBytes(path) ?: return emptyList()
        if (bytes.isEmpty()) return emptyList()
        return runCatching {
            json.decodeFromString<OutboxFile>(bytes.decodeToString()).entries.map { it.toReport() }
        }.getOrElse { emptyList() }
    }

    /** Atomically overwrite the file with the current outbox. Best-effort (returns false on IO error). */
    fun save(reports: List<Report>): Boolean {
        val file = OutboxFile(entries = reports.map { it.toOutboxEntry() })
        val text = runCatching { json.encodeToString(file) }.getOrNull() ?: return false
        return writeFileBytes(path, text.encodeToByteArray())
    }
}

@Serializable
private data class OutboxFile(
    val version: Int = 1,
    val entries: List<OutboxEntry> = emptyList(),
)

/** Persisted snapshot of a single outbox row. */
@Serializable
data class OutboxEntry(
    val submit: SubmitReportDto,
    val photoLocalPath: String? = null,
    val photoRemoteUrl: String? = null,
    // Display fields My Reports / Sync Queue render directly (so we don't depend on re-derivation).
    val damageLevel: String,
    val damageTier: String? = null,
    val place: String = "",
    val capturedAtMillis: Long,
    val isMine: Boolean = true,
    val version: Int = 1,
    val supersedesReportId: String? = null,
    // Durable sync state.
    val syncKind: SyncKind = SyncKind.QUEUED,
    val attempt: Int = 0,
    val nextRetryAtMillis: Long? = null,
    val reason: String? = null,
)

/** Flattened, file-stable representation of [SyncState] (kotlinx sealed-poly avoided on purpose). */
@Serializable
enum class SyncKind { QUEUED, SYNCED, PHOTO_PENDING, FAILED }
