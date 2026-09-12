package com.lumarans30.hexamesh.node

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelsClientTest {
    @Test
    fun `reload targets the local router with the reload flag`() {
        assertEquals("http://127.0.0.1:8080/models?reload=1", reloadUrl(8080))
        assertEquals("http://127.0.0.1:9090/models?reload=1", reloadUrl(9090))
    }
}
