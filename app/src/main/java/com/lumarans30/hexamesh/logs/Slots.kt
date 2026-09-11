package com.lumarans30.hexamesh.logs

private val DECODED = Regex("\"n_decoded\"\\s*:\\s*(\\d+)")

/** Sum of `n_decoded` across processing slots in a `/slots` response, or null when idle. */
fun parseDecodedTokens(slotsJson: String): Int? {
    val decoded = DECODED.findAll(slotsJson).mapNotNull { it.groupValues[1].toIntOrNull() }.toList()
    return decoded.sum().takeIf { decoded.isNotEmpty() }
}

/**
 * Live generation rate from two `/slots` samples. Null on the first sample of a
 * generation (no baseline yet, keep the previous value); zero when idle.
 */
fun tokensPerSecond(decoded: Int?, previousDecoded: Int?, previousAtMs: Long, nowMs: Long): Double? =
    when {
        decoded == null -> 0.0
        previousDecoded == null || nowMs <= previousAtMs -> null
        else -> ((decoded - previousDecoded) * 1000.0 / (nowMs - previousAtMs)).coerceAtLeast(0.0)
    }
