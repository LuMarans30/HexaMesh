package com.lumarans30.hexamesh.node

/** Observable lifecycle of the node; it always runs llama-server in router mode. */
sealed interface NodeState {
    data object Stopped : NodeState

    data object Starting : NodeState

    data object Stopping : NodeState

    data class Running(
        val endpoint: String,
        val isWorker: Boolean = false,
    ) : NodeState

    data class Error(
        val message: String,
    ) : NodeState
}
