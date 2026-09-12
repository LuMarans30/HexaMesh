package com.lumarans30.hexamesh.logs

import java.io.File

/** Reads `/sys/class/thermal`, returning (zone type, millidegrees C) pairs. */
object ThermalZones {
    private val root = File("/sys/class/thermal")

    private val zones: List<Pair<String, File>> by lazy { discoverZones(root) }

    fun read(): List<Pair<String, Int>> = zones.mapNotNull(::readTemperature)
}

internal fun discoverZones(root: File): List<Pair<String, File>> =
    root
        .listFiles { file -> file.name.startsWith("thermal_zone") }
        ?.mapNotNull { zone ->
            val type = readText(File(zone, "type")) ?: return@mapNotNull null
            type to File(zone, "temp")
        }.orEmpty()

internal fun readTemperature(zone: Pair<String, File>): Pair<String, Int>? {
    val (type, tempFile) = zone
    val temp = readText(tempFile)?.toIntOrNull() ?: return null
    return type to temp
}

private fun readText(file: File): String? = runCatching { file.readText().trim() }.getOrNull()?.takeIf { it.isNotEmpty() }

/** Hottest CPU or GPU zone in Celsius, or null when the device exposes neither. */
fun hottestCelsius(zones: List<Pair<String, Int>>): Double? =
    zones
        .filter { it.first.startsWith("cpu") || it.first.startsWith("gpu") }
        .maxOfOrNull { it.second }
        ?.div(1000.0)
