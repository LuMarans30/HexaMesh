package com.lumarans30.hexamesh.bridge

/** Role the native supervisor runs: an OpenAI-compatible server or an RPC peer. */
object ServerRole {
    const val SERVER = "server"
    const val RPC = "rpc"
}

data class ServerConfig(
    @JvmField val modelPath: String,
    @JvmField val nativeLibDir: String,
    @JvmField val cacheDir: String,
    /** Root for llama.cpp's Hugging Face cache; empty leaves `LLAMA_CACHE` unset. */
    @JvmField val llamaCacheDir: String = "",
    @JvmField val port: Int,
    @JvmField val backend: String,
    @JvmField val apiKey: String? = null,
    /** Newline-joined launch args; tokens never contain whitespace. */
    @JvmField val extraArgs: String = "",
    /** [ServerRole.SERVER] or [ServerRole.RPC]; mirrored by the Rust `ServerConfig`. */
    @JvmField val role: String = ServerRole.SERVER,
    /** Comma-joined `host:port` peers for llama-server's `--rpc`, empty when solo. */
    @JvmField val rpcServers: String = "",
    /** GGUF directory for router mode (`--models-dir`); empty selects single-model `-m`. */
    @JvmField val modelsDir: String = "",
)
