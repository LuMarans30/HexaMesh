package com.lumarans30.hexamesh.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DocumentIdPathTest {

    private val external = "com.android.externalstorage.documents"

    @Test
    fun `maps a primary-volume document id to a path`() {
        assertEquals(
            "/storage/emulated/0/Download/model.gguf",
            documentIdToPath(external, "primary:Download/model.gguf", "/storage/emulated/0"),
        )
    }

    @Test
    fun `maps a secondary-volume document id to a path`() {
        assertEquals(
            "/storage/1234-5678/models/model.gguf",
            documentIdToPath(external, "1234-5678:models/model.gguf", "/storage/emulated/0"),
        )
    }

    @Test
    fun `unwraps raw document ids`() {
        assertEquals(
            "/storage/emulated/0/Download/model.gguf",
            documentIdToPath("com.android.providers.downloads.documents", "raw:/storage/emulated/0/Download/model.gguf", null),
        )
    }

    @Test
    fun `returns null for non-local providers`() {
        assertNull(documentIdToPath("com.google.android.apps.docs", "some-id", "/storage/emulated/0"))
    }

    @Test
    fun `returns null when the primary root is unknown`() {
        assertNull(documentIdToPath(external, "primary:Download/model.gguf", null))
    }

    @Test
    fun `returns null for malformed document ids`() {
        assertNull(documentIdToPath(external, "nocolon", "/storage/emulated/0"))
        assertNull(documentIdToPath(external, "primary:", "/storage/emulated/0"))
        assertNull(documentIdToPath(external, null, "/storage/emulated/0"))
        assertNull(documentIdToPath(external, "", "/storage/emulated/0"))
    }
}
