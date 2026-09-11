package com.lumarans30.hexamesh.logs

/** llama.cpp's `slot print_timing` line reports generation throughput as `tg = <n> t/s`. */
private val GENERATION_RATE = Regex("""\btg\s*=\s*([0-9.]+)\s+t/s""")

fun parseTokensPerSecond(line: String): Double? =
    GENERATION_RATE.find(line)?.groupValues?.get(1)?.toDoubleOrNull()

/** Snapshot of the running node's diagnostics. Null means "not measured yet". */
data class NodeMetrics(
    val predictedPerSecond: Double? = null,
    val temperatureCelsius: Double? = null,
)
