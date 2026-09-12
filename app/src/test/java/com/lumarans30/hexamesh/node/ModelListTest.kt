package com.lumarans30.hexamesh.node

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelListTest {

    @Test
    fun `plain gguf files are loadable`() {
        assertTrue(isLoadableModel("Qwen3.5-0.8B-Q8_0.gguf"))
        assertTrue(isLoadableModel("Kimi-K2-Thinking-UD-IQ1_S-00001-of-00006.gguf"))
    }

    @Test
    fun `mmproj sidecars are not models`() {
        assertFalse(isLoadableModel("mmproj-BF16.gguf"))
        assertFalse(isLoadableModel("gemma-3-4b-it-mmproj-F16.gguf"))
    }

    @Test
    fun `draft sidecars are not models`() {
        assertFalse(isLoadableModel("mtp-draft.gguf"))
        assertFalse(isLoadableModel("dspark-x.gguf"))
        assertFalse(isLoadableModel("dflash-x.gguf"))
    }

    @Test
    fun `the extension is case-sensitive like llama cpp`() {
        assertFalse(isLoadableModel("MODEL.GGUF"))
    }

    @Test
    fun `non-gguf files are ignored`() {
        assertFalse(isLoadableModel("readme.txt"))
        assertFalse(isLoadableModel("model.safetensors"))
    }

    @Test
    fun `the id is the filename without the gguf extension`() {
        assertEquals("Qwen3.5-0.8B-Q8_0", Model("/m/a.gguf", "Qwen3.5-0.8B-Q8_0.gguf", 1L).id)
    }
}
