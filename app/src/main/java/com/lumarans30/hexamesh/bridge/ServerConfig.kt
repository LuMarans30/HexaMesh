package com.lumarans30.hexamesh.bridge

data class ServerConfig(
    @JvmField val modelPath: String,
    @JvmField val nativeLibDir: String,
    @JvmField val cacheDir: String,
    @JvmField val port: Int,
    @JvmField val backend: String,
    //@JvmField val meshPeers: Array<String> = emptyArray(),
    //@JvmField val apiKey: String? = null
)
