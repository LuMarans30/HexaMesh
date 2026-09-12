package com.lumarans30.hexamesh.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LaunchArgsTest {

    @Test
    fun `splits on any whitespace and drops blanks`() {
        assertEquals(listOf("-fa", "on", "-t", "6"), parseLaunchArgs("  -fa on\n-t\t6  "))
    }

    @Test
    fun `locks the app-owned flags including equals form`() {
        val flags = lockedLaunchFlags(parseLaunchArgs("-m x --host=0.0.0.0 -t 6"))

        assertEquals(listOf("-m", "--host=0.0.0.0"), flags)
    }

    @Test
    fun `effective args drop locked flags and keep the rest`() {
        val args = effectiveLaunchArgs("-m x -t 6 --host 0.0.0.0 --no-warmup")

        assertEquals(listOf("-t", "6", "--no-warmup"), args)
    }

    @Test
    fun `the port is not a locked flag`() {
        assertEquals(listOf("--port", "9090"), effectiveLaunchArgs("--port 9090"))
    }

    @Test
    fun `rpc is app-owned and stripped from user args`() {
        assertEquals(listOf("--rpc"), lockedLaunchFlags(parseLaunchArgs("--rpc 10.0.0.1:50052")))
        assertEquals(listOf("-t", "6"), effectiveLaunchArgs("-t 6 --rpc 10.0.0.1:50052"))
    }

    @Test
    fun `models-dir is app-owned and stripped from user args`() {
        assertEquals(listOf("--models-dir"), lockedLaunchFlags(parseLaunchArgs("--models-dir /tmp/m")))
        assertEquals(listOf("-t", "6"), effectiveLaunchArgs("-t 6 --models-dir /tmp/m"))
    }

    @Test
    fun `parses the last port, space and equals forms`() {
        assertEquals(9090, parseLaunchPort(parseLaunchArgs("--port 9090")))
        assertEquals(9090, parseLaunchPort(parseLaunchArgs("--port=9090")))
        assertEquals(9091, parseLaunchPort(parseLaunchArgs("--port 9090 --port 9091")))
    }

    @Test
    fun `no valid port yields null`() {
        assertNull(parseLaunchPort(parseLaunchArgs("-t 6")))
        assertNull(parseLaunchPort(parseLaunchArgs("--port 80")))
        assertNull(parseLaunchPort(parseLaunchArgs("--port abc")))
    }

    @Test
    fun `withPort rewrites an existing port`() {
        assertEquals("--port 9090 -t 6", withPort("--port 8080 -t 6", 9090))
        assertEquals("--port=9090 -t 6", withPort("--port=8080 -t 6", 9090))
    }

    @Test
    fun `withPort adds a port when none is present`() {
        assertEquals("--port 9090 -t 6", withPort("-t 6", 9090))
    }
}
