package com.lumarans30.hexamesh.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatSizeTest {

    @Test
    fun `zero bytes`() {
        assertEquals("0 B", formatSize(0))
    }

    @Test
    fun `bytes below one kibibyte are not scaled`() {
        assertEquals("512 B", formatSize(512))
    }

    @Test
    fun `exact kibibyte`() {
        assertEquals("1.0 KiB", formatSize(1024))
    }

    @Test
    fun mebibyte() {
        assertEquals("1.0 MiB", formatSize(1024L * 1024))
    }

    @Test
    fun `gibibyte with fraction`() {
        assertEquals("3.8 GiB", formatSize((3.8 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun tebibyte() {
        assertEquals("1.0 TiB", formatSize(1024L * 1024 * 1024 * 1024))
    }
}
