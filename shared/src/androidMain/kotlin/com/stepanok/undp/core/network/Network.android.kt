package com.stepanok.undp.core.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.CertificatePinner

// TLS public-key pinning: OkHttp rejects any chain for the API host that does not
// terminate in a pinned Let's Encrypt/ISRG root, defeating MITM via a rogue or
// device-installed CA. The pins are the shared [BEACON_TLS_PINS] (SPKI sha256).
actual fun beaconHttpEngine(): HttpClientEngine = OkHttp.create {
    config {
        certificatePinner(
            CertificatePinner.Builder()
                .apply { BEACON_TLS_PINS.forEach { pin -> add(BEACON_API_HOST, pin) } }
                .build(),
        )
    }
}

// Live backend (Go + PostGIS on the stepanok.com server, HTTPS via Traefik).
actual val beaconBaseUrl: String = "https://beacon-api.stepanok.com"
