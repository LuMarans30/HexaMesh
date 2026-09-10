package com.lumarans30.hexamesh.ui

import com.lumarans30.hexamesh.node.Model
import com.lumarans30.hexamesh.node.NodeState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeControlsTest {

    @Test
    fun `selection is enabled when the node is not busy`() {
        assertTrue(selectionEnabled(NodeState.Stopped))
        assertTrue(selectionEnabled(NodeState.Idle))
        assertTrue(selectionEnabled(NodeState.Error("boom")))
    }

    @Test
    fun `selection is disabled while loading, running or unloading`() {
        assertFalse(selectionEnabled(NodeState.Starting("/models/a.gguf")))
        assertFalse(selectionEnabled(NodeState.Stopping("/models/a.gguf")))
        assertFalse(selectionEnabled(NodeState.Running("/models/a.gguf", "http://127.0.0.1:8080")))
    }

    @Test
    fun `load is allowed from a settled state with a selection`() {
        assertTrue(canLoad(NodeState.Stopped, hasSelection = true))
        assertTrue(canLoad(NodeState.Error("boom"), hasSelection = true))
    }

    @Test
    fun `load is refused without a selection`() {
        assertFalse(canLoad(NodeState.Stopped, hasSelection = false))
        assertFalse(canLoad(NodeState.Error("boom"), hasSelection = false))
    }

    @Test
    fun `load is refused while busy or running`() {
        assertFalse(canLoad(NodeState.Starting("/models/a.gguf"), hasSelection = true))
        assertFalse(canLoad(NodeState.Stopping("/models/a.gguf"), hasSelection = true))
        assertFalse(canLoad(NodeState.Running("/models/a.gguf", "http://127.0.0.1:8080"), true))
        assertFalse(canLoad(NodeState.Idle, hasSelection = true))
    }

    @Test
    fun `delete is allowed when the node is settled`() {
        assertTrue(canDelete(NodeState.Stopped, A, activeModelPath = null))
        assertTrue(canDelete(NodeState.Idle, A, activeModelPath = null))
        assertTrue(canDelete(NodeState.Error("boom"), A, activeModelPath = null))
    }

    @Test
    fun `delete is refused while the node is busy`() {
        assertFalse(canDelete(NodeState.Starting(A.path), A, activeModelPath = null))
        assertFalse(canDelete(NodeState.Stopping(A.path), A, activeModelPath = null))
        assertFalse(canDelete(NodeState.Running(A.path, "http://127.0.0.1:8080"), A, A.path))
    }

    @Test
    fun `delete is refused for the active model`() {
        assertFalse(canDelete(NodeState.Stopped, A, activeModelPath = A.path))
    }

    private companion object {
        val A = Model("/models/a.gguf", "a.gguf", 1L)
    }
}
