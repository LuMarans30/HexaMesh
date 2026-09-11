package com.lumarans30.hexamesh.logs

import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration.Companion.milliseconds

const val LLAMA_SERVER_LOG_NAME = "llama-server.log"

private const val NEWLINE = '\n'.code.toByte()
private const val MAX_CHUNK_BYTES = 1 shl 20

sealed interface TailEvent {
    data class Line(val text: String) : TailEvent
    data object Reset : TailEvent
}

/**
 * Follows a growing text file and emits each completed line. Detects truncation
 * and re-reads from the top, which the Rust supervisor triggers on every server
 * start (`OpenOptions::truncate`).
 */
class LogTailer(
    private val file: File,
    private val pollIntervalMs: Long = 250L,
) {
    fun events(): Flow<TailEvent> = flow {
        var position = 0L
        var pending = ByteArray(0)

        while (true) {
            val length = if (file.exists()) file.length() else 0L

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
                        RandomAccessFile(file, "r").use { raf ->
                            raf.seek(position)
                            raf.readFully(chunk)
                        }
                    }.isSuccess

                if (!read) {
                    position = 0L
                    pending = ByteArray(0)
                    continue
                }

                position += size
                pending += chunk

                val newline = pending.lastIndexOf(NEWLINE)
                if (newline >= 0) {
                    for (line in String(pending, 0, newline, StandardCharsets.UTF_8).split('\n')) {
                        emit(TailEvent.Line(line.trimEnd('\r')))
                    }
                    pending = pending.copyOfRange(newline + 1, pending.size)
                }

                continue
            }

            delay(pollIntervalMs.milliseconds)
        }
    }
}
