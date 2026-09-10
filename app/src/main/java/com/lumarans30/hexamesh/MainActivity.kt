package com.lumarans30.hexamesh

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.lumarans30.hexamesh.node.DownloadRequest
import com.lumarans30.hexamesh.node.Model
import com.lumarans30.hexamesh.node.ModelDownloadWorker
import com.lumarans30.hexamesh.node.ModelRepository
import com.lumarans30.hexamesh.node.NodeState
import com.lumarans30.hexamesh.platform.ApiKeyManager
import com.lumarans30.hexamesh.ui.DownloadStatus
import com.lumarans30.hexamesh.ui.hexaMeshTheme
import com.lumarans30.hexamesh.ui.nodeScreen

/** Thin control panel for the headless node. */
class MainActivity : ComponentActivity() {

    private lateinit var repository: ModelRepository
    private lateinit var workManager: WorkManager

    private var available by mutableStateOf<List<Model>>(emptyList())
    private var selectedPath by mutableStateOf<String?>(null)
    private var batteryExempt by mutableStateOf(false)

    private val apiKey by lazy { ApiKeyManager.getOrCreateApiKey(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        repository = ModelRepository(this)
        workManager = WorkManager.getInstance(this)
        refreshModels()
        refreshBatteryStatus()

        setContent {
            hexaMeshTheme {
                val state by NodeState.current.collectAsState()
                val workInfos by
                remember {
                    workManager.getWorkInfosForUniqueWorkFlow(ModelDownloadWorker.WORK_NAME)
                }
                    .collectAsState(emptyList())

                LaunchedEffect(workInfos) {
                    if (workInfos.any { it.state == WorkInfo.State.SUCCEEDED }) refreshModels()
                }

                nodeScreen(
                    state = state,
                    models = available,
                    selectedPath = selectedPath,
                    batteryExempt = batteryExempt,
                    apiKey = apiKey,
                    adbPushHint = repository.adbPushHint(),
                    download =
                        workInfos
                            .firstOrNull {
                                it.state == WorkInfo.State.RUNNING ||
                                        it.state == WorkInfo.State.ENQUEUED
                            }
                            ?.let { info ->
                                DownloadStatus(
                                    fileName =
                                        info.progress.getString(ModelDownloadWorker.KEY_FILE_NAME)
                                            ?: "model.gguf",
                                    downloaded =
                                        info.progress.getLong(ModelDownloadWorker.KEY_DOWNLOADED, 0L),
                                    total =
                                        info.progress.getLong(ModelDownloadWorker.KEY_TOTAL, -1L),
                                )
                            },
                    downloadError =
                        workInfos
                            .firstOrNull { it.state == WorkInfo.State.FAILED }
                            ?.outputData
                            ?.getString(ModelDownloadWorker.KEY_ERROR),
                    onSelect = ::selectModel,
                    onDelete = ::deleteModel,
                    onDownload = ::startDownload,
                    onCancelDownload = ::cancelDownload,
                    onStart = ::startMeshService,
                    onStop = ::stopMeshService,
                    onFixBattery = ::requestIgnoreBatteryOptimizations,
                )
            }
        }

        requestNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        refreshModels()
        refreshBatteryStatus()
    }

    private fun refreshBatteryStatus() {
        batteryExempt = isIgnoringBatteryOptimizations()
    }

    private fun refreshModels() {
        available = repository.list()
        selectedPath = repository.selected()?.path
    }

    private fun selectModel(model: Model) {
        repository.select(model)
        selectedPath = model.path
    }

    private fun deleteModel(model: Model) {
        repository.delete(model)
        refreshModels()
    }

    private fun startDownload(request: DownloadRequest) {
        val work =
            OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                .setInputData(
                    workDataOf(
                        ModelDownloadWorker.KEY_URL to request.url,
                        ModelDownloadWorker.KEY_FILE_NAME to request.fileName,
                    )
                )
                .build()

        workManager.enqueueUniqueWork(ModelDownloadWorker.WORK_NAME, ExistingWorkPolicy.KEEP, work)
    }

    private fun cancelDownload() {
        workManager.cancelUniqueWork(ModelDownloadWorker.WORK_NAME)
    }

    private fun isIgnoringBatteryOptimizations(): Boolean =
        getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(packageName) == true

    private fun startMeshService() {
        val intent = Intent(this, MeshService::class.java)
        repository.selected()?.let { intent.putExtra(MeshService.EXTRA_MODEL_PATH, it.path) }
        startForegroundService(intent)
    }

    private fun stopMeshService() {
        val intent =
            Intent(this, MeshService::class.java).apply { action = MeshService.ACTION_STOP }
        startService(intent)
    }

    private fun requestNotificationPermission() {
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQ_NOTIFICATIONS,
            )
        }
    }

    private fun requestIgnoreBatteryOptimizations() {
        val pm = getSystemService(PowerManager::class.java) ?: return
        if (pm.isIgnoringBatteryOptimizations(packageName)) return

        try {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName"),
                )
            )
        } catch (_: Exception) {
            // Some OEM builds remove this settings screen.
        }
    }

    companion object {
        private const val REQ_NOTIFICATIONS = 1001
    }
}