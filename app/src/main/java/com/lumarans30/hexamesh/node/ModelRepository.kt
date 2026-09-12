package com.lumarans30.hexamesh.node

import android.content.Context
import java.io.File

class ModelRepository(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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

    /**
     * The user's choice if it still exists on disk, otherwise the first model.
     * Resolved against [list] so a deleted file can never be handed to the
     * engine.
     */
    fun selected(): Model? =
        resolveSelected(list(), prefs.getString(KEY_SELECTED, null))

    fun select(model: Model) {
        prefs.edit().putString(KEY_SELECTED, model.path).apply()
    }

    fun delete(model: Model): Boolean =
        runCatching { !File(model.path).exists() || File(model.path).delete() }
            .getOrDefault(false)

    fun adbPushHint(): String = "adb push model.gguf ${dir.absolutePath}/"

    private companion object {
        const val PREFS_NAME = "hexamesh_models"
        const val KEY_SELECTED = "selected_model"
    }
}

private val DRAFT_PREFIXES = listOf("mtp-", "dspark-", "dflash-")

/**
 * Mirrors llama.cpp's `load_from_models_dir` for top-level files: a lowercase
 * `.gguf` is a model unless it is an `mmproj` or a draft sidecar. Case-sensitive
 * like upstream, so the app's list matches the router's `--models-dir` scan.
 */
internal fun isLoadableModel(fileName: String): Boolean =
    fileName.endsWith(".gguf") &&
        !fileName.contains("mmproj") &&
        DRAFT_PREFIXES.none(fileName::startsWith)

internal fun resolveSelected(models: List<Model>, pinnedPath: String?): Model? =
    models.firstOrNull { it.path == pinnedPath } ?: models.firstOrNull()
