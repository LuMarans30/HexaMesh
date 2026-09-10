package com.lumarans30.hexamesh.node

import android.content.Context
import java.io.File

class ModelRepository(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val dir: File = File(app.getExternalFilesDir(null), "models").apply { mkdirs() }

    fun modelsDir(): File = dir

    fun list(): List<Model> =
        dir.listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.name.endsWith(EXTENSION, ignoreCase = true) }
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
        const val EXTENSION = ".gguf"
    }
}

internal fun resolveSelected(models: List<Model>, pinnedPath: String?): Model? =
    models.firstOrNull { it.path == pinnedPath } ?: models.firstOrNull()
