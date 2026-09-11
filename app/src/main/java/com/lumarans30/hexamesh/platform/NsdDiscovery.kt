package com.lumarans30.hexamesh.platform

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.lumarans30.hexamesh.mesh.MESH_SERVICE_TYPE
import com.lumarans30.hexamesh.mesh.PeerNode
import com.lumarans30.hexamesh.mesh.discoveredPeer
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Browses the LAN for mesh peers over mDNS.
 *
 * Every found service gets a [NsdManager.ServiceInfoCallback], which resolves it
 * and keeps pushing updates (so TXT changes, like a refreshed memory figure,
 * reach us). The flow emits the whole set on every change. Collect it only while
 * the tab that shows peers is visible — it unregisters on cancel.
 */
class NsdDiscovery(private val context: Context) {

    fun discover(): Flow<List<PeerNode>> = callbackFlow {
        val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        val executor = context.mainExecutor
        val peers = LinkedHashMap<String, PeerNode>()
        val callbacks = LinkedHashMap<String, NsdManager.ServiceInfoCallback>()

        fun publish() {
            trySend(peers.values.toList())
        }

        fun register(service: NsdServiceInfo) {
            val name = service.serviceName
            if (callbacks.containsKey(name)) return

            val callback =
                object : NsdManager.ServiceInfoCallback {
                    override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                        Log.w(TAG, "Service info callback failed for $name: $errorCode")
                        callbacks.remove(name)
                    }

                    override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                        val host = serviceInfo.hostAddresses.firstOrNull()?.hostAddress ?: return
                        val peer =
                            discoveredPeer(
                                serviceName = serviceInfo.serviceName,
                                host = host,
                                port = serviceInfo.port,
                                attributes = serviceInfo.attributes,
                                nowMs = System.currentTimeMillis(),
                            )
                        Log.i(
                            TAG,
                            "Discovered ${peer.name} at ${peer.endpoint}, " +
                                "free=${peer.stats.freeMemoryBytes}",
                        )
                        peers[serviceInfo.serviceName] = peer
                        publish()
                    }

                    override fun onServiceLost() {
                        callbacks.remove(name)
                        peers.remove(name)
                        publish()
                    }

                    override fun onServiceInfoCallbackUnregistered() {
                        callbacks.remove(name)
                    }
                }

            callbacks[name] = callback
            runCatching { nsd.registerServiceInfoCallback(service, executor, callback) }
                .onFailure {
                    callbacks.remove(name)
                    Log.w(TAG, "Could not watch $name: $it")
                }
        }

        val listener =
            object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) = Unit

                override fun onServiceFound(serviceInfo: NsdServiceInfo) = register(serviceInfo)

                override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                    peers.remove(serviceInfo.serviceName)
                    callbacks.remove(serviceInfo.serviceName)?.let {
                        runCatching { nsd.unregisterServiceInfoCallback(it) }
                    }
                    publish()
                }

                override fun onDiscoveryStopped(serviceType: String) = Unit

                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    Log.w(TAG, "Discovery failed to start: $errorCode")
                    close(IllegalStateException("NSD discovery failed: $errorCode"))
                }

                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                    Log.w(TAG, "Discovery failed to stop: $errorCode")
                }
            }

        // Emit an initial empty set so downstream `combine` fires even when no
        // service is ever found (otherwise manual peers would stay hidden).
        publish()

        nsd.discoverServices(MESH_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)

        awaitClose {
            runCatching { nsd.stopServiceDiscovery(listener) }
            callbacks.values.forEach { runCatching { nsd.unregisterServiceInfoCallback(it) } }
        }
    }

    private companion object {
        const val TAG = "HexaNsd"
    }
}
