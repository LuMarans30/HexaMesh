package com.lumarans30.hexamesh.logs

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import kotlin.time.Duration.Companion.milliseconds

const val LLAMA_SERVER_LOG_NAME = "llama-server.log"

private const val NEWLINE = '\n'.code.toByte()
private const val MAX_CHUNK_BYTES = 1 shl 20

sealed interface TailEvent {
    data class Lines(
        val texts: List<String>,
    ) : TailEvent

    data object Reset : TailEvent
}

/**
 * Follows a growing text file, emitting each completed line. Truncation rewinds
 * to the top (the supervisor truncates the log on every server start).
 */
class LogTailer(
    private val file: File,
    private val pollIntervalMs: Long = 250L,
) {
    fun events(): Flow<TailEvent> =
        flow {
            var position = 0L
            var pending = ByteArray(0)
            var raf: RandomAccessFile? = null
            var key: Any? = null

            try {
                while (true) {
                    val attrs = attributes()
                    val length = attrs?.size() ?: 0L

                    if (attrs?.fileKey() != key) {
                        raf.closeQuietly()
                        raf = null
                        key = attrs?.fileKey()
                    }

                    if (length < position) {
                        position = 0L
                        pending = ByteArray(0)
                        emit(TailEvent.Reset)
                    }

                    if (length > position) {
                        val size = (length - position).coerceAtMost(MAX_CHUNK_BYTES.toLong()).toInt()
                        val chunk = ByteArray(size)

                        val read =
                            runCatching {
                                val open = raf ?: RandomAccessFile(file, "r").also { raf = it }
                                open.seek(position)
                                open.readFully(chunk)
                            }.isSuccess

                        if (!read) {
                            raf.closeQuietly()
                            raf = null
                            key = null
                            position = 0L
                            pending = ByteArray(0)
                            delay(pollIntervalMs.milliseconds)
                            continue
                        }

                        position += size
                        pending += chunk

                        val newline = pending.lastIndexOf(NEWLINE)
                        if (newline >= 0) {
                            val text = String(pending, 0, newline, StandardCharsets.UTF_8)
                            emit(TailEvent.Lines(text.split('\n').map { it.trimEnd('\r') }))
                            pending = pending.copyOfRange(newline + 1, pending.size)
                        }

                        continue
                    }

                    delay(pollIntervalMs.milliseconds)
                }
            } finally {
                raf.closeQuietly()
            }
        }

    private fun attributes(): BasicFileAttributes? =
        runCatching { Files.readAttributes(file.toPath(), BasicFileAttributes::class.java) }.getOrNull()

    private fun RandomAccessFile?.closeQuietly() = runCatching { this?.close() }
}
