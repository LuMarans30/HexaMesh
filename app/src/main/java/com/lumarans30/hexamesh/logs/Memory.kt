package com.lumarans30.hexamesh.logs

import java.io.File

private val MEM_TOTAL = Regex("""MemTotal:\s+(\d+) kB""")
private val MEM_AVAILABLE = Regex("""MemAvailable:\s+(\d+) kB""")

/** System RAM from `/proc/meminfo`: available (reclaimable) and total, in bytes. */
data class MemoryInfo(val availableBytes: Long, val totalBytes: Long)

fun parseMemoryInfo(meminfo: String): MemoryInfo? {
    val available = MEM_AVAILABLE.find(meminfo)?.groupValues?.get(1)?.toLongOrNull() ?: return null
    val total = MEM_TOTAL.find(meminfo)?.groupValues?.get(1)?.toLongOrNull() ?: return null
    return MemoryInfo(availableBytes = available * 1024, totalBytes = total * 1024)
}

fun readMemoryInfo(meminfo: File = File("/proc/meminfo")): MemoryInfo? =
    runCatching { meminfo.readText() }.getOrNull()?.let(::parseMemoryInfo)
