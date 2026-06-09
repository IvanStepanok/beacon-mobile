package com.stepanok.undp.core.connectivity

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_queue_create

/** Reflects real network reachability via the Network framework's NWPathMonitor. */
@OptIn(ExperimentalForeignApi::class)
class IosConnectivityObserver : ConnectivityObserver {

    private val _status = MutableStateFlow(ConnectivityStatus.ONLINE)
    override val status: StateFlow<ConnectivityStatus> = _status.asStateFlow()

    private val monitor = nw_path_monitor_create()

    init {
        nw_path_monitor_set_update_handler(monitor) { path ->
            _status.value =
                if (path != null && nw_path_get_status(path) == nw_path_status_satisfied) {
                    ConnectivityStatus.ONLINE
                } else {
                    ConnectivityStatus.OFFLINE
                }
        }
        nw_path_monitor_set_queue(monitor, dispatch_queue_create("com.stepanok.beacon.connectivity", null))
        nw_path_monitor_start(monitor)
    }
}

actual fun createConnectivityObserver(): ConnectivityObserver = IosConnectivityObserver()
