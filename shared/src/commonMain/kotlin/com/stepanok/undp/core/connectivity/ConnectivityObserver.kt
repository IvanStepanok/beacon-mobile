package com.stepanok.undp.core.connectivity

import kotlinx.coroutines.flow.StateFlow

enum class ConnectivityStatus { ONLINE, OFFLINE }

/**
 * Real device connectivity. Backed by Android's [android.net.ConnectivityManager]
 * default-network callback and iOS's `NWPathMonitor` — the UI reflects the actual
 * link state (there is no manual toggle).
 */
interface ConnectivityObserver {
    val status: StateFlow<ConnectivityStatus>
}

/** Platform-provided observer (Android ConnectivityManager / iOS NWPathMonitor). */
expect fun createConnectivityObserver(): ConnectivityObserver
