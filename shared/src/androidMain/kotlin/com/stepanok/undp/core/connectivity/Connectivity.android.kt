package com.stepanok.undp.core.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.stepanok.undp.core.android.AndroidAppContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Reflects real network reachability via ConnectivityManager's default-network callback.
 * Every callback recomputes from the active network, and a light periodic re-check self-heals
 * the state if a transition callback is ever missed (so the offline banner can't get stuck).
 */
class AndroidConnectivityObserver(context: Context) : ConnectivityObserver {

    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val _status = MutableStateFlow(currentStatus())
    override val status: StateFlow<ConnectivityStatus> = _status.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        runCatching {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _status.value = currentStatus()
                }

                override fun onLost(network: Network) {
                    _status.value = currentStatus()
                }

                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    _status.value = statusOf(caps)
                }
            })
        }
        // Safety net: re-evaluate every few seconds so a missed callback never leaves a stuck banner.
        scope.launch {
            while (true) {
                delay(4_000)
                _status.value = currentStatus()
            }
        }
    }

    private fun statusOf(caps: NetworkCapabilities?): ConnectivityStatus =
        if (caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            ConnectivityStatus.ONLINE
        } else {
            ConnectivityStatus.OFFLINE
        }

    private fun currentStatus(): ConnectivityStatus = statusOf(cm.getNetworkCapabilities(cm.activeNetwork))
}

actual fun createConnectivityObserver(): ConnectivityObserver =
    AndroidConnectivityObserver(AndroidAppContext.require())
