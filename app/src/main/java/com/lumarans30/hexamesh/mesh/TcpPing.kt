package com.lumarans30.hexamesh.mesh

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.net.InetSocketAddress
import java.net.Socket

private const val NANOS_PER_MILLI = 1_000_000L

/** Connect round-trip to [host]:[port] in ms, or null on refusal/timeout. */
fun tcpLatencyMs(
    host: String,
    port: Int,
    timeoutMs: Int = 500,
): Long? {
    val start = System.nanoTime()
    return runCatching {
        Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs) }
        (System.nanoTime() - start) / NANOS_PER_MILLI
    }.getOrNull()
}

/** Probes every peer concurrently, keyed by endpoint; null means unreachable. */
suspend fun tcpLatencies(peers: List<PeerNode>): Map<String, Long?> =
    coroutineScope {
        peers
            .map { peer ->
                async(Dispatchers.IO) { peer.endpoint to tcpLatencyMs(peer.host, peer.port) }
            }.awaitAll()
            .toMap()
    }
