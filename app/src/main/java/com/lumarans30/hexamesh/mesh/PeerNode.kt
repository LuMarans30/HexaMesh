package com.lumarans30.hexamesh.mesh

/** Manual peers survive discovery outages. */
enum class PeerSource { Discovered, Manual }

/**
 * A llama.cpp RPC endpoint. [id] stays stable across discovery updates so UI
 * state and stats follow the same node.
 */
data class PeerNode(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val source: PeerSource = PeerSource.Discovered,
    val stats: PeerStats = PeerStats(),
) {
    val endpoint: String
        get() = "$host:$port"
}

/** Last measurements for a peer; null means unknown, which is not the same as zero. */
data class PeerStats(
    val freeMemoryBytes: Long? = null,
    val totalMemoryBytes: Long? = null,
    val latencyMs: Long? = null,
    val lastSeenAtMs: Long? = null,
)
