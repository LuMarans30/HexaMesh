package com.lumarans30.hexamesh.node

import java.io.File
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ModelImportTest {

    @get:Rule val temp = TemporaryFolder()

    @Test
    fun `copy leaves the original in place`() = runBlocking {
        val source = sourceFile()
        val dir = targetDir()

        val outcome = importModel(source, dir, move = false)

        assertTrue(source.exists())
        assertTrue(outcome.target.exists())
        assertEquals("model.gguf", outcome.target.name)
        assertEquals(source.readBytes().toList(), outcome.target.readBytes().toList())
    }

    @Test
    fun `move removes the original`() = runBlocking {
        val source = sourceFile()
        val dir = targetDir()

        val outcome = importModel(source, dir, move = true)

        assertFalse(source.exists())
        assertEquals("model.gguf", outcome.target.name)
    }

    @Test
    fun `refuses to overwrite an existing model`() {
        val source = sourceFile()
        val dir = targetDir()
        File(dir, "model.gguf").writeBytes(ByteArray(1))

        val error =
            runCatching { runBlocking { importModel(source, dir, move = false) } }.exceptionOrNull()

        assertTrue(error is IOException)
    }

    @Test
    fun `fails when the source is missing`() {
        val dir = targetDir()

        val error =
            runCatching { runBlocking { importModel(File(temp.root, "gone.gguf"), dir, true) } }
                .exceptionOrNull()

        assertTrue(error is IOException)
    }

    @Test
    fun `reports progress to completion`() = runBlocking {
        val source = sourceFile(content = ByteArray(5 * 1024 * 1024))
        var lastCopied = 0L
        var lastTotal = 0L

        importModel(source, targetDir(), move = false) { copied, total ->
            lastCopied = copied
            lastTotal = total
        }

        assertEquals(5L * 1024 * 1024, lastCopied)
        assertEquals(5L * 1024 * 1024, lastTotal)
    }

    private fun sourceFile(
        name: String = "model.gguf",
        content: ByteArray = ByteArray(4096) { it.toByte() },
    ): File = File(temp.root, name).apply { writeBytes(content) }

    private fun targetDir(): File = File(temp.root, "models").apply { mkdirs() }
}
