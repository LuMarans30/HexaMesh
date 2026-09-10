package com.lumarans30.hexamesh.platform

import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager

/**
 * Keeps the CPU, Wi-Fi radio and multicast socket alive while the node serves.
 */
class LockManager(context: Context) : Locks {
    private val app = context.applicationContext

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    @Synchronized
    override fun acquire() {
        if (wakeLock == null) {
            val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock =
                pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "hexamesh::inference").apply {
                    setReferenceCounted(false)
                    acquire()
                }
        }
        val wm = app.getSystemService(Context.WIFI_SERVICE) as WifiManager
        if (wifiLock == null) {
            wifiLock =
                wm.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "hexamesh::wifi")
                    .apply {
                        setReferenceCounted(false)
                        acquire()
                    }
        }
        if (multicastLock == null) {
            multicastLock =
                wm.createMulticastLock("hexamesh::mdns").apply {
                    setReferenceCounted(false)
                    acquire()
                }
        }
    }

    @Synchronized
    override fun release() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        runCatching { if (wifiLock?.isHeld == true) wifiLock?.release() }
        runCatching { if (multicastLock?.isHeld == true) multicastLock?.release() }
        wakeLock = null
        wifiLock = null
        multicastLock = null
    }
}
