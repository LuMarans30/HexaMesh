package com.lumarans30.hexamesh.node

import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class ImportOutcome(val target: File, val warning: String? = null)

private const val BUFFER_SIZE = 64 * 1024
private const val PROGRESS_STEP_BYTES = 2L * 1024 * 1024

/**
 * Moves or copies [source] into [targetDir]. A move renames first (instant, no
 * extra space) and falls back to copy-then-delete across volumes; both paths
 * need path access, so this is only used with all-files access.
 *
 * @throws IOException when the source is missing, the target exists, space is
 *   short, or the copy fails.
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
 * Copies a picked document into [targetDir] by streaming [open], so it needs no
 * filesystem access and works for shared or cloud storage.
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
            currentCoroutineContext().ensureActive()
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
