package com.lumarans30.hexamesh.logs

import java.io.File

/** Reads `/sys/class/thermal`, returning (zone type, millidegrees C) pairs. */
object ThermalZones {
    private val root = File("/sys/class/thermal")

    fun read(): List<Pair<String, Int>> =
        root
            .listFiles { file -> file.name.startsWith("thermal_zone") }
            ?.mapNotNull { zone ->
                val type = read(File(zone, "type")) ?: return@mapNotNull null
                val temp = read(File(zone, "temp"))?.toIntOrNull() ?: return@mapNotNull null
                type to temp
            }.orEmpty()

    private fun read(file: File): String? = runCatching { file.readText().trim() }.getOrNull()?.takeIf { it.isNotEmpty() }
}

/** Hottest CPU or GPU zone in Celsius, or null when the device exposes neither. */
fun hottestCelsius(zones: List<Pair<String, Int>>): Double? =
    zones
        .filter { it.first.startsWith("cpu") || it.first.startsWith("gpu") }
        .maxOfOrNull { it.second }
        ?.div(1000.0)
