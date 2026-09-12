package com.lumarans30.hexamesh.mesh

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket

class TcpPingTest {
    @Test
    fun `reports a latency for a listening socket`() {
        ServerSocket(0).use { server ->
            val latency = tcpLatencyMs("127.0.0.1", server.localPort)

            assertNotNull(latency)
            assertTrue(latency!! >= 0)
        }
    }

    @Test
    fun `returns null when nothing is listening`() {
        val port = ServerSocket(0).use { it.localPort }

        assertNull(tcpLatencyMs("127.0.0.1", port, timeoutMs = 200))
    }

    @Test
    fun `batch probing keys results by endpoint`() =
        runBlocking {
            ServerSocket(0).use { server ->
                val live = peer("live", "127.0.0.1", server.localPort)
                val dead = peer("dead", "127.0.0.1", 1)

                val latencies = tcpLatencies(listOf(live, dead))

                assertNotNull(latencies[live.endpoint])
                assertNull(latencies[dead.endpoint])
            }
        }

    private fun peer(
        id: String,
        host: String,
        port: Int,
    ) = PeerNode(id = id, name = id, host = host, port = port)
}
