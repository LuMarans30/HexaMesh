package com.lumarans30.hexamesh.node

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.lumarans30.hexamesh.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads a `.gguf` into the models directory as a foreground worker, streaming
 * to a `.part` file that is renamed only when complete.
 */
class ModelDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val url = inputData.getString(KEY_URL)
        val fileName = inputData.getString(KEY_FILE_NAME)
        if (url.isNullOrBlank() || fileName.isNullOrBlank()) {
            return Result.failure(workDataOf(KEY_ERROR to "Missing download URL or file name."))
        }

        val modelsDir =
            File(applicationContext.getExternalFilesDir(null), MODELS_DIR).apply { mkdirs() }
        val target = File(modelsDir, fileName)
        val temp = File(modelsDir, "$fileName$PART_SUFFIX")

        if (target.exists()) {
            return Result.failure(
                workDataOf(KEY_ERROR to "A model named $fileName already exists."),
            )
        }

        return try {
            download(url, temp, target)
        } catch (cancellation: CancellationException) {
            temp.delete()
            throw cancellation
        } catch (t: Throwable) {
            Log.w(TAG, "Download failed", t)
            temp.delete()
            Result.failure(workDataOf(KEY_ERROR to (t.message ?: "Download failed.")))
        }
    }

    private suspend fun download(
        url: String,
        temp: File,
        target: File,
    ): Result =
        withContext(Dispatchers.IO) {
            runCatching { setForeground(foregroundInfo(target.name, percent = null)) }

            val connection =
                (URL(url).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    setRequestProperty("User-Agent", USER_AGENT)
                    connect()
                }

            try {
                val code = connection.responseCode
                if (code !in 200..299) throw IOException("Server returned HTTP $code.")

                val total = connection.contentLengthLong
                val free = temp.parentFile?.usableSpace ?: 0L
                if (total > 0 && total > free) {
                    throw IOException("Not enough free space for ${total / MIB} MiB.")
                }

                report(target.name, 0L, total)

                connection.inputStream.use { input ->
                    temp.outputStream().use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var downloaded = 0L
                        var reportedAt = 0L

                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloaded += read

                            if (downloaded - reportedAt >= PROGRESS_STEP_BYTES) {
                                reportedAt = downloaded
                                report(target.name, downloaded, total)
                            }
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }

            if (!temp.renameTo(target)) throw IOException("Could not finalise the downloaded file.")

            Result.success(
                workDataOf(KEY_FILE_NAME to target.name, KEY_PATH to target.absolutePath),
            )
        }

    private suspend fun report(
        fileName: String,
        downloaded: Long,
        total: Long,
    ) {
        val percent = if (total > 0) ((downloaded * 100) / total).toInt() else -1
        setProgress(
            workDataOf(
                KEY_FILE_NAME to fileName,
                KEY_DOWNLOADED to downloaded,
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
                .setContentTitle("Downloading model")
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
            NotificationChannel(CHANNEL_ID, "Model downloads", NotificationManager.IMPORTANCE_LOW),
        )
    }

    companion object {
        const val WORK_NAME = "hexamesh-model-download"
        const val KEY_URL = "url"
        const val KEY_FILE_NAME = "fileName"
        const val KEY_PATH = "path"
        const val KEY_ERROR = "error"
        const val KEY_PROGRESS = "progress"
        const val KEY_DOWNLOADED = "downloaded"
        const val KEY_TOTAL = "total"

        private const val TAG = "ModelDownload"
        private const val MODELS_DIR = "models"
        private const val PART_SUFFIX = ".part"
        private const val CHANNEL_ID = "hexamesh_downloads"
        private const val NOTIFICATION_ID = 2
        private const val PROGRESS_MAX = 100
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val BUFFER_SIZE = 64 * 1024
        private const val PROGRESS_STEP_BYTES = 2L * 1024 * 1024
        private const val MIB = 1024L * 1024L
        private const val USER_AGENT = "HexaMesh"
    }
}
