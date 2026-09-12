package com.lumarans30.hexamesh.ui

import com.lumarans30.hexamesh.node.DownloadRequest
import com.lumarans30.hexamesh.node.Model
import com.lumarans30.hexamesh.node.NodeState

/**
 * Everything the Manage screen renders, gathered into one immutable snapshot so
 * the screen takes a state object instead of a couple dozen arguments.
 */
data class ManagerUiState(
    val node: NodeState = NodeState.Stopped,
    val models: List<Model> = emptyList(),
    val selectedPath: String? = null,
    val routerMode: Boolean = false,
    val batteryExempt: Boolean = false,
    val apiKey: String = "",
    val adbPushHint: String = "",
    val download: DownloadStatus? = null,
    val downloadError: String? = null,
    val importPrompt: ImportPrompt? = null,
    val importProgress: DownloadStatus? = null,
    val importError: String? = null,
)

/** Callbacks the Manage screen raises, grouped so the screen signature stays small. */
class ManagerActions(
    val onSelect: (Model) -> Unit,
    val onDelete: (Model) -> Unit,
    val onRouterModeChange: (Boolean) -> Unit,
    val onDownload: (DownloadRequest) -> Unit,
    val onCancelDownload: () -> Unit,
    val onImport: () -> Unit,
    val onCancelImport: () -> Unit,
    val onImportCopy: () -> Unit,
    val onImportMove: () -> Unit,
    val onImportCancel: () -> Unit,
    val onGrantAccess: () -> Unit,
    val onGrantDismiss: () -> Unit,
    val onStart: () -> Unit,
    val onStop: () -> Unit,
    val onFixBattery: () -> Unit,
)

data class DownloadStatus(val fileName: String, val downloaded: Long, val total: Long) {
    val fraction: Float
        get() = if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f

    val label: String
        get() =
            if (total > 0) {
                "${(fraction * 100).toInt()}% · ${formatSize(downloaded)} / ${formatSize(total)}"
            } else {
                formatSize(downloaded)
            }
}

sealed interface ImportPrompt {
    val name: String

    data class Choose(override val name: String, val sizeBytes: Long) : ImportPrompt

    data class Grant(override val name: String) : ImportPrompt
}
