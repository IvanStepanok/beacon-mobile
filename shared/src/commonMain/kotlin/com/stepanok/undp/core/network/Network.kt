package com.stepanok.undp.core.network

import com.stepanok.undp.core.storage.PrefKeys
import com.stepanok.undp.core.storage.Prefs
import io.ktor.client.engine.HttpClientEngine
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Platform HTTP engine (OkHttp on Android, Darwin on iOS). */
expect fun beaconHttpEngine(): HttpClientEngine

/** Backend base URL per platform (Android emulator reaches the host at 10.0.2.2). */
expect val beaconBaseUrl: String

/**
 * Per-install anonymous device identity sent as the X-Device-Id header.
 *
 * Generated ONCE on first launch via [Uuid.random] and persisted in [Prefs], then read back
 * unchanged on every subsequent launch — it must be stable across restarts so server-side
 * identity (My Reports, points, rate-limit, dedup, photo ownership) stays attached to THIS
 * install and only this install. A shared hardcoded id would let any caller overwrite another
 * user's report photo, so per-install uniqueness is a correctness/security requirement.
 *
 * Resolution is synchronous and memoized so the value is available at (and before) the very
 * first HTTP request, when the Ktor [defaultRequest] header is computed.
 */
object DeviceId {
    // `by lazy` uses SYNCHRONIZED mode: the read-or-generate-and-persist runs exactly once
    // even under concurrent first access, and the result is memoized for every later request.
    private val id: String by lazy { resolve() }

    fun get(): String = id

    @OptIn(ExperimentalUuidApi::class)
    private fun resolve(): String {
        val existing = Prefs.getString(PrefKeys.DEVICE_ID)
        if (!existing.isNullOrBlank()) return existing
        return Uuid.random().toString().also { Prefs.setString(PrefKeys.DEVICE_ID, it) }
    }
}
