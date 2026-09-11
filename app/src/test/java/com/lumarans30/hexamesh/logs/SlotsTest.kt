package com.lumarans30.hexamesh.logs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SlotsTest {

    @Test
    fun `sums decoded tokens across processing slots`() {
        val json =
            """[{"id":0,"n_ctx":231168,"is_processing":false},""" +
                """{"id":1,"n_decoded":42,"is_processing":true},""" +
                """{"id":2,"n_decoded":8,"is_processing":true}]"""

        assertEquals(50, parseDecodedTokens(json))
    }

    @Test
    fun `idle slots yield null`() {
        val json = """[{"id":0,"is_processing":false},{"id":1,"is_processing":false}]"""

        assertNull(parseDecodedTokens(json))
    }

    @Test
    fun `computes a live rate from two samples`() {
        assertEquals(30.0, tokensPerSecond(130, 100, 0, 1000)!!, 0.001)
    }

    @Test
    fun `idle reports zero`() {
        assertEquals(0.0, tokensPerSecond(null, 100, 0, 1000)!!, 0.001)
    }

    @Test
    fun `the first sample of a generation has no rate yet`() {
        assertNull(tokensPerSecond(100, null, 0, 1000))
    }
}
