package com.lumarans30.hexamesh.node

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadRequestTest {
    @Test
    fun `accepts a plain https gguf link`() {
        val request = parseDownloadRequest("https://example.com/models/tiny.gguf")

        assertEquals("https://example.com/models/tiny.gguf", request?.url)
        assertEquals("tiny.gguf", request?.fileName)
    }

    @Test
    fun `accepts plain http`() {
        assertEquals("tiny.gguf", parseDownloadRequest("http://example.com/tiny.gguf")?.fileName)
    }

    @Test
    fun `keeps the query string but strips it from the file name`() {
        val request = parseDownloadRequest("https://example.com/tiny.gguf?download=true")

        assertEquals("https://example.com/tiny.gguf?download=true", request?.url)
        assertEquals("tiny.gguf", request?.fileName)
    }

    @Test
    fun `rewrites hugging face blob links to resolve`() {
        val request =
            parseDownloadRequest(
                "https://huggingface.co/Qwen/Qwen2.5-0.5B-GGUF/blob/main/qwen-q8_0.gguf",
            )

        assertEquals(
            "https://huggingface.co/Qwen/Qwen2.5-0.5B-GGUF/resolve/main/qwen-q8_0.gguf",
            request?.url,
        )
        assertEquals("qwen-q8_0.gguf", request?.fileName)
    }

    @Test
    fun `keeps hugging face resolve links unchanged`() {
        val input = "https://huggingface.co/Qwen/Qwen2.5-0.5B-GGUF/resolve/main/qwen-q8_0.gguf"

        assertEquals(input, parseDownloadRequest(input)?.url)
    }

    @Test
    fun `decodes percent-encoded file names`() {
        val request = parseDownloadRequest("https://example.com/my%20model.gguf")

        assertEquals("my model.gguf", request?.fileName)
    }

    @Test
    fun `is case-insensitive about the extension`() {
        assertEquals("tiny.GGUF", parseDownloadRequest("https://example.com/tiny.GGUF")?.fileName)
    }

    @Test
    fun `rejects links that are not gguf files`() {
        assertNull(parseDownloadRequest("https://example.com/model.bin"))
        assertNull(parseDownloadRequest("https://huggingface.co/Qwen/Qwen2.5-0.5B-GGUF"))
    }

    @Test
    fun `rejects unsupported schemes`() {
        assertNull(parseDownloadRequest("ftp://example.com/tiny.gguf"))
        assertNull(parseDownloadRequest("file:///tmp/tiny.gguf"))
        assertNull(parseDownloadRequest("not a url"))
    }

    @Test
    fun `rejects blank input`() {
        assertNull(parseDownloadRequest(""))
        assertNull(parseDownloadRequest("   "))
    }
}
