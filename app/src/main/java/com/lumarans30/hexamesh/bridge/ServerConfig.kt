package com.lumarans30.hexamesh.bridge

data class ServerConfig(
    @JvmField val modelPath: String,
    @JvmField val nativeLibDir: String,
    @JvmField val cacheDir: String,
    @JvmField val port: Int,
    @JvmField val backend: String,
    @JvmField val apiKey: String? = null,
    /** Newline-joined launch args; tokens never contain whitespace. */
    @JvmField val extraArgs: String = "",
)
