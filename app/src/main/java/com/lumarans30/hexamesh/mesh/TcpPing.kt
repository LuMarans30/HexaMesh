package com.lumarans30.hexamesh.mesh

import java.net.InetSocketAddress
import java.net.Socket

private const val NANOS_PER_MILLI = 1_000_000L

/**
 * Round-trip time to open a TCP connection to [host]:[port], in milliseconds, or
 * null when the peer refused or the connect timed out. This is the LAN latency
 * used to rank peers.
 */
fun tcpLatencyMs(host: String, port: Int, timeoutMs: Int = 500): Long? {
    val start = System.nanoTime()
    return runCatching {
        Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs) }
        (System.nanoTime() - start) / NANOS_PER_MILLI
    }.getOrNull()
}
