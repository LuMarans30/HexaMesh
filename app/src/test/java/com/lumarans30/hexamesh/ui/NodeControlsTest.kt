package com.lumarans30.hexamesh.ui

import com.lumarans30.hexamesh.node.NodeState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeControlsTest {

    @Test
    fun `controls are enabled when the node is settled`() {
        assertTrue(controlsEnabled(NodeState.Stopped))
        assertTrue(controlsEnabled(NodeState.Error("boom")))
    }

    @Test
    fun `controls are disabled while loading, running or unloading`() {
        assertFalse(controlsEnabled(NodeState.Starting))
        assertFalse(controlsEnabled(NodeState.Stopping))
        assertFalse(controlsEnabled(NodeState.Running("http://127.0.0.1:8080")))
    }
}
