package com.lumarans30.hexamesh.logs

/** Snapshot of the running node's diagnostics. Null means "not measured yet". */
data class NodeMetrics(
    val predictedPerSecond: Double? = null,
    val temperatureCelsius: Double? = null,
    val memory: MemoryInfo? = null,
)
