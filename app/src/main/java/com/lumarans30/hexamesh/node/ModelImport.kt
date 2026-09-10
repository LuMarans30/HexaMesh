package com.lumarans30.hexamesh.node

import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

/** Result of importing a file into the models directory. */
data class ImportOutcome(val target: File, val warning: String? = null)

private const val BUFFER_SIZE = 64 * 1024
private const val PROGRESS_STEP_BYTES = 2L * 1024 * 1024

/**
 * Moves or copies [source] into [targetDir].
 *
 * With [move], a rename is attempted first: it is instant and needs no extra
 * space. When the source lives on another volume the rename fails, so this
 * falls back to copy-then-delete.
 *
 * Both paths need raw read access to [source], so this is only used for files
 * the app can already open by path (i.e. with all-files access).
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
        source.inputStream().use { input -> copyInto(input, temp, source.length(), onProgress) }
        if (!temp.renameTo(target)) throw IOException("Could not finalise the imported file.")
    } catch (t: Throwable) {
        temp.delete()
        throw t
    }

    if (move && !source.delete()) {
        return ImportOutcome(target, "The original file could not be removed.")
    }

    return ImportOutcome(target)
}

/**
 * Copies a picked document into [targetDir] by streaming it.
 *
 * Unlike [importModel] this needs no filesystem access to the source: it reads
 * through [open], which is how a file picked from shared or cloud storage is
 * copied without "All files access".
 */
internal suspend fun importFromStream(
    fileName: String,
    totalBytes: Long,
    targetDir: File,
    onProgress: suspend (copied: Long, total: Long) -> Unit = { _, _ -> },
    open: () -> InputStream,
): ImportOutcome {
    val safeName = fileName.substringAfterLast('/').substringAfterLast('\\').trim()
    if (safeName.isEmpty()) throw IOException("The selected file has no name.")

    val target = File(targetDir, safeName)
    if (target.exists()) throw IOException("A model named $safeName already exists.")

    targetDir.mkdirs()

    val temp = File(targetDir, "$safeName.part")
    try {
        open().use { input -> copyInto(input, temp, totalBytes, onProgress) }
        if (!temp.renameTo(target)) throw IOException("Could not finalise the imported file.")
    } catch (t: Throwable) {
        temp.delete()
        throw t
    }

    return ImportOutcome(target)
}

private suspend fun copyInto(
    input: InputStream,
    target: File,
    total: Long,
    onProgress: suspend (copied: Long, total: Long) -> Unit,
) {
    val free = target.parentFile?.usableSpace ?: 0L
    if (total > 0 && total > free) {
        throw IOException("Not enough free space for ${total / (1024 * 1024)} MiB.")
    }

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
