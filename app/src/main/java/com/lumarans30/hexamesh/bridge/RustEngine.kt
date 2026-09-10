package com.lumarans30.hexamesh.bridge

/** JNI-backed [Engine] that drives `libhexa_mesh_core.so`. */
class RustEngine : Engine {

    external override fun start(config: ServerConfig)

    external override fun stop()

    external override fun pollStatus(): EngineStatus?

    companion object {
        init {
            System.loadLibrary("hexa_mesh_core")
        }
    }
}