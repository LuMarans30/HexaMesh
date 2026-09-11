package com.lumarans30.hexamesh.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerSettingsTest {

    @Test
    fun `the user range is valid`() {
        assertTrue(isValidPort(MIN_SERVER_PORT))
        assertTrue(isValidPort(MAX_SERVER_PORT))
        assertTrue(isValidPort(8080))
    }

    @Test
    fun `privileged and out-of-range ports are rejected`() {
        assertFalse(isValidPort(-1))
        assertFalse(isValidPort(0))
        assertFalse(isValidPort(80))
        assertFalse(isValidPort(MIN_SERVER_PORT - 1))
        assertFalse(isValidPort(MAX_SERVER_PORT + 1))
    }

    @Test
    fun `parsePort trims and validates`() {
        assertEquals(8080, parsePort("8080"))
        assertEquals(9090, parsePort("  9090 "))
        assertNull(parsePort(""))
        assertNull(parsePort("80"))
        assertNull(parsePort("70000"))
        assertNull(parsePort("abc"))
    }

    @Test
    fun `the initial value is coerced into the valid range`() {
        val settings = ServerSettings({ 80 }, {})

        assertEquals(DEFAULT_SERVER_PORT, settings.port)
        assertEquals(DEFAULT_SERVER_PORT, settings.portFlow.value)
    }

    @Test
    fun `setPort writes once and ignores invalid or unchanged values`() {
        var stored = 8080
        var writes = 0
        val settings = ServerSettings({ stored }, { stored = it; writes++ })

        settings.setPort(9090)
        assertEquals(9090, stored)
        assertEquals(9090, settings.port)
        assertEquals(9090, settings.portFlow.value)

        settings.setPort(80)
        settings.setPort(9090)
        assertEquals(1, writes)
        assertEquals(9090, stored)
    }

    @Test
    fun `an instance reads the port written by another`() {
        var stored = 8080
        val service = ServerSettings({ stored }, { stored = it })
        val ui = ServerSettings({ stored }, { stored = it })

        ui.setPort(9090)

        assertEquals(9090, service.port)
    }
}
