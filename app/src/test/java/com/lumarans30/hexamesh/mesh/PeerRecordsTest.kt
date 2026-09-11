package com.lumarans30.hexamesh.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PeerRecordsTest {

    @Test
    fun `a resolved record maps to a discovered peer with txt stats`() {
        val peer =
            discoveredPeer(
                serviceName = "HexaMesh-phone",
                host = "192.168.1.42",
                port = 50052,
                nowMs = 1000L,
                attributes =
                    mapOf(
                        PeerTxt.FREE_MEMORY to "3000000000".toByteArray(),
                        PeerTxt.TOTAL_MEMORY to "8000000000".toByteArray(),
                    ),
            )

        assertEquals("HexaMesh-phone", peer.id)
        assertEquals("192.168.1.42", peer.host)
        assertEquals(50052, peer.port)
        assertEquals(PeerSource.Discovered, peer.source)
        assertEquals(3_000_000_000L, peer.stats.freeMemoryBytes)
        assertEquals(8_000_000_000L, peer.stats.totalMemoryBytes)
        assertEquals(1000L, peer.stats.lastSeenAtMs)
    }

    @Test
    fun `missing or malformed txt stats stay unknown`() {
        val peer =
            discoveredPeer(
                serviceName = "phone",
                host = "10.0.0.2",
                port = 50052,
                nowMs = 1L,
                attributes = mapOf(PeerTxt.FREE_MEMORY to "lots".toByteArray()),
            )

        assertNull(peer.stats.freeMemoryBytes)
        assertNull(peer.stats.totalMemoryBytes)
    }

    @Test
    fun `fallback entries accept host, host port, comments and blanks`() {
        val text =
            """
            # office phones
            192.168.1.42

            192.168.1.43:50053   # spare
            """.trimIndent()

        val peers = parseFallbackPeers(text)

        assertEquals(2, peers.size)
        assertEquals("manual:192.168.1.42:50052", peers[0].id)
        assertEquals(50052, peers[0].port)
        assertEquals(PeerSource.Manual, peers[0].source)
        assertEquals(50053, peers[1].port)
    }

    @Test
    fun `fallback drops entries with an invalid port`() {
        assertEquals(emptyList<PeerNode>(), parseFallbackPeers("10.0.0.2:0\n10.0.0.3:70000"))
    }

    @Test
    fun `a bracketed ipv6 fallback keeps the address intact`() {
        val peer = parseFallbackPeers("[fe80::1]:50052").single()

        assertEquals("fe80::1", peer.host)
        assertEquals(50052, peer.port)
    }

    @Test
    fun `a bare ipv6 address is rejected`() {
        assertEquals(emptyList<PeerNode>(), parseFallbackPeers("fe80::1"))
    }

    @Test
    fun `the advertised service name is derived from the model`() {
        assertEquals("HexaMesh-Pixel-9-Pro", meshServiceName("Pixel 9 Pro"))
        assertEquals("HexaMesh-SM-S928B", meshServiceName("SM-S928B"))
        assertEquals("HexaMesh", meshServiceName("   "))
    }

    @Test
    fun `advertised memory round-trips through discovery`() {
        val peer = discoveredPeer("phone", "10.0.0.2", 50052, 0L, memoryAttributes(3L, 9L))

        assertEquals(3L, peer.stats.freeMemoryBytes)
        assertEquals(9L, peer.stats.totalMemoryBytes)
    }
}
