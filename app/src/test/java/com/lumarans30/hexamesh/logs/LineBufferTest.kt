package com.lumarans30.hexamesh.logs

import org.junit.Assert.assertEquals
import org.junit.Test

class LineBufferTest {
    @Test
    fun `keeps only the most recent lines`() {
        val buffer = LineBuffer(capacity = 3)

        listOf("a", "b", "c", "d", "e").forEach(buffer::append)

        assertEquals(listOf("c", "d", "e"), buffer.lines.value)
    }

    @Test
    fun `clear empties the buffer`() {
        val buffer = LineBuffer(capacity = 3)
        buffer.append("a")

        buffer.clear()

        assertEquals(emptyList<String>(), buffer.lines.value)
    }

    @Test
    fun `a batch keeps order and only the most recent lines`() {
        val buffer = LineBuffer(capacity = 3)

        buffer.append(listOf("a", "b", "c", "d", "e"))

        assertEquals(listOf("c", "d", "e"), buffer.lines.value)
    }

    @Test
    fun `an empty batch is ignored`() {
        val buffer = LineBuffer(capacity = 3)
        buffer.append("a")

        buffer.append(emptyList())

        assertEquals(listOf("a"), buffer.lines.value)
    }
}
