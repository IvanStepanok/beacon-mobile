@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.stepanok.undp.core.io

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import platform.posix.memcpy

actual fun readFileBytes(path: String): ByteArray? {
    val data: NSData = NSData.dataWithContentsOfFile(path) ?: return null
    val size = data.length.toInt()
    if (size == 0) return ByteArray(0)
    val out = ByteArray(size)
    out.usePinned { pinned -> memcpy(pinned.addressOf(0), data.bytes, data.length) }
    return out
}

actual fun writeFileBytes(path: String, bytes: ByteArray): Boolean {
    val data: NSData = if (bytes.isEmpty()) {
        NSData()
    } else {
        bytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
        }
    }
    // Atomic write (atomically = true → temp file + rename under the hood).
    val tmp = "$path.tmp"
    if (!data.writeToFile(tmp, atomically = true)) return false
    val fm = NSFileManager.defaultManager
    fm.removeItemAtPath(path, error = null) // ignore "no such file"
    return fm.moveItemAtPath(tmp, toPath = path, error = null)
}

/** Application Support directory — persistent, NOT purgeable like the caches dir. */
actual fun outboxFilePath(): String {
    val dirs = NSSearchPathForDirectoriesInDomains(
        directory = NSApplicationSupportDirectory,
        domainMask = NSUserDomainMask,
        expandTilde = true,
    )
    // Fallback to a temporary location only if Application Support is unavailable.
    val base = (dirs.firstOrNull() as? String) ?: platform.Foundation.NSTemporaryDirectory()
    // Ensure the directory exists (Application Support is not created automatically).
    NSFileManager.defaultManager.createDirectoryAtPath(
        path = base,
        withIntermediateDirectories = true,
        attributes = null,
        error = null,
    )
    return if (base.endsWith("/")) "${base}outbox.json" else "$base/outbox.json"
}
