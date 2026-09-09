package com.lumarans30.hexamesh.node

import android.content.Context
import java.io.File

class ModelRepository(context: Context) {
    private val dir: File = File(context.getExternalFilesDir(null), "models").apply { mkdirs() }

    fun modelsDir(): File = dir

    fun findDefault(): File? =
        dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".gguf", ignoreCase = true) }
            ?.minByOrNull { it.name }

    fun adbPushHint(): String = "adb push model.gguf ${dir.absolutePath}/"
}
