package com.lumarans30.hexamesh.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class HexaTabTest {
    @Test
    fun `tabs keep the roadmap order`() {
        assertEquals(
            listOf(HexaTab.Manage, HexaTab.Mesh, HexaTab.Logs, HexaTab.Settings),
            HexaTab.entries.toList(),
        )
    }
}
