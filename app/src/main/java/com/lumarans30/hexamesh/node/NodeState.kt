package com.lumarans30.hexamesh.node

/**
 * Observable lifecycle of the mesh node. The node always runs llama-server in
 * router mode, so there is a single model set rather than one active model.
 */
sealed interface NodeState {
    data object Stopped : NodeState
    data object Starting : NodeState
    data object Stopping : NodeState
    data class Running(val serverUrl: String) : NodeState
    data class Error(val message: String) : NodeState
}
