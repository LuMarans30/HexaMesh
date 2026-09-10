package com.lumarans30.hexamesh.node

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Observable lifecycle of the mesh node. */
sealed interface NodeState {
    data object Stopped : NodeState
    data object Idle : NodeState
    data class Starting(val modelPath: String) : NodeState
    data class Stopping(val modelPath: String) : NodeState
    data class Running(val modelPath: String, val endpoint: String) : NodeState
    data class Error(val message: String) : NodeState

    companion object {
        private val _current = MutableStateFlow<NodeState>(Stopped)

        val current: StateFlow<NodeState> = _current.asStateFlow()

        internal fun post(state: NodeState) {
            _current.value = state
        }
    }
}
