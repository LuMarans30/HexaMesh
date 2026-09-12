package com.lumarans30.hexamesh.platform

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.lumarans30.hexamesh.mesh.MESH_SERVICE_TYPE
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Advertises this device's RPC endpoint over mDNS, emitting the name the network assigned. */
class NsdAdvertiser(private val context: Context) {

    fun advertise(
        name: String,
        port: Int,
        attributes: Map<String, ByteArray> = emptyMap(),
    ): Flow<String?> = callbackFlow {
        val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        val info =
            NsdServiceInfo().apply {
                serviceName = name
                serviceType = MESH_SERVICE_TYPE
                this.port = port
            }
        attributes.forEach { (key, value) -> info.setAttribute(key, value) }

        val listener =
            object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                    Log.i(TAG, "Registered as ${serviceInfo.serviceName}")
                    trySend(serviceInfo.serviceName)
                }

                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.w(TAG, "Registration failed: $errorCode")
                    trySend(null)
                }

                override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                    trySend(null)
                }

                override fun onUnregistrationFailed(
                    serviceInfo: NsdServiceInfo,
                    errorCode: Int,
                ) {
                    Log.w(TAG, "Unregistration failed: $errorCode")
                }
            }

        nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
        awaitClose { runCatching { nsd.unregisterService(listener) } }
    }

    private companion object {
        const val TAG = "HexaNsd"
    }
}
