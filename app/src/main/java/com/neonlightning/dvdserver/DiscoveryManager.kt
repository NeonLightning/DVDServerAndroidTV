package com.neonlightning.dvdserver

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log

class DiscoveryManager(context: Context, private val onServerFound: (String, String) -> Unit) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val serviceType = "_dvds._tcp."

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(regType: String) {
            Log.d("Discovery", "Service discovery started: $regType")
        }

        override fun onServiceFound(service: NsdServiceInfo) {
            Log.d("Discovery", "Service found: ${service.serviceName}")
            // The serviceName will be like "DVD MKV Server (hostname)"
            nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.e("Discovery", "Resolve failed for ${service.serviceName}: $errorCode")
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    val host = serviceInfo.host.hostAddress
                    val port = serviceInfo.port
                    val url = "http://$host:$port"
                    Log.d("Discovery", "Resolved ${serviceInfo.serviceName}: $url")
                    onServerFound(serviceInfo.serviceName, url)
                }
            })
        }

        override fun onServiceLost(service: NsdServiceInfo) {
            Log.d("Discovery", "Service lost: ${service.serviceName}")
        }

        override fun onDiscoveryStopped(regType: String) {
            Log.d("Discovery", "Discovery stopped")
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e("Discovery", "Start discovery failed: $errorCode")
            try { nsdManager.stopServiceDiscovery(this) } catch (e: Exception) {}
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e("Discovery", "Stop discovery failed: $errorCode")
            try { nsdManager.stopServiceDiscovery(this) } catch (e: Exception) {}
        }
    }

    fun start() {
        Log.d("Discovery", "Starting discovery for $serviceType")
        nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    fun stop() {
        try {
            nsdManager.stopServiceDiscovery(discoveryListener)
        } catch (e: Exception) {
            Log.e("Discovery", "Error stopping discovery", e)
        }
    }
}
