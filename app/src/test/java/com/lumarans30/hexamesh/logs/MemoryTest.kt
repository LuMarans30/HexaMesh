package com.lumarans30.hexamesh.logs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MemoryTest {
    @Test
    fun `parses available and total in bytes`() {
        val meminfo =
            "MemTotal:       11502936 kB\n" +
                "MemFree:          123456 kB\n" +
                "MemAvailable:    1810512 kB\n"

        val info = parseMemoryInfo(meminfo)!!

        assertEquals(1_810_512L * 1024, info.availableBytes)
        assertEquals(11_502_936L * 1024, info.totalBytes)
    }

    @Test
    fun `missing MemAvailable yields null`() {
        assertNull(parseMemoryInfo("MemTotal: 11502936 kB\n"))
        assertNull(parseMemoryInfo("MemAvailable: 1810512 kB\n"))
    }
}
