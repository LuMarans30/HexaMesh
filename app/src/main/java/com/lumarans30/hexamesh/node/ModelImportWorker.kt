package com.lumarans30.hexamesh.node

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.lumarans30.hexamesh.R
import kotlinx.coroutines.CancellationException
import java.io.File
import java.io.IOException

/**
 * Imports a picked `.gguf` into the models directory: a rename when possible,
 * otherwise a streamed copy (which works without all-files access).
 */
class ModelImportWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val fileName = inputData.getString(KEY_FILE_NAME) ?: "model.gguf"
        val move = inputData.getBoolean(KEY_MOVE, false)
        val sourcePath = inputData.getString(KEY_SOURCE)?.takeIf { it.isNotBlank() }
        val uriString = inputData.getString(KEY_URI)
        val total = inputData.getLong(KEY_TOTAL, -1L)

        val modelsDir =
            File(applicationContext.getExternalFilesDir(null), MODELS_DIR).apply { mkdirs() }

        return try {
            runCatching { setForeground(foregroundInfo(fileName, percent = null)) }

            val outcome =
                if (move) {
                    val path =
                        sourcePath ?: return Result.failure(
                            workDataOf(KEY_ERROR to "No source path was provided."),
                        )
                    importModel(File(path), modelsDir, move = true) { copied, size ->
                        report(fileName, copied, size)
                    }
                } else {
                    val uri =
                        uriString?.let(Uri::parse)
                            ?: return Result.failure(
                                workDataOf(KEY_ERROR to "No source file was provided."),
                            )
                    importFromStream(fileName, total, modelsDir, onProgress = { copied, size ->
                        report(fileName, copied, size)
                    }) {
                        applicationContext.contentResolver.openInputStream(uri)
                            ?: throw IOException("Could not open the selected file.")
                    }
                }

            Result.success(
                workDataOf(
                    KEY_FILE_NAME to outcome.target.name,
                    KEY_PATH to outcome.target.absolutePath,
                    KEY_WARNING to (outcome.warning ?: ""),
                ),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (t: Throwable) {
            Log.w(TAG, "Import failed", t)
            val message = t.message.orEmpty()
            val friendly =
                if (message.contains("EACCES") || message.contains("Permission denied")) {
                    "Move failed: HexaMesh needs \"All files access\". Try Copy instead."
                } else {
                    message.ifBlank { "Import failed." }
                }
            Result.failure(workDataOf(KEY_ERROR to friendly))
        }
    }

    private suspend fun report(
        fileName: String,
        copied: Long,
        total: Long,
    ) {
        val percent = if (total > 0) ((copied * 100) / total).toInt() else -1
        setProgress(
            workDataOf(
                KEY_FILE_NAME to fileName,
                KEY_DOWNLOADED to copied,
                KEY_TOTAL to total,
                KEY_PROGRESS to percent,
            ),
        )
        runCatching { setForeground(foregroundInfo(fileName, percent.takeIf { it >= 0 })) }
    }

    private fun foregroundInfo(
        fileName: String,
        percent: Int?,
    ): ForegroundInfo {
        ensureChannel()
        val notification =
            Notification
                .Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_hexagon)
                .setContentTitle("Importing model")
                .setContentText(fileName)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(PROGRESS_MAX, percent ?: 0, percent == null)
                .build()

        return ForegroundInfo(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    private fun ensureChannel() {
        val manager = applicationContext.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Model imports", NotificationManager.IMPORTANCE_LOW),
        )
    }

    companion object {
        const val WORK_NAME = "hexamesh-model-import"
        const val KEY_SOURCE = "sourcePath"
        const val KEY_URI = "sourceUri"
        const val KEY_MOVE = "move"
        const val KEY_FILE_NAME = "fileName"
        const val KEY_PATH = "path"
        const val KEY_WARNING = "warning"
        const val KEY_ERROR = "error"
        const val KEY_PROGRESS = "progress"
        const val KEY_DOWNLOADED = "downloaded"
        const val KEY_TOTAL = "total"

        private const val TAG = "ModelImport"
        private const val MODELS_DIR = "models"
        private const val CHANNEL_ID = "hexamesh_imports"
        private const val NOTIFICATION_ID = 3
        private const val PROGRESS_MAX = 100
    }
}
