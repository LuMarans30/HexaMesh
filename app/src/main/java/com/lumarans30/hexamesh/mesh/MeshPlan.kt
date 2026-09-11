package com.lumarans30.hexamesh.mesh

/** Peers not seen within this window are treated as gone. */
const val PEER_TTL_MS = 30_000L

/** llama.cpp's GGML_RPC_MAX_SERVERS; more endpoints than this are ignored. */
const val MAX_RPC_SERVERS = 16

/** Layers offloaded to one peer. */
data class LayerAssignment(val peer: PeerNode, val layers: Int)

/**
 * How the offloaded layers are split between this device and its peers. [localLayers]
 * counts the layers computed locally; each assignment is rendered as one RPC device
 * in llama-server via [rpcEndpoints].
 */
data class MeshPlan(
    val localLayers: Int,
    val assignments: List<LayerAssignment> = emptyList(),
) {
    val remoteLayers: Int
        get() = assignments.sumOf { it.layers }

    val totalLayers: Int
        get() = localLayers + remoteLayers

    val isMeshed: Boolean
        get() = assignments.isNotEmpty()

    /** Value for llama-server's `--rpc` flag, or null when running alone. */
    val rpcEndpoints: String?
        get() =
            assignments
                .takeIf { it.isNotEmpty() }
                ?.joinToString(",") { "${it.peer.host}:${it.peer.port}" }
}

/**
 * Ranks peers best-first by the coordinator's policy: most free memory, then
 * lowest latency, then id so the order is stable across equal candidates.
 * Unknown readings sort last.
 */
fun rankPeers(peers: List<PeerNode>): List<PeerNode> =
    peers.sortedWith(
        compareByDescending<PeerNode> { it.stats.freeMemoryBytes ?: -1L }
            .thenBy { it.stats.latencyMs ?: Long.MAX_VALUE }
            .thenBy { it.id }
    )

/**
 * A peer is reachable once its RPC port answered a ping and the record is still
 * fresh. Manual peers never expire (there is no discovery to refresh them).
 * This is the gate for `--rpc` selection, where capacity is not yet known.
 */
fun isReachable(peer: PeerNode, nowMs: Long, maxAgeMs: Long = PEER_TTL_MS): Boolean {
    if (peer.stats.latencyMs == null) return false
    if (peer.source == PeerSource.Manual) return true
    val seen = peer.stats.lastSeenAtMs ?: return false
    return nowMs - seen in 0..maxAgeMs
}

/**
 * A peer is plan-ready once it has reported free memory and was seen recently.
 * Manual peers never expire (there is no discovery to refresh them); latency is
 * optional, since ranking prefers a measured peer but a missing ping must not
 * drop an otherwise healthy node.
 */
fun isEligible(peer: PeerNode, nowMs: Long, maxAgeMs: Long = PEER_TTL_MS): Boolean {
    val free = peer.stats.freeMemoryBytes ?: return false
    if (free <= 0) return false
    if (peer.source == PeerSource.Manual) return true
    val seen = peer.stats.lastSeenAtMs ?: return false
    return nowMs - seen in 0..maxAgeMs
}

/**
 * Splits [totalLayers] across the local device and the eligible [peers], weighted
 * by free memory. Ineligible peers are ignored and the local device absorbs the
 * rounding remainder, so the plan always sums to [totalLayers] and degrades to a
 * local-only plan when no peer qualifies.
 */
fun planLayers(
    totalLayers: Int,
    localFreeMemoryBytes: Long,
    peers: List<PeerNode>,
    nowMs: Long,
    peerTtlMs: Long = PEER_TTL_MS,
): MeshPlan {
    if (totalLayers <= 0) return MeshPlan(0)

    val eligible = rankPeers(peers).filter { isEligible(it, nowMs, peerTtlMs) }
    if (eligible.isEmpty()) return MeshPlan(totalLayers)

    // Local joins the apportionment so every offloaded layer is accounted for.
    val weights = mutableListOf(localFreeMemoryBytes.coerceAtLeast(0L))
    eligible.forEach { weights += it.stats.freeMemoryBytes ?: 0L }

    val counts = apportion(weights, totalLayers)
    val assignments =
        eligible.mapIndexedNotNull { index, peer ->
            counts[index + 1].takeIf { it > 0 }?.let { LayerAssignment(peer, it) }
        }

    return MeshPlan(localLayers = counts.first(), assignments = assignments)
}

/**
 * Largest-remainder apportionment: floors each exact share, then hands the
 * leftover layers to the largest fractional parts. The stable sort keeps the
 * local device (index 0) ahead of peers on ties.
 */
private fun apportion(weights: List<Long>, total: Int): IntArray {
    val totalWeight = weights.sum()
    if (totalWeight <= 0L) return IntArray(weights.size).also { it[0] = total }

    val exact = weights.map { it.toDouble() * total / totalWeight }
    val counts = IntArray(weights.size) { exact[it].toInt() }

    val leftover = total - counts.sum()
    val order = weights.indices.sortedByDescending { exact[it] - counts[it] }
    for (i in 0 until leftover) counts[order[i]]++

    return counts
}
