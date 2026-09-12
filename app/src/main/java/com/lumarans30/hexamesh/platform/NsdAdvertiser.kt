package com.lumarans30.hexamesh.platform

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.lumarans30.hexamesh.mesh.MESH_SERVICE_TYPE
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds

/**
 * Advertises this device's RPC endpoint over mDNS, emitting the name the network
 * assigned. NSD has no in-place TXT update, so changed [attributes] unregister and
 * re-register the record, which briefly drops it from peer discovery.
 */
class NsdAdvertiser(
    private val context: Context,
) {
    fun advertise(
        name: String,
        port: Int,
        attributes: suspend () -> Map<String, ByteArray>,
    ): Flow<String?> =
        callbackFlow {
            val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
            val unregistered = Channel<Unit>(Channel.CONFLATED)
            val retry = Channel<Unit>(Channel.CONFLATED)
            val registered = AtomicBoolean(false)
            lateinit var listener: NsdManager.RegistrationListener

            fun register(attrs: Map<String, ByteArray>) {
                val info =
                    NsdServiceInfo().apply {
                        serviceName = name
                        serviceType = MESH_SERVICE_TYPE
                        this.port = port
                        attrs.forEach { (key, value) -> setAttribute(key, value) }
                    }

                listener =
                    object : NsdManager.RegistrationListener {
                        override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                            Log.i(TAG, "Registered as ${serviceInfo.serviceName}")
                            registered.set(true)
                            trySend(serviceInfo.serviceName)
                        }

                        override fun onRegistrationFailed(
                            serviceInfo: NsdServiceInfo,
                            errorCode: Int,
                        ) {
                            Log.w(TAG, "Registration failed: $errorCode")
                            registered.set(false)
                            retry.trySend(Unit)
                            trySend(null)
                        }

                        override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                            registered.set(false)
                            unregistered.trySend(Unit)
                        }

                        override fun onUnregistrationFailed(
                            serviceInfo: NsdServiceInfo,
                            errorCode: Int,
                        ) {
                            Log.w(TAG, "Unregistration failed: $errorCode")
                            unregistered.trySend(Unit)
                        }
                    }

                nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
            }

            var current = attributes()
            register(current)

            launch {
                while (isActive) {
                    val failed =
                        withTimeoutOrNull(REFRESH_INTERVAL_MS.milliseconds) { retry.receive() } != null
                    if (failed) delay(RETRY_INTERVAL_MS.milliseconds)

                    val next = attributes()
                    if (registered.get() && next == current) continue

                    if (registered.get()) {
                        runCatching { nsd.unregisterService(listener) }
                        withTimeoutOrNull(UNREGISTER_TIMEOUT_MS.milliseconds) { unregistered.receive() }
                    }
                    current = next
                    register(next)
                }
            }

            awaitClose { runCatching { nsd.unregisterService(listener) } }
        }

    private companion object {
        const val TAG = "HexaNsd"
        const val REFRESH_INTERVAL_MS = 30_000L
        const val RETRY_INTERVAL_MS = 3_000L
        const val UNREGISTER_TIMEOUT_MS = 3_000L
    }
}
