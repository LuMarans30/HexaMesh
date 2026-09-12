package com.lumarans30.hexamesh.node

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelsClientTest {
    @Test
    fun `reload targets the local router with the reload flag`() {
        assertEquals("http://127.0.0.1:8080/models?reload=1", reloadUrl(8080))
        assertEquals("http://127.0.0.1:9090/models?reload=1", reloadUrl(9090))
    }

    @Test
    fun `models targets the local router`() {
        assertEquals("http://127.0.0.1:8080/models", modelsUrl(8080))
    }

    @Test
    fun `finds loaded models among the router list`() {
        val json =
            """{"data":[${entry("a", "unloaded")},${entry("b", "loaded")},""" +
                """${entry("c", "downloaded")}],"object":"list"}"""

        assertEquals(listOf("b"), parseLoadedModelIds(json))
    }

    @Test
    fun `no loaded model yields an empty list`() {
        val json = """{"data":[${entry("a", "unloaded")}],"object":"list"}"""

        assertEquals(emptyList<String>(), parseLoadedModelIds(json))
    }

    private fun entry(
        id: String,
        status: String,
    ) = """{"id":"$id","aliases":[],"tags":["chat"],"object":"model","owned_by":"llamacpp",""" +
        """"created":1,"status":{"value":"$status","args":["--host","127.0.0.1"]},""" +
        """"architecture":{"input_modalities":["text"]},"can_remove":false}"""
}
