package com.lumarans30.hexamesh.node

import android.content.Context
import java.io.File

class ModelRepository(context: Context) {
    private val app = context.applicationContext
    private val dir: File = File(app.getExternalFilesDir(null), "models").apply { mkdirs() }
    private val hfCache: File = File(app.getExternalFilesDir(null), "hf-cache").apply { mkdirs() }

    fun modelsDir(): File = dir

    fun hfCacheDir(): File = hfCache

    fun list(): List<Model> =
        dir.listFiles()
            ?.asSequence()
            ?.filter { it.isFile && isLoadableModel(it.name) }
            ?.map { Model(path = it.absolutePath, name = it.name, sizeBytes = it.length()) }
            ?.sortedBy { it.name.lowercase() }
            ?.toList()
            .orEmpty()

    fun delete(model: Model) {
        runCatching { !File(model.path).exists() || File(model.path).delete() }
    }

    fun adbPushHint(): String = "adb push model.gguf ${dir.absolutePath}/"
}

private val DRAFT_PREFIXES = listOf("mtp-", "dspark-", "dflash-")

/**
 * Mirrors llama.cpp's `load_from_models_dir` for top-level files: a lowercase
 * `.gguf` is a model unless it is an `mmproj` or draft sidecar. Keep it in step
 * with upstream so the app's list matches the router's `--models-dir` scan.
 */
internal fun isLoadableModel(fileName: String): Boolean =
    fileName.endsWith(".gguf") &&
        !fileName.contains("mmproj") &&
        DRAFT_PREFIXES.none(fileName::startsWith)
