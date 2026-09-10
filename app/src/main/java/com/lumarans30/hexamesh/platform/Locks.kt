package com.lumarans30.hexamesh.platform

/**
 * Holds the wake/Wi-Fi/multicast locks the node needs while it serves.
 */
interface Locks {
    fun acquire()

    fun release()
}
