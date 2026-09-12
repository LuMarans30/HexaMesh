package com.lumarans30.hexamesh.logs

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class LogTailerTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun logFile(contents: String = ""): File = folder.newFile("llama-server.log").apply { writeText(contents) }

    private fun List<TailEvent>.texts(): List<String> = filterIsInstance<TailEvent.Line>().map { it.text }

    @Test
    fun `emits lines already in the file`() =
        runTest {
            val file = logFile("first\nsecond\n")
            val events = mutableListOf<TailEvent>()

            val job = launch { LogTailer(file).events().collect { events += it } }
            runCurrent()
            job.cancel()

            assertEquals(listOf("first", "second"), events.texts())
        }

    @Test
    fun `follows lines appended after it starts`() =
        runTest {
            val file = logFile("first\n")
            val events = mutableListOf<TailEvent>()

            val job = launch { LogTailer(file).events().collect { events += it } }
            runCurrent()

            file.appendText("second\n")
            advanceTimeBy(300.milliseconds)
            job.cancel()

            assertEquals(listOf("first", "second"), events.texts())
        }

    @Test
    fun `waits for the file to appear`() =
        runTest {
            val file = File(folder.root, "late.log")
            val events = mutableListOf<TailEvent>()

            val job = launch { LogTailer(file).events().collect { events += it } }
            advanceTimeBy(300.milliseconds)

            file.writeText("hello\n")
            advanceTimeBy(300.milliseconds)
            job.cancel()

            assertEquals(listOf("hello"), events.texts())
        }

    @Test
    fun `signals reset and re-reads when the file is truncated`() =
        runTest {
            val file = logFile("old line one\nold line two\n")
            val events = mutableListOf<TailEvent>()

            val job = launch { LogTailer(file).events().collect { events += it } }
            runCurrent()

            file.writeText("new\n")
            advanceTimeBy(300.milliseconds)
            job.cancel()

            assertEquals(listOf("old line one", "old line two", "new"), events.texts())
            assertEquals(TailEvent.Reset, events[2])
        }

    @Test
    fun `strips carriage returns from CRLF lines`() =
        runTest {
            val file = logFile("a\r\nb\r\n")
            val events = mutableListOf<TailEvent>()

            val job = launch { LogTailer(file).events().collect { events += it } }
            runCurrent()
            job.cancel()

            assertEquals(listOf("a", "b"), events.texts())
        }

    @Test
    fun `holds a trailing partial line until it is terminated`() =
        runTest {
            val file = logFile("complete\npart")
            val events = mutableListOf<TailEvent>()

            val job = launch { LogTailer(file).events().collect { events += it } }
            runCurrent()
            assertEquals(listOf("complete"), events.texts())

            file.appendText("ial\n")
            advanceTimeBy(300.milliseconds)
            job.cancel()

            assertEquals(listOf("complete", "partial"), events.texts())
        }
}
