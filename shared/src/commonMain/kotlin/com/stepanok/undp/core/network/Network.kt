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

/** The single API origin the app ever talks to — the host the TLS pins below apply to. */
const val BEACON_API_HOST = "beacon-api.stepanok.com"

/**
 * TLS public-key (SPKI, SHA-256) pins for [BEACON_API_HOST]. The app talks to exactly ONE
 * origin, so we pin its chain to Let's Encrypt's **ISRG roots** — stable for years, so they
 * survive every leaf/intermediate auto-renewal, while still rejecting a man-in-the-middle
 * that presents a chain from any OTHER certificate authority (e.g. a corporate TLS-inspection
 * proxy or a rogue CA installed on the device). Enforced by the platform HTTP engines:
 * OkHttp's CertificatePinner on Android, an NSURLSession server-trust evaluation on iOS.
 *
 * VERIFY against production from a clean (non-proxied) network before each release:
 *   openssl s_client -connect beacon-api.stepanok.com:443 -showcerts </dev/null | \
 *     openssl x509 -pubkey -noout | openssl pkey -pubin -outform der | \
 *     openssl dgst -sha256 -binary | openssl base64
 * (pins are the SHA-256 of the ISRG root SubjectPublicKeyInfo; recompute if Let's Encrypt rotates roots).
 */
val BEACON_TLS_PINS = listOf(
    "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M=", // ISRG Root X1 (RSA-4096)
    "sha256/diGVwiVYbubAI3RW4hB9xU8e/CH2GnkuvVFZE8zmgzI=", // ISRG Root X2 (EC P-384)
)

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
