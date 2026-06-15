package com.stepanok.undp.core.network

import beacon.sectrust.beaconCredentialForTrust
import beacon.sectrust.beaconServerTrust
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSURLAuthenticationMethodServerTrust
import platform.Foundation.NSURLSessionAuthChallengeCancelAuthenticationChallenge
import platform.Foundation.NSURLSessionAuthChallengePerformDefaultHandling
import platform.Foundation.NSURLSessionAuthChallengeUseCredential

// Darwin engine + TLS pinning. The SecTrust is read via a cinterop shim (beaconServerTrust)
// because NSURLProtectionSpace.serverTrust is absent from the Kotlin/Native Foundation binding;
// the pin check (beaconEvaluateAndPin) is shared from iosMain (CertPinning.ios.kt).
@OptIn(ExperimentalForeignApi::class)
actual fun beaconHttpEngine(): HttpClientEngine = Darwin.create {
    handleChallenge { _, _, challenge, completionHandler ->
        val space = challenge.protectionSpace
        if (space.authenticationMethod != NSURLAuthenticationMethodServerTrust) {
            completionHandler(NSURLSessionAuthChallengePerformDefaultHandling, null)
            return@handleChallenge
        }
        val trust = beaconServerTrust(space)
        if (trust != null && beaconEvaluateAndPin(trust)) {
            completionHandler(NSURLSessionAuthChallengeUseCredential, beaconCredentialForTrust(trust))
        } else {
            completionHandler(NSURLSessionAuthChallengeCancelAuthenticationChallenge, null)
        }
    }
}
