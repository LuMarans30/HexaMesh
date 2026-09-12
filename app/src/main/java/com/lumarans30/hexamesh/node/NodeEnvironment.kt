package com.lumarans30.hexamesh.node

import com.lumarans30.hexamesh.platform.Locks

/** [NodeController]'s Android-framework inputs, so the controller is JVM-testable. */
data class NodeEnvironment(
    val nativeLibDir: String,
    val cacheDir: String,
    val llamaCacheDir: String,
    val modelsDir: String,
    val apiKey: String,
    val serverDiedMessage: String,
    val locks: Locks,
    val settings: NodeSettings,
)
