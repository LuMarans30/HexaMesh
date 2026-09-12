package com.lumarans30.hexamesh.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val NOW = 1_000_000L
private const val GB = 1_000_000_000L

private fun peer(
    id: String,
    freeGb: Long?,
    latencyMs: Long? = 10L,
    seenAgoMs: Long = 0L,
    port: Int = 50052,
) = PeerNode(
    id = id,
    name = id,
    host = "$id.local",
    port = port,
    stats =
        PeerStats(
            freeMemoryBytes = freeGb?.times(GB),
            latencyMs = latencyMs,
            lastSeenAtMs = NOW - seenAgoMs,
        ),
)

class PeerSelectionTest {
    @Test
    fun `ranking prefers memory then latency then id`() {
        val ranked =
            rankPeers(
                listOf(
                    peer("slow", freeGb = 8, latencyMs = 40),
                    peer("fast", freeGb = 8, latencyMs = 5),
                    peer("rich", freeGb = 16, latencyMs = 80),
                ),
            )

        assertEquals(listOf("rich", "fast", "slow"), ranked.map { it.id })
    }

    @Test
    fun `peers without memory sort behind peers that report some`() {
        val ranked = rankPeers(listOf(peer("unknown", freeGb = null), peer("known", freeGb = 1)))

        assertEquals(listOf("known", "unknown"), ranked.map { it.id })
    }

    @Test
    fun `reachability needs a successful ping and a fresh record`() {
        val pinged = peer("pinged", freeGb = 4, latencyMs = 7)
        val unpinged = peer("unpinged", freeGb = 4, latencyMs = null)
        val stale = peer("stale", freeGb = 4, latencyMs = 7, seenAgoMs = PEER_TTL_MS + 1)

        assertTrue(isReachable(pinged, NOW))
        assertFalse(isReachable(unpinged, NOW))
        assertFalse(isReachable(stale, NOW))
    }

    @Test
    fun `manual peers are reachable once pinged, without a discovery stamp`() {
        val manual =
            PeerNode(
                id = "manual:10.0.0.9:50052",
                name = "10.0.0.9",
                host = "10.0.0.9",
                port = 50052,
                source = PeerSource.Manual,
                stats = PeerStats(latencyMs = 12),
            )

        assertTrue(isReachable(manual, NOW))
    }
}
