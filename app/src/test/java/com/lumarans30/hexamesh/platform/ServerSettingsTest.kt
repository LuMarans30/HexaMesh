package com.lumarans30.hexamesh.platform

import com.lumarans30.hexamesh.bridge.ServerRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `launch args default when nothing is stored`() {
        val settings = ServerSettings(FakeStore())

        assertEquals(DEFAULT_LAUNCH_ARGS, settings.launchArgsText)
        assertEquals(DEFAULT_LAUNCH_ARGS, settings.launchArgsFlow.value)
        assertEquals(parseLaunchArgs(DEFAULT_LAUNCH_ARGS), settings.launchArgs)
    }

    @Test
    fun `the port is parsed from the launch args`() {
        val settings = ServerSettings(FakeStore())

        settings.setLaunchArgs("--port 9090 -t 6")

        assertEquals(9090, settings.port)
    }

    @Test
    fun `the port falls back when the args omit it`() {
        val settings = ServerSettings(FakeStore())

        settings.setLaunchArgs("-t 6")

        assertEquals(DEFAULT_SERVER_PORT, settings.port)
    }

    @Test
    fun `an instance reads the args written by another`() {
        val store = FakeStore()
        val service = ServerSettings(store)
        val ui = ServerSettings(store)

        ui.setLaunchArgs("--port 9091 -t 8")

        assertEquals("--port 9091 -t 8", service.launchArgsText)
        assertEquals(9091, service.port)
    }

    @Test
    fun `stored args keep the model flag and the port`() {
        val settings = ServerSettings(FakeStore())

        settings.setLaunchArgs("-m other.gguf --port 9090 -t 8")

        assertEquals(listOf("-m", "other.gguf", "--port", "9090", "-t", "8"), settings.launchArgs)
        assertEquals(9090, settings.port)
    }

    @Test
    fun `the role defaults to serving and persists`() {
        val settings = ServerSettings(FakeStore())

        assertEquals(ServerRole.SERVER, settings.role)

        settings.setRole(ServerRole.RPC)

        assertEquals(ServerRole.RPC, settings.role)
    }

    private class FakeStore(
        val strings: MutableMap<String, String> = mutableMapOf(),
    ) : SettingsStore {
        override fun getString(
            key: String,
            defaultValue: String,
        ): String = strings[key] ?: defaultValue

        override fun putString(
            key: String,
            value: String,
        ) {
            strings[key] = value
        }
    }
}
