package com.lumarans30.hexamesh.node

import com.lumarans30.hexamesh.platform.Locks

/**
 * Everything [NodeController] needs from the Android framework, gathered into one
 * value so the controller can be exercised in plain JVM unit tests.
 */
data class NodeEnvironment(
    val nativeLibDir: String,
    val cacheDir: String,
    val apiKey: String,
    val serverDiedMessage: String,
    val locks: Locks,
    val settings: NodeSettings,
)
