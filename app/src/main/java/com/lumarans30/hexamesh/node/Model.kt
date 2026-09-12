package com.lumarans30.hexamesh.node

data class Model(
    val path: String,
    val name: String,
    val sizeBytes: Long,
) {
    /**
     * The router's id for this model: the filename with `.gguf` removed. Mirrors
     * llama.cpp's `load_from_models_dir`, so a client can paste it straight into
     * an OpenAI request's `model` field.
     */
    val id: String = name.replace(".gguf", "")
}
