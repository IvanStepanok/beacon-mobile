package com.stepanok.undp.core.network

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.CoreFoundation.CFArrayGetCount
import platform.CoreFoundation.CFArrayGetValueAtIndex
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFRelease
import platform.Foundation.NSData
import platform.Foundation.base64EncodedStringWithOptions
import platform.Foundation.create
import platform.Security.SecCertificateCopyData
import platform.Security.SecCertificateRef
import platform.Security.SecTrustCopyCertificateChain
import platform.Security.SecTrustEvaluateWithError
import platform.Security.SecTrustRef

/**
 * iOS TLS pinning for [BEACON_API_HOST]. Same trust anchors as Android (Let's Encrypt /
 * ISRG roots), pinned here by the SHA-256 of the root CERTIFICATE DER — the lowest-interop,
 * hardest-to-get-wrong representation on the Security framework (no public-key extraction or
 * ASN.1 SubjectPublicKeyInfo reconstruction). The values differ in form from Android's SPKI
 * pins ([BEACON_TLS_PINS]) but anchor to the SAME two ISRG roots, so both platforms accept
 * exactly the Let's Encrypt chains and reject any other CA. Re-verify against production
 * from a clean (non-proxied) network before each release; recompute if the roots rotate.
 *
 * The SecTrust is extracted in the per-target source sets (iosArm64Main / iosSimulatorArm64Main)
 * because the `NSURLProtectionSpace.serverTrust` property is dropped from the COMMONIZED iosMain
 * view of Foundation; the trust evaluation + pin check below use only Security-framework APIs,
 * which ARE available in iosMain, so the security logic stays in one place.
 */
private val BEACON_TLS_CERT_PINS = setOf(
    "sha256/lrzsBiZJdvN0YHeazyjFp8/oo8Cq4RqP/O4FwL3fCMY=", // ISRG Root X1
    "sha256/aXKbjhWobvwXelevtxcd/GSt0owvyozxUH40RTzLFHA=", // ISRG Root X2
)

/**
 * Returns true iff the system trusts the chain (valid signatures, not expired, hostname OK)
 * AND a pinned ISRG root appears in the evaluated chain. Fail-closed: any failure → false.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun beaconEvaluateAndPin(trust: SecTrustRef): Boolean {
    if (!SecTrustEvaluateWithError(trust, null)) return false
    return trustChainMatchesPin(trust)
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun trustChainMatchesPin(trust: SecTrustRef): Boolean {
    val chain = SecTrustCopyCertificateChain(trust) ?: return false
    try {
        val count = CFArrayGetCount(chain)
        for (i in 0 until count) {
            val raw = CFArrayGetValueAtIndex(chain, i) ?: continue
            val cert: SecCertificateRef = raw.reinterpret()
            val der = SecCertificateCopyData(cert) ?: continue
            try {
                val length = CFDataGetLength(der)
                val bytes = CFDataGetBytePtr(der)
                if (bytes == null || length <= 0) continue
                val digest = UByteArray(CC_SHA256_DIGEST_LENGTH)
                digest.usePinned { d ->
                    CC_SHA256(bytes, length.convert(), d.addressOf(0))
                }
                val b64 = digest.usePinned { d ->
                    NSData.create(bytes = d.addressOf(0), length = CC_SHA256_DIGEST_LENGTH.convert())
                        .base64EncodedStringWithOptions(0u)
                }
                if (BEACON_TLS_CERT_PINS.contains("sha256/$b64")) {
                    return true
                }
            } finally {
                CFRelease(der)
            }
        }
    } finally {
        CFRelease(chain)
    }
    return false
}
