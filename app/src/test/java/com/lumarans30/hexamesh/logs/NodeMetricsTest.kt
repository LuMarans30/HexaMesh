package com.lumarans30.hexamesh.logs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NodeMetricsTest {

    @Test
    fun `parses the slot generation rate`() {
        val line =
            "0.20.340.241 I slot print_timing: id  3 | task 0 | n_gen =    100, " +
                "tg =  30.67 t/s, tg_3s =  30.98 t/s"

        assertEquals(30.67, parseTokensPerSecond(line)!!, 0.001)
    }

    @Test
    fun `returns null when there is no generation rate`() {
        assertNull(parseTokensPerSecond("0.13.086.789 I srv llama_server: listening on"))
        assertNull(parseTokensPerSecond("tg_3s =  30.98 t/s"))
    }
}
