package com.lumarans30.hexamesh.mesh

/** How a peer entered the directory. Manual peers survive discovery outages. */
enum class PeerSource { Discovered, Manual }

/**
 * A compute node that can serve a share of a model over llama.cpp's RPC backend.
 * [host] and [port] address its `ggml-rpc-server`; [id] stays stable across
 * discovery updates so UI state and stats follow the same node.
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

/**
 * What the coordinator last measured about a peer. Every field is nullable:
 * "unknown" and "zero" mean different things, and a peer is only planned for
 * once a memory reading exists.
 */
data class PeerStats(
    val freeMemoryBytes: Long? = null,
    val totalMemoryBytes: Long? = null,
    val latencyMs: Long? = null,
    val lastSeenAtMs: Long? = null,
)
