package com.stepanok.undp.core.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin

actual fun beaconHttpEngine(): HttpClientEngine = Darwin.create()

// Live backend (Go + PostGIS on the stepanok.com server, HTTPS via Traefik).
actual val beaconBaseUrl: String = "https://beacon-api.stepanok.com"
