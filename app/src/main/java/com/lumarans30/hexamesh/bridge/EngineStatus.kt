package com.lumarans30.hexamesh.bridge

/**
 * Snapshot published by the Rust supervisor through
 * [RustEngine.pollStatus]. Keep the integer values in sync with
 * `status.rs` (`Status`).
 */
class EngineStatus(
    @JvmField val state: Int,
    @JvmField val message: String?,
) {
    companion object {
        const val STOPPED = 0
        const val STARTING = 1
        const val RUNNING = 2
        const val ERROR = 3
    }
}
