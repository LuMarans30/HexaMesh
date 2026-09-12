package com.lumarans30.hexamesh.logs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ThermalTest {
    @get:Rule
    val folder = TemporaryFolder()

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

    @Test
    fun `discovers zone labels once and re-reads each temperature`() {
        val zone = folder.newFolder("thermal_zone0")
        File(zone, "type").writeText("cpu-0\n")
        File(zone, "temp").writeText("42000\n")

        val zones = discoverZones(folder.root)

        assertEquals(listOf("cpu-0"), zones.map { it.first })
        assertEquals("cpu-0" to 42000, readTemperature(zones.single()))

        File(zone, "temp").writeText("51000\n")
        assertEquals("cpu-0" to 51000, readTemperature(zones.single()))
    }

    @Test
    fun `zones without a type are skipped`() {
        folder.newFolder("thermal_zone0")

        assertEquals(emptyList<Pair<String, File>>(), discoverZones(folder.root))
    }
}
