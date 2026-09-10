package com.lumarans30.hexamesh.bridge

class RustEngine {

    external fun start(config: ServerConfig)

    external fun stop()

    external fun pollStatus(): EngineStatus?

    companion object {
        init {
            System.loadLibrary("hexa_mesh_core")
        }
    }
}