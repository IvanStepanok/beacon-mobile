package com.stepanok.undp.core.io

import com.stepanok.undp.core.android.AndroidAppContext
import java.io.File

actual fun readFileBytes(path: String): ByteArray? =
    runCatching { File(path).takeIf { it.exists() }?.readBytes() }.getOrNull()

actual fun writeFileBytes(path: String, bytes: ByteArray): Boolean = runCatching {
    val target = File(path)
    target.parentFile?.mkdirs()
    // Atomic-ish write: write to a temp sibling, then rename over the target.
    val tmp = File(target.parentFile, "${target.name}.tmp")
    tmp.writeBytes(bytes)
    if (!tmp.renameTo(target)) {
        // Fallback: rename can fail if target exists on some filesystems; overwrite directly.
        target.writeBytes(bytes)
        tmp.delete()
    }
    true
}.getOrElse { false }

/** Persistent internal storage (filesDir) — survives app kill; never a cache dir. */
actual fun outboxFilePath(): String =
    File(AndroidAppContext.require().filesDir, "outbox.json").absolutePath
