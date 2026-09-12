package com.lumarans30.hexamesh.node

data class Model(
    val path: String,
    val name: String,
    val sizeBytes: Long,
) {
    /** The router's id: the filename minus `.gguf`, mirroring llama.cpp's `load_from_models_dir`. */
    val id: String = name.replace(".gguf", "")
}
