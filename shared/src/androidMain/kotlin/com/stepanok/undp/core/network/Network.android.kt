package com.stepanok.undp.core.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp

actual fun beaconHttpEngine(): HttpClientEngine = OkHttp.create()

// Live backend (Go + PostGIS on the stepanok.com server, HTTPS via Traefik).
actual val beaconBaseUrl: String = "https://beacon-api.stepanok.com"
