package com.lumarans30.hexamesh.mesh

import java.net.ServerSocket
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
