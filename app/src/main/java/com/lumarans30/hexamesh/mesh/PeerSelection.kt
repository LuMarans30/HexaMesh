package com.lumarans30.hexamesh.mesh

/** Peers not seen within this window are treated as gone. */
const val PEER_TTL_MS = 30_000L

/** llama.cpp's GGML_RPC_MAX_SERVERS; more endpoints than this are ignored. */
const val MAX_RPC_SERVERS = 16

/**
 * Ranks peers best-first by free memory, then latency, then id (stable on ties).
 * Unknown readings sort last.
 */
fun rankPeers(peers: List<PeerNode>): List<PeerNode> =
    peers.sortedWith(
        compareByDescending<PeerNode> { it.stats.freeMemoryBytes ?: -1L }
            .thenBy { it.stats.latencyMs ?: Long.MAX_VALUE }
            .thenBy { it.id },
    )

/**
 * Pinging and freshness gate for `--rpc` selection. Manual peers never expire.
 */
fun isReachable(
    peer: PeerNode,
    nowMs: Long,
    maxAgeMs: Long = PEER_TTL_MS,
): Boolean {
    if (peer.stats.inUse) return true
    if (peer.stats.latencyMs == null) return false
    if (peer.source == PeerSource.Manual) return true
    val seen = peer.stats.lastSeenAtMs ?: return false
    return nowMs - seen in 0..maxAgeMs
}
