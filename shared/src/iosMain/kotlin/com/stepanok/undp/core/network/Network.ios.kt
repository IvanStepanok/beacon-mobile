package com.stepanok.undp.core.network

// The Darwin HTTP engine actual (with TLS pinning) lives in the per-target source sets
// (iosArm64Main / iosSimulatorArm64Main) — see Engine.*.kt — because the SecTrust must be
// read there via a cinterop shim (NSURLProtectionSpace.serverTrust is not exposed by the
// Kotlin/Native Foundation binding). The pin check is shared in CertPinning.ios.kt.

// Live backend (Go + PostGIS on the stepanok.com server, HTTPS via Traefik).
actual val beaconBaseUrl: String = "https://beacon-api.stepanok.com"
