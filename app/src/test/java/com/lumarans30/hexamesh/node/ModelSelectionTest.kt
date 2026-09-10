package com.lumarans30.hexamesh.node

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelSelectionTest {

    @Test
    fun `pinned model is preferred while it still exists`() {
        assertEquals(B, resolveSelected(listOf(A, B), B.path))
    }

    @Test
    fun `falls back to the first model when the pinned one is gone`() {
        assertEquals(A, resolveSelected(listOf(A, B), "/models/gone.gguf"))
    }

    @Test
    fun `falls back to the first model when nothing is pinned`() {
        assertEquals(A, resolveSelected(listOf(A, B), null))
    }

    @Test
    fun `no models resolves to null`() {
        assertNull(resolveSelected(emptyList(), A.path))
    }

    private companion object {
        val A = Model("/models/a.gguf", "a.gguf", 1L)
        val B = Model("/models/b.gguf", "b.gguf", 2L)
    }
}
