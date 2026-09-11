package com.lumarans30.hexamesh.node

/**
 * Observable lifecycle of the mesh node.
 */
sealed interface NodeState {
    data object Stopped : NodeState
    data object Idle : NodeState
    data class Starting(val modelPath: String) : NodeState
    data class Stopping(val modelPath: String) : NodeState
    data class Running(val modelPath: String, val serverUrl: String) : NodeState
    data class Error(val message: String) : NodeState
}
