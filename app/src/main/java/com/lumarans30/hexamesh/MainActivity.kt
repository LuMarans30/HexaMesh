package com.lumarans30.hexamesh

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import com.lumarans30.hexamesh.node.ModelImportWorker
import com.lumarans30.hexamesh.node.ModelRepository
import com.lumarans30.hexamesh.node.NodeState
import com.lumarans30.hexamesh.platform.AllFilesAccess
import com.lumarans30.hexamesh.platform.ApiKeyManager
import com.lumarans30.hexamesh.platform.displayName
import com.lumarans30.hexamesh.platform.documentSize
import com.lumarans30.hexamesh.platform.realPath
import com.lumarans30.hexamesh.ui.DownloadStatus
import com.lumarans30.hexamesh.ui.ImportPrompt
import com.lumarans30.hexamesh.ui.hexaMeshTheme
import com.lumarans30.hexamesh.ui.nodeScreen
import java.io.File

/** Thin control panel for the headless node. */
class MainActivity : ComponentActivity() {

    private lateinit var repository: ModelRepository
    private lateinit var workManager: WorkManager
    private val batteryHandler = Handler(Looper.getMainLooper())

    private var available by mutableStateOf<List<Model>>(emptyList())
    private var selectedPath by mutableStateOf<String?>(null)
    private var batteryExempt by mutableStateOf(false)

    private var importCandidate by mutableStateOf<ImportCandidate?>(null)
    private var importPrompt by mutableStateOf<ImportPrompt?>(null)
    private var awaitingGrant by mutableStateOf(false)
    private var grantPrompted = false

    private val apiKey by lazy { ApiKeyManager.getOrCreateApiKey(this) }

    private val pickModel =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) onPicked(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        repository = ModelRepository(this)
        workManager = WorkManager.getInstance(this)
        workManager.pruneWork()
        refreshModels()
        refreshBatteryStatus()

        setContent {
            hexaMeshTheme {
                val state by NodeState.current.collectAsState()
                val downloadInfos by
                remember {
                    workManager.getWorkInfosForUniqueWorkFlow(ModelDownloadWorker.WORK_NAME)
                }
                    .collectAsState(emptyList())
                val importInfos by
                remember {
                    workManager.getWorkInfosForUniqueWorkFlow(ModelImportWorker.WORK_NAME)
                }
                    .collectAsState(emptyList())

                LaunchedEffect(downloadInfos) {
                    if (downloadInfos.any { it.state == WorkInfo.State.SUCCEEDED }) refreshModels()
                }

                LaunchedEffect(importInfos) {
                    if (importInfos.any { it.state == WorkInfo.State.SUCCEEDED }) refreshModels()
                }

                val importMessage =
                    importInfos
                        .firstOrNull { it.state == WorkInfo.State.FAILED }
                        ?.outputData
                        ?.getString(ModelImportWorker.KEY_ERROR)
                        ?: importInfos
                            .firstOrNull { it.state == WorkInfo.State.SUCCEEDED }
                            ?.outputData
                            ?.getString(ModelImportWorker.KEY_WARNING)
                            ?.takeIf { it.isNotBlank() }

                nodeScreen(
                    state = state,
                    models = available,
                    selectedPath = selectedPath,
                    batteryExempt = batteryExempt,
                    apiKey = apiKey,
                    adbPushHint = repository.adbPushHint(),
                    download = downloadInfos.activeTransfer(),
                    downloadError = downloadInfos.failureMessage(ModelDownloadWorker.KEY_ERROR),
                    importPrompt = importPrompt,
                    importProgress = importInfos.activeTransfer(),
                    importError = importMessage,
                    onSelect = ::selectModel,
                    onDelete = ::deleteModel,
                    onDownload = ::startDownload,
                    onCancelDownload = ::cancelDownload,
                    onImport = { pickModel.launch(arrayOf("*/*")) },
                    onCancelImport = { workManager.cancelUniqueWork(ModelImportWorker.WORK_NAME) },
                    onImportCopy = { decideImport(move = false) },
                    onImportMove = { decideImport(move = true) },
                    onImportCancel = {
                        importPrompt = null
                        importCandidate = null
                    },
                    onGrantAccess = ::openAllFilesSettings,
                    onGrantDismiss = {
                        importPrompt =
                            importCandidate?.let {
                                ImportPrompt.Choose(it.name, it.sizeBytes)
                            }
                    },
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
        scheduleBatteryRefresh()

        if (awaitingGrant) {
            awaitingGrant = false
            importCandidate?.let {
                importPrompt =
                    ImportPrompt.Choose(it.name, it.sizeBytes)
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) scheduleBatteryRefresh()
    }

    override fun onDestroy() {
        super.onDestroy()
        batteryHandler.removeCallbacksAndMessages(null)
    }

    private fun onPicked(uri: Uri) {
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }

        val path = realPath(uri)
        val candidate =
            ImportCandidate(
                name = displayName(this, uri) ?: path?.let { File(it).name } ?: "model.gguf",
                path = path,
                uri = uri.toString(),
                sizeBytes = path?.let { File(it).length() } ?: documentSize(this, uri) ?: -1L,
            )
        importCandidate = candidate
        grantPrompted = false
        importPrompt = ImportPrompt.Choose(candidate.name, candidate.sizeBytes)
    }

    private fun canMove(candidate: ImportCandidate): Boolean =
        candidate.path != null && AllFilesAccess.isGranted()

    private fun decideImport(move: Boolean) {
        val candidate = importCandidate ?: return
        if (move && !canMove(candidate) && !grantPrompted) {
            grantPrompted = true
            importPrompt = ImportPrompt.Grant(candidate.name)
            return
        }

        val work =
            OneTimeWorkRequestBuilder<ModelImportWorker>()
                .setInputData(
                    workDataOf(
                        ModelImportWorker.KEY_SOURCE to (candidate.path ?: ""),
                        ModelImportWorker.KEY_URI to candidate.uri,
                        ModelImportWorker.KEY_FILE_NAME to candidate.name,
                        ModelImportWorker.KEY_MOVE to move,
                        ModelImportWorker.KEY_TOTAL to candidate.sizeBytes,
                    )
                )
                .build()

        workManager.enqueueUniqueWork(ModelImportWorker.WORK_NAME, ExistingWorkPolicy.KEEP, work)
        importPrompt = null
        importCandidate = null
    }

    private fun openAllFilesSettings() {
        awaitingGrant = true
        importPrompt = null
        try {
            startActivity(AllFilesAccess.settingsIntent(this))
        } catch (_: ActivityNotFoundException) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            } catch (_: Exception) {
                awaitingGrant = false
            }
        }
    }

    private fun refreshBatteryStatus() {
        batteryExempt = isIgnoringBatteryOptimizations()
    }

    /**
     * The battery-optimization screen can commit the whitelist change a moment
     * after we regain focus
     */
    private fun scheduleBatteryRefresh() {
        batteryHandler.removeCallbacksAndMessages(null)
        refreshBatteryStatus()
        for (delay in BATTERY_REFRESH_RETRY_MS) {
            batteryHandler.postDelayed({ refreshBatteryStatus() }, delay)
        }
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

    private data class ImportCandidate(
        val name: String,
        val path: String?,
        val uri: String,
        val sizeBytes: Long,
    )

    companion object {
        private const val REQ_NOTIFICATIONS = 1001
        private val BATTERY_REFRESH_RETRY_MS = longArrayOf(300, 900, 1800, 3000)
    }
}

private fun List<WorkInfo>.activeTransfer(): DownloadStatus? =
    firstOrNull { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
        ?.let { info ->
            DownloadStatus(
                fileName = info.progress.getString(ModelDownloadWorker.KEY_FILE_NAME) ?: "model.gguf",
                downloaded = info.progress.getLong(ModelDownloadWorker.KEY_DOWNLOADED, 0L),
                total = info.progress.getLong(ModelDownloadWorker.KEY_TOTAL, -1L),
            )
        }

private fun List<WorkInfo>.failureMessage(key: String): String? =
    firstOrNull { it.state == WorkInfo.State.FAILED }?.outputData?.getString(key)
