package com.lumarans30.hexamesh.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Wires the Manage screen to its view models. Keeps the composable free of
 * view-model lookups so it can be previewed and tested with plain state.
 */
@Composable
fun ManagerRoute(
    nodeViewModel: NodeViewModel,
    transferViewModel: TransferViewModel,
    batteryExempt: Boolean,
    onPickModel: () -> Unit,
    onOpenAllFilesSettings: () -> Unit,
    onFixBattery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val nodeState by nodeViewModel.state.collectAsStateWithLifecycle()
    val transfer by transferViewModel.uiState.collectAsStateWithLifecycle()

    val state =
        ManagerUiState(
            node = nodeState,
            models = transfer.models,
            selectedPath = transfer.selectedPath,
            batteryExempt = batteryExempt,
            apiKey = nodeViewModel.apiKey,
            adbPushHint = transfer.adbPushHint,
            download = transfer.download,
            downloadError = transfer.downloadError,
            importPrompt = transfer.importPrompt,
            importProgress = transfer.importProgress,
            importError = transfer.importError,
        )

    val actions =
        remember(
            nodeViewModel,
            transferViewModel,
            onPickModel,
            onOpenAllFilesSettings,
            onFixBattery,
        ) {
            ManagerActions(
                onSelect = transferViewModel::select,
                onDelete = transferViewModel::delete,
                onDownload = transferViewModel::startDownload,
                onCancelDownload = transferViewModel::cancelDownload,
                onImport = onPickModel,
                onCancelImport = transferViewModel::cancelImport,
                onImportCopy = { transferViewModel.decideImport(move = false) },
                onImportMove = { transferViewModel.decideImport(move = true) },
                onImportCancel = transferViewModel::onImportCancel,
                onGrantAccess = {
                    transferViewModel.onGrantAccess()
                    onOpenAllFilesSettings()
                },
                onGrantDismiss = transferViewModel::onGrantDismiss,
                onStart = { nodeViewModel.start(transferViewModel.uiState.value.selectedPath) },
                onStop = nodeViewModel::stop,
                onFixBattery = onFixBattery,
            )
        }

    nodeScreen(state = state, actions = actions, modifier = modifier)
}
