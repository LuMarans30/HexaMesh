package com.lumarans30.hexamesh.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
) =
    PeerNode(
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

class MeshPlanTest {

    @Test
    fun `no peers leaves every layer local`() {
        val plan = planLayers(totalLayers = 33, localFreeMemoryBytes = 4 * GB, peers = emptyList(), nowMs = NOW)

        assertEquals(33, plan.localLayers)
        assertTrue(plan.assignments.isEmpty())
        assertFalse(plan.isMeshed)
        assertNull(plan.rpcEndpoints)
    }

    @Test
    fun `equal peers split the layers evenly with the local remainder`() {
        val peers = listOf(peer("a", freeGb = 4), peer("b", freeGb = 4))

        val plan = planLayers(totalLayers = 30, localFreeMemoryBytes = 4 * GB, peers = peers, nowMs = NOW)

        assertEquals(10, plan.localLayers)
        assertEquals(listOf(10, 10), plan.assignments.map { it.layers })
        assertEquals(30, plan.totalLayers)
    }

    @Test
    fun `a larger peer carries a larger share`() {
        val peers = listOf(peer("big", freeGb = 8), peer("small", freeGb = 2))

        val plan = planLayers(totalLayers = 30, localFreeMemoryBytes = 2 * GB, peers = peers, nowMs = NOW)

        val byId = plan.assignments.associate { it.peer.id to it.layers }
        assertTrue(byId.getValue("big") > byId.getValue("small"))
        assertEquals(30, plan.totalLayers)
    }

    @Test
    fun `stale peers are ignored`() {
        val peers = listOf(peer("gone", freeGb = 16, seenAgoMs = PEER_TTL_MS + 1))

        val plan = planLayers(totalLayers = 20, localFreeMemoryBytes = 4 * GB, peers = peers, nowMs = NOW)

        assertEquals(20, plan.localLayers)
        assertTrue(plan.assignments.isEmpty())
    }

    @Test
    fun `peers without a memory reading are ignored`() {
        val peers = listOf(peer("unknown", freeGb = null))

        val plan = planLayers(totalLayers = 20, localFreeMemoryBytes = 4 * GB, peers = peers, nowMs = NOW)

        assertTrue(plan.assignments.isEmpty())
    }

    @Test
    fun `peers with no measured latency are still eligible`() {
        val peers = listOf(peer("quiet", freeGb = 4, latencyMs = null))

        val plan = planLayers(totalLayers = 20, localFreeMemoryBytes = 4 * GB, peers = peers, nowMs = NOW)

        assertEquals(listOf(10), plan.assignments.map { it.layers })
    }

    @Test
    fun `the plan always sums to the requested layer count`() {
        val peers = listOf(peer("a", freeGb = 3), peer("b", freeGb = 5), peer("c", freeGb = 1))

        for (total in 0..64) {
            val plan = planLayers(total, localFreeMemoryBytes = 7 * GB, peers = peers, nowMs = NOW)
            assertEquals("total=$total", total, plan.totalLayers)
        }
    }

    @Test
    fun `endpoints are rendered for the rpc flag`() {
        val peers = listOf(peer("a", freeGb = 4, port = 50052), peer("b", freeGb = 4, port = 50053))

        val plan = planLayers(totalLayers = 20, localFreeMemoryBytes = 4 * GB, peers = peers, nowMs = NOW)

        assertEquals("a.local:50052,b.local:50053", plan.rpcEndpoints)
    }

    @Test
    fun `ranking prefers memory then latency then id`() {
        val ranked =
            rankPeers(
                listOf(
                    peer("slow", freeGb = 8, latencyMs = 40),
                    peer("fast", freeGb = 8, latencyMs = 5),
                    peer("rich", freeGb = 16, latencyMs = 80),
                )
            )

        assertEquals(listOf("rich", "fast", "slow"), ranked.map { it.id })
    }

    @Test
    fun `peers without memory sort behind peers that report some`() {
        val ranked = rankPeers(listOf(peer("unknown", freeGb = null), peer("known", freeGb = 1)))

        assertEquals(listOf("known", "unknown"), ranked.map { it.id })
    }

    @Test
    fun `manual peers never expire even without a last-seen stamp`() {
        val manual =
            PeerNode(
                id = "manual:10.0.0.9:50052",
                name = "10.0.0.9",
                host = "10.0.0.9",
                port = 50052,
                source = PeerSource.Manual,
                stats = PeerStats(freeMemoryBytes = 4 * GB),
            )

        assertTrue(isEligible(manual, NOW))
    }

    @Test
    fun `discovered peers need a fresh last-seen stamp`() {
        val stale = peer("gone", freeGb = 4, seenAgoMs = PEER_TTL_MS + 1)
        val fresh = peer("here", freeGb = 4)

        assertFalse(isEligible(stale, NOW))
        assertTrue(isEligible(fresh, NOW))
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
