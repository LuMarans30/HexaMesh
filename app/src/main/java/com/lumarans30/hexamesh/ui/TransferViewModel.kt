package com.lumarans30.hexamesh.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import com.lumarans30.hexamesh.node.ModelsClient
import com.lumarans30.hexamesh.platform.AllFilesAccess
import com.lumarans30.hexamesh.platform.ApiKeyManager
import com.lumarans30.hexamesh.platform.ServerSettings
import com.lumarans30.hexamesh.platform.displayName
import com.lumarans30.hexamesh.platform.documentSize
import com.lumarans30.hexamesh.platform.realPath
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Model library plus the download/import transfers that feed it. */
data class TransferUiState(
    val models: List<Model> = emptyList(),
    val adbPushHint: String = "",
    val download: DownloadStatus? = null,
    val downloadError: String? = null,
    val importProgress: DownloadStatus? = null,
    val importError: String? = null,
    val importPrompt: ImportPrompt? = null,
)

/** Owns the model library and the download/import flows. */
class TransferViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val repository = ModelRepository(context)
    private val workManager = WorkManager.getInstance(context)
    private val settings = ServerSettings.from(context)
    private val apiKey = ApiKeyManager.getOrCreateApiKey(context)

    private val models = MutableStateFlow<List<Model>>(emptyList())
    private val importPrompt = MutableStateFlow<ImportPrompt?>(null)

    private var importCandidate: ImportCandidate? = null
    private var awaitingGrant = false
    private var grantPrompted = false

    private val downloadInfos =
        workManager.getWorkInfosForUniqueWorkFlow(ModelDownloadWorker.WORK_NAME)

    private val importInfos =
        workManager.getWorkInfosForUniqueWorkFlow(ModelImportWorker.WORK_NAME)

    val uiState: StateFlow<TransferUiState> =
        combine(
            models,
            importPrompt,
            downloadInfos,
            importInfos,
        ) { models, prompt, downloads, imports ->
            TransferUiState(
                models = models,
                adbPushHint = repository.adbPushHint(),
                download = downloads.activeTransfer(),
                downloadError = downloads.failureMessage(ModelDownloadWorker.KEY_ERROR),
                importProgress = imports.activeTransfer(),
                importError = imports.importMessage(),
                importPrompt = prompt,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, TransferUiState())

    init {
        workManager.pruneWork()
        refreshModels()
        viewModelScope.launch {
            downloadInfos.collect { infos ->
                if (infos.any { it.state == WorkInfo.State.SUCCEEDED }) onModelsChanged()
            }
        }
        viewModelScope.launch {
            importInfos.collect { infos ->
                if (infos.any { it.state == WorkInfo.State.SUCCEEDED }) onModelsChanged()
            }
        }
    }

    fun refreshModels() {
        models.value = repository.list()
    }

    fun onResumed() {
        onModelsChanged()
        if (awaitingGrant) {
            awaitingGrant = false
            importCandidate?.let { importPrompt.value = ImportPrompt.Choose(it.name, it.sizeBytes) }
        }
    }

    fun delete(model: Model) {
        repository.delete(model)
        onModelsChanged()
    }

    fun startDownload(request: DownloadRequest) {
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

    fun cancelDownload() {
        workManager.cancelUniqueWork(ModelDownloadWorker.WORK_NAME)
    }

    fun cancelImport() {
        workManager.cancelUniqueWork(ModelImportWorker.WORK_NAME)
    }

    fun onPicked(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }

        val path = realPath(uri)
        val candidate =
            ImportCandidate(
                name = displayName(context, uri) ?: path?.let { File(it).name } ?: "model.gguf",
                path = path,
                uri = uri.toString(),
                sizeBytes = path?.let { File(it).length() } ?: documentSize(context, uri) ?: -1L,
            )
        importCandidate = candidate
        grantPrompted = false
        importPrompt.value = ImportPrompt.Choose(candidate.name, candidate.sizeBytes)
    }

    fun decideImport(move: Boolean) {
        val candidate = importCandidate ?: return
        if (move && !canMove(candidate) && !grantPrompted) {
            grantPrompted = true
            importPrompt.value = ImportPrompt.Grant(candidate.name)
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
        importPrompt.value = null
        importCandidate = null
    }

    fun onGrantAccess() {
        awaitingGrant = true
        importPrompt.value = null
    }

    fun onGrantDismiss() {
        importCandidate?.let { importPrompt.value = ImportPrompt.Choose(it.name, it.sizeBytes) }
    }

    fun onImportCancel() {
        importPrompt.value = null
        importCandidate = null
    }

    private fun canMove(candidate: ImportCandidate): Boolean =
        candidate.path != null && AllFilesAccess.isGranted()

    /** A change on disk: refresh the app's list and, if serving, the router's too. */
    private fun onModelsChanged() {
        refreshModels()
        reloadRouter()
    }

    /** Best-effort: ask a running router to re-scan its models directory. */
    private fun reloadRouter() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { ModelsClient(settings.port, apiKey).reload() }
        }
    }

    private data class ImportCandidate(
        val name: String,
        val path: String?,
        val uri: String,
        val sizeBytes: Long,
    )
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

private fun List<WorkInfo>.importMessage(): String? =
    firstOrNull { it.state == WorkInfo.State.FAILED }
        ?.outputData
        ?.getString(ModelImportWorker.KEY_ERROR)
        ?: firstOrNull { it.state == WorkInfo.State.SUCCEEDED }
            ?.outputData
            ?.getString(ModelImportWorker.KEY_WARNING)
            ?.takeIf { it.isNotBlank() }
