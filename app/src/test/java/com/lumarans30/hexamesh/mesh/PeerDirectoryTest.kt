package com.lumarans30.hexamesh.mesh

import org.junit.Assert.assertEquals
import org.junit.Test

private fun node(id: String, host: String, port: Int, source: PeerSource) =
    PeerNode(id = id, name = id, host = host, port = port, source = source)

class PeerDirectoryTest {

    @Test
    fun `a manual entry overrides the discovered peer at the same endpoint`() {
        val discovered = listOf(node("auto", "10.0.0.2", 50052, PeerSource.Discovered))
        val manual = listOf(node("Living room", "10.0.0.2", 50052, PeerSource.Manual))

        val merged = mergePeers(discovered, manual)

        assertEquals(1, merged.size)
        assertEquals("Living room", merged.single().name)
        assertEquals(PeerSource.Manual, merged.single().source)
    }

    @Test
    fun `manual peers come first and discovered-only peers follow`() {
        val discovered =
            listOf(
                node("a", "10.0.0.2", 50052, PeerSource.Discovered),
                node("b", "10.0.0.3", 50052, PeerSource.Discovered),
            )
        val manual = listOf(node("manual", "10.0.0.9", 50052, PeerSource.Manual))

        val merged = mergePeers(discovered, manual)

        assertEquals(listOf("manual", "a", "b"), merged.map { it.id })
    }

    @Test
    fun `the same host on a different port is a distinct peer`() {
        val discovered = listOf(node("one", "10.0.0.2", 50052, PeerSource.Discovered))
        val manual = listOf(node("two", "10.0.0.2", 50053, PeerSource.Manual))

        assertEquals(2, mergePeers(discovered, manual).size)
    }

    @Test
    fun `this device's own advertisement is filtered out`() {
        val peers =
            listOf(
                node("self", "10.0.0.2", 50052, PeerSource.Discovered),
                node("other", "10.0.0.3", 50052, PeerSource.Discovered),
            )

        assertEquals(listOf("other"), withoutSelf(peers, "self").map { it.id })
        assertEquals(peers, withoutSelf(peers, null))
    }
}
