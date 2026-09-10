package com.lumarans30.hexamesh.node

import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

/** Result of importing a file into the models directory. */
data class ImportOutcome(val target: File, val warning: String? = null)

private const val BUFFER_SIZE = 64 * 1024
private const val PROGRESS_STEP_BYTES = 2L * 1024 * 1024

/**
 * Copies or moves [source] into [targetDir].
 *
 * With [move], a rename is attempted first: it is instant and needs no extra
 * space. When the source lives on another volume the rename fails, so this
 * falls back to copy-then-delete.
 *
 * @throws IOException if the source is missing, the destination already exists,
 *   there is not enough free space, or the copy fails.
 */
internal suspend fun importModel(
    source: File,
    targetDir: File,
    move: Boolean,
    onProgress: suspend (copied: Long, total: Long) -> Unit = { _, _ -> },
): ImportOutcome {
    if (!source.isFile) throw IOException("The selected file no longer exists.")

    val target = File(targetDir, source.name)
    if (target.exists()) throw IOException("A model named ${source.name} already exists.")

    targetDir.mkdirs()

    if (move && source.renameTo(target)) return ImportOutcome(target)

    val temp = File(targetDir, "${source.name}.part")
    try {
        copyFile(source, temp, onProgress)
        if (!temp.renameTo(target)) throw IOException("Could not finalise the imported file.")
    } catch (t: Throwable) {
        temp.delete()
        throw t
    }

    if (move && !source.delete()) {
        return ImportOutcome(target, "The original file could not be deleted.")
    }

    return ImportOutcome(target)
}

private suspend fun copyFile(
    source: File,
    target: File,
    onProgress: suspend (copied: Long, total: Long) -> Unit,
) {
    val total = source.length()
    val free = target.parentFile?.usableSpace ?: 0L
    if (total > 0 && total > free) {
        throw IOException("Not enough free space for ${total / (1024 * 1024)} MiB.")
    }

    source.inputStream().use { input ->
        target.outputStream().use { output ->
            val buffer = ByteArray(BUFFER_SIZE)
            var copied = 0L
            var reported = 0L
            while (true) {
                coroutineContext.ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                output.write(buffer, 0, read)
                copied += read
                if (copied - reported >= PROGRESS_STEP_BYTES) {
                    reported = copied
                    onProgress(copied, total)
                }
            }
            onProgress(copied, total)
        }
    }
}
