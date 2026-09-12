package com.lumarans30.hexamesh.bridge

/**
 * Abstraction over the native Rust supervisor.
 */
interface Engine {
    fun start(config: ServerConfig)

    fun stop()

    fun pollStatus(): EngineStatus?
}
