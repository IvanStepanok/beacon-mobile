package com.stepanok.undp.core.io

/** Reads a local file's bytes (for uploading a captured photo), or null if unavailable. */
expect fun readFileBytes(path: String): ByteArray?

/**
 * Writes [bytes] to [path] in a PERSISTENT app directory, surviving process death.
 * Implementations should write atomically (temp file + rename) so a crash mid-write
 * never corrupts an existing file. Returns true on success.
 */
expect fun writeFileBytes(path: String, bytes: ByteArray): Boolean

/**
 * Absolute path of the persistent durable-outbox file (NOT a cache dir, which can be
 * evicted). Android: Context.filesDir; iOS: Application Support directory.
 */
expect fun outboxFilePath(): String

/**
 * Absolute path of the persistent "captures" directory for queued photos — the SAME
 * persistent root as [outboxFilePath] (never a cache/tmp dir, which the OS can purge),
 * so a queued photo survives until upload across cache eviction + process death.
 * Implementations create the directory if missing.
 *
 * Known trade-off: captures are retained FOREVER (each local file backs its My-Reports
 * thumbnail after sync) — size-capped pruning of synced captures is roadmap, not implemented.
 */
expect fun capturesDirPath(): String
