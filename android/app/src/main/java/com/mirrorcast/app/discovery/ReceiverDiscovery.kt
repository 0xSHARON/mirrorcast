package com.mirrorcast.app.discovery

import android.content.Context
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Socket

data class DiscoveredReceiver(
    val name: String,
    val host: String,
    val port: Int,
    val pin: String? = null
)

class ReceiverDiscovery(context: Context) {
    private val TAG = "ReceiverDiscovery"
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val SERVICE_TYPE = "_mirrorcast._tcp."

    private val _receivers = MutableStateFlow<List<DiscoveredReceiver>>(emptyList())
    val receivers: StateFlow<List<DiscoveredReceiver>> = _receivers.asStateFlow()

    private val _isUsbAvailable = MutableStateFlow(false)
    val isUsbAvailable: StateFlow<Boolean> = _isUsbAvailable.asStateFlow()

    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var isDiscovering = false
    private var discoveryJob: Job? = null
    private val discoveryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun startDiscovery() {
        if (isDiscovering) return
        isDiscovering = true

        startNsdDiscovery()
        startUdpBroadcastDiscovery()
        checkUsbAvailability()
    }

    private fun startNsdDiscovery() {
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "mDNS Service discovery started")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "mDNS Service found: ${serviceInfo.serviceName}")
                if (serviceInfo.serviceType.contains("mirrorcast")) {
                    resolveService(serviceInfo)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "mDNS Service lost: ${serviceInfo.serviceName}")
                removeReceiver(serviceInfo.serviceName)
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "mDNS Discovery stopped: $serviceType")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "mDNS Start discovery failed: $errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "mDNS Stop discovery failed: $errorCode")
            }
        }

        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting NSD: ${e.message}")
        }
    }

    private fun resolveService(serviceInfo: NsdServiceInfo) {
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "mDNS Resolve failed for ${serviceInfo.serviceName}: $errorCode")
            }

            override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                val host = resolvedInfo.host?.hostAddress ?: return
                val port = resolvedInfo.port
                val receiver = DiscoveredReceiver(
                    name = resolvedInfo.serviceName.removePrefix("MirrorCast-"),
                    host = host,
                    port = port
                )
                addReceiver(receiver)
            }
        }

        try {
            nsdManager.resolveService(serviceInfo, resolveListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve mDNS service: ${e.message}")
        }
    }

    private fun startUdpBroadcastDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = discoveryScope.launch {
            while (isActive && isDiscovering) {
                try {
                    val socket = DatagramSocket()
                    socket.broadcast = true
                    socket.soTimeout = 1500

                    val msg = "MIRRORCAST_DISCOVER".toByteArray()
                    val packet = DatagramPacket(
                        msg,
                        msg.size,
                        InetAddress.getByName("255.255.255.255"),
                        8091
                    )
                    socket.send(packet)

                    // Receive responses
                    val rxBuffer = ByteArray(512)
                    val rxPacket = DatagramPacket(rxBuffer, rxBuffer.size)

                    val startTime = System.currentTimeMillis()
                    while (System.currentTimeMillis() - startTime < 1500) {
                        try {
                            socket.receive(rxPacket)
                            val response = String(rxPacket.data, 0, rxPacket.length)
                            if (response.startsWith("MIRRORCAST_OFFER|")) {
                                val parts = response.split("|")
                                if (parts.size >= 3) {
                                    val name = parts[1]
                                    val port = parts[2].toIntOrNull() ?: 8090
                                    val host = rxPacket.address.hostAddress ?: continue
                                    addReceiver(DiscoveredReceiver(name = name, host = host, port = port))
                                }
                            }
                        } catch (e: Exception) {
                            break // timeout reached
                        }
                    }
                    socket.close()
                } catch (e: Exception) {
                    Log.d(TAG, "UDP broadcast scan note: ${e.message}")
                }

                delay(2000)
            }
        }
    }

    fun checkUsbAvailability() {
        discoveryScope.launch {
            try {
                val s = Socket("127.0.0.1", 8090)
                s.close()
                _isUsbAvailable.value = true
                addReceiver(DiscoveredReceiver(name = "Windows PC (USB)", host = "127.0.0.1", port = 8090))
            } catch (e: Exception) {
                _isUsbAvailable.value = false
            }
        }
    }

    @Synchronized
    private fun addReceiver(receiver: DiscoveredReceiver) {
        val current = _receivers.value.toMutableList()
        current.removeAll { it.name == receiver.name || (it.host == receiver.host && it.port == receiver.port) }
        current.add(0, receiver)
        _receivers.value = current
    }

    @Synchronized
    private fun removeReceiver(name: String) {
        val current = _receivers.value.toMutableList()
        current.removeAll { it.name == name }
        _receivers.value = current
    }

    fun stopDiscovery() {
        isDiscovering = false
        discoveryJob?.cancel()
        discoveryJob = null
        try {
            discoveryListener?.let { nsdManager.stopServiceDiscovery(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping NSD: ${e.message}")
        } finally {
            discoveryListener = null
        }
    }

    companion object {
        fun parseQrUri(qrContent: String): DiscoveredReceiver? {
            return try {
                val uri = Uri.parse(qrContent)
                if (uri.scheme == "mirrorcast" && uri.host == "pair") {
                    val ip = uri.getQueryParameter("ip") ?: return null
                    val port = uri.getQueryParameter("port")?.toIntOrNull() ?: 8090
                    val pin = uri.getQueryParameter("pin")
                    val name = uri.getQueryParameter("name") ?: "Windows PC"
                    DiscoveredReceiver(name = name, host = ip, port = port, pin = pin)
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}
