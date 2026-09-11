package com.lumarans30.hexamesh.logs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThermalTest {

    @Test
    fun `picks the hottest cpu or gpu zone`() {
        val zones =
            listOf(
                "ddr-0" to 45000,
                "cpu-0-0" to 42000,
                "gpu-0" to 39000,
                "camera-0" to 30000,
            )

        assertEquals(42.0, hottestCelsius(zones)!!, 0.001)
    }

    @Test
    fun `returns null when no cpu or gpu zone exists`() {
        assertNull(hottestCelsius(listOf("ddr-0" to 45000, "battery" to 30000)))
        assertNull(hottestCelsius(emptyList()))
    }
}
