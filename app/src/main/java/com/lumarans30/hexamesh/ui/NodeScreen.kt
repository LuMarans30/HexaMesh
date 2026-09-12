package com.lumarans30.hexamesh.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lumarans30.hexamesh.R
import com.lumarans30.hexamesh.node.DownloadRequest
import com.lumarans30.hexamesh.node.Model
import com.lumarans30.hexamesh.node.NodeState
import com.lumarans30.hexamesh.node.parseDownloadRequest
import java.io.File
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Manage tab: model picker, node status and the transfer/diagnostics panels.
 * The shell owns the top bar, the persistent action bar and the bottom nav.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun nodeScreen(
    state: ManagerUiState,
    actions: ManagerActions,
    modifier: Modifier = Modifier,
) {
    val nodeState = state.node
    val activeModelPath = (nodeState as? NodeState.Running)?.modelPath
    var pendingDelete by remember { mutableStateOf<Model?>(null) }
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    fun closeSheet() {
        scope
            .launch { sheetState.hide() }
            .invokeOnCompletion { if (!sheetState.isVisible) sheetOpen = false }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
    ) {
        statusHero(nodeState, state.apiKey)

        Spacer(Modifier.height(24.dp))

        modelsHeader(onAdd = { sheetOpen = true })

        Spacer(Modifier.height(8.dp))

        if (state.models.isEmpty()) {
            Text(
                stringResource(R.string.no_model_found),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text("  ${state.adbPushHint}", style = MaterialTheme.typography.bodySmall)
        } else {
            modelList(
                models = state.models,
                selectedPath = state.selectedPath,
                enabled = selectionEnabled(nodeState),
                canDelete = { canDelete(nodeState, it, activeModelPath) },
                onSelect = actions.onSelect,
                onDelete = { pendingDelete = it },
            )
        }

        Spacer(Modifier.height(16.dp))

        transfersSection(
            download = state.download,
            downloadError = state.downloadError,
            importProgress = state.importProgress,
            importError = state.importError,
            onCancelDownload = actions.onCancelDownload,
            onCancelImport = actions.onCancelImport,
        )

        Spacer(Modifier.height(16.dp))

        diagnostics(exempt = state.batteryExempt, onFix = actions.onFixBattery)

        Spacer(Modifier.height(24.dp))
    }

    if (sheetOpen) {
        ModalBottomSheet(onDismissRequest = { sheetOpen = false }, sheetState = sheetState) {
            ingestionSheet(
                download = state.download,
                importEnabled = state.importPrompt == null && state.importProgress == null,
                onDownload = { request ->
                    actions.onDownload(request)
                    closeSheet()
                },
                onImport = {
                    closeSheet()
                    actions.onImport()
                },
            )
        }
    }

    pendingDelete?.let { model ->
        deleteDialog(
            model = model,
            onConfirm = {
                actions.onDelete(model)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }

    state.importPrompt?.let { prompt ->
        when (prompt) {
            is ImportPrompt.Choose ->
                importChoiceDialog(
                    prompt = prompt,
                    onCopy = actions.onImportCopy,
                    onMove = actions.onImportMove,
                    onDismiss = actions.onImportCancel,
                )

            is ImportPrompt.Grant ->
                grantAccessDialog(
                    name = prompt.name,
                    onOpenSettings = actions.onGrantAccess,
                    onDismiss = actions.onGrantDismiss,
                )
        }
    }
}

@Composable
private fun modelsHeader(onAdd: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(stringResource(R.string.models), style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = onAdd) {
            Icon(
                painter = painterResource(R.drawable.ic_add),
                contentDescription = stringResource(R.string.add_model),
            )
        }
    }
}

@Composable
private fun transfersSection(
    download: DownloadStatus?,
    downloadError: String?,
    importProgress: DownloadStatus?,
    importError: String?,
    onCancelDownload: () -> Unit,
    onCancelImport: () -> Unit,
) {
    if (download == null && downloadError == null && importProgress == null && importError == null) {
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        download?.let {
            transferCard(
                title = stringResource(R.string.downloading_model),
                status = it,
                onCancel = onCancelDownload,
            )
        }
        downloadError?.let { errorText(it) }
        importProgress?.let {
            transferCard(
                title = stringResource(R.string.importing_model),
                status = it,
                onCancel = onCancelImport,
            )
        }
        importError?.let { errorText(it) }
    }
}

@Composable
private fun transferCard(title: String, status: DownloadStatus, onCancel: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
            }
            LinearProgressIndicator(progress = { status.fraction }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(4.dp))
            Text(
                text = status.fileName,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(status.label, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun errorText(message: String) {
    Text(
        text = message,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun diagnostics(exempt: Boolean, onFix: () -> Unit) {
    if (!exempt) {
        batteryWarningCard(onFix = onFix)
    }
}

@Composable
private fun batteryWarningCard(onFix: () -> Unit) {
    val accent = MaterialTheme.colorScheme.error

    Surface(
        color = accent.copy(alpha = 0.16f),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onFix),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).background(accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "!",
                    color = Color(0xFF1B1B1B),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Spacer(Modifier.width(16.dp))

            Column {
                Text(
                    text = stringResource(R.string.battery_optimization_on),
                    color = accent,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.battery_on_hint),
                    color = accent.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun ingestionSheet(
    download: DownloadStatus?,
    importEnabled: Boolean,
    onDownload: (DownloadRequest) -> Unit,
    onImport: () -> Unit,
) {
    var input by rememberSaveable { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }
    val invalidMessage = stringResource(R.string.invalid_model_url)

    fun submit() {
        val request = parseDownloadRequest(input)
        if (request == null) {
            validationError = invalidMessage
        } else {
            onDownload(request)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().imePadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.add_model), style = MaterialTheme.typography.titleLarge)

        OutlinedTextField(
            value = input,
            onValueChange = {
                input = it
                validationError = null
            },
            label = { Text(stringResource(R.string.model_url)) },
            placeholder = { Text(stringResource(R.string.model_url_hint)) },
            singleLine = true,
            isError = validationError != null,
            supportingText = validationError?.let { message -> { Text(message) } },
            enabled = download == null,
            trailingIcon = {
                IconButton(
                    onClick = { submit() },
                    enabled = input.isNotBlank() && download == null,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_download),
                        contentDescription = stringResource(R.string.download),
                    )
                }
            },
            keyboardOptions =
                KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedButton(
            onClick = onImport,
            enabled = importEnabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.import_from_storage))
        }
    }
}

@Composable
private fun modelList(
    models: List<Model>,
    selectedPath: String?,
    enabled: Boolean,
    canDelete: (Model) -> Boolean,
    onSelect: (Model) -> Unit,
    onDelete: (Model) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        models.forEach { model ->
            modelRow(
                model = model,
                selected = model.path == selectedPath,
                enabled = enabled,
                deleteEnabled = canDelete(model),
                onClick = { onSelect(model) },
                onDelete = { onDelete(model) },
            )
        }
    }
}

@Composable
private fun modelRow(
    model: Model,
    selected: Boolean,
    enabled: Boolean,
    deleteEnabled: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        color =
            if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .alpha(if (enabled) 1f else 0.45f)
                    .clickable(enabled = enabled, onClick = onClick)
                    .padding(start = 8.dp, top = 4.dp, end = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onClick, enabled = enabled)
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(
                    text = model.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(formatSize(model.sizeBytes), style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onDelete, enabled = deleteEnabled) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = stringResource(R.string.delete_model, model.name),
                    tint =
                        if (deleteEnabled) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
        }
    }
}

@Composable
private fun deleteDialog(model: Model, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_model_title)) },
        text = { Text(stringResource(R.string.delete_model_message, model.name)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun importChoiceDialog(
    prompt: ImportPrompt.Choose,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import model") },
        text = {
            Text(
                if (prompt.sizeBytes > 0) {
                    "${prompt.name} · ${formatSize(prompt.sizeBytes)}"
                } else {
                    prompt.name
                }
            )
        },
        confirmButton = { TextButton(onClick = onMove) { Text("Move") } },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(onClick = onCopy) { Text("Copy") }
            }
        },
    )
}

@Composable
private fun grantAccessDialog(name: String, onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move needs \"All files access\"") },
        text = {
            Text(
                "Android only lets apps delete files in shared storage with this permission, " +
                        "which is what lets HexaMesh move $name instead of copying it."
            )
        },
        confirmButton = { TextButton(onClick = onOpenSettings) { Text("Open settings") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}

@Composable
private fun statusHero(state: NodeState, apiKey: String) {
    when (state) {
        is NodeState.Running ->
            servingCard(
                modelName = if (state.router) "all models" else File(state.modelPath).name,
                serverUrl = state.serverUrl,
                apiKey = apiKey,
            )

        is NodeState.Stopped ->
            statusCard(
                accent = MaterialTheme.colorScheme.onSurfaceVariant,
                title = "Node stopped",
                subtitle = "Select a model, then tap Start Node.",
                symbol = "○",
            )

        is NodeState.Idle ->
            statusCard(
                accent = MaterialTheme.colorScheme.tertiary,
                title = "Service running",
                subtitle = "No model selected to load.",
                symbol = "○",
            )

        is NodeState.Starting ->
            statusCard(
                accent = MaterialTheme.colorScheme.primary,
                title = "Starting llama-server",
                subtitle =
                    if (state.router) "Loading models from the models folder…"
                    else "Loading ${File(state.modelPath).name}…",
                busy = true,
            )

        is NodeState.Stopping ->
            statusCard(
                accent = MaterialTheme.colorScheme.primary,
                title = "Stopping llama-server",
                subtitle = "Shutting down…",
                busy = true,
            )

        is NodeState.Error ->
            statusCard(
                accent = MaterialTheme.colorScheme.error,
                title = "Node failed",
                subtitle = state.message,
                symbol = "!",
            )
    }
}

@Composable
private fun statusCard(
    accent: Color,
    title: String,
    subtitle: String,
    symbol: String? = null,
    busy: Boolean = false,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(44.dp).background(accent.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = accent,
                    )
                } else {
                    Text(
                        text = symbol.orEmpty(),
                        color = accent,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun servingCard(modelName: String, serverUrl: String, apiKey: String) {
    val accent = MaterialTheme.colorScheme.primary

    Surface(
        color = accent.copy(alpha = 0.16f),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(40.dp).background(accent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "✓",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                Spacer(Modifier.width(16.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Node is serving",
                        color = accent,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = modelName,
                        color = accent.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            HorizontalDivider(color = accent.copy(alpha = 0.25f))

            Spacer(Modifier.height(16.dp))

            connectionRow("Web UI", "$serverUrl/", accent)
            Spacer(Modifier.height(12.dp))
            connectionRow("API", "$serverUrl/v1", accent)
            Spacer(Modifier.height(12.dp))
            connectionRow("API key", apiKey, accent)
        }
    }
}

@Composable
private fun connectionRow(label: String, value: String, accent: Color) {
    Column {
        Text(
            text = label,
            color = accent.copy(alpha = 0.6f),
            style = MaterialTheme.typography.labelMedium,
        )
        Spacer(Modifier.height(2.dp))
        SelectionContainer {
            Text(
                text = value,
                color = accent,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            )
        }
    }
}

internal fun selectionEnabled(state: NodeState): Boolean =
    when (state) {
        is NodeState.Stopped, is NodeState.Idle, is NodeState.Error -> true
        is NodeState.Starting, is NodeState.Stopping, is NodeState.Running -> false
    }

internal fun canDelete(state: NodeState, model: Model, activeModelPath: String?): Boolean =
    selectionEnabled(state) && model.path != activeModelPath

internal fun canLoad(state: NodeState, hasSelection: Boolean, routerMode: Boolean = false): Boolean =
    (routerMode || hasSelection) && (state is NodeState.Stopped || state is NodeState.Error)

internal fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"

    val units = arrayOf("KiB", "MiB", "GiB", "TiB")
    var value = bytes.toDouble() / 1024.0
    var unit = 0
    while (value >= 1024.0 && unit < units.lastIndex) {
        value /= 1024.0
        unit++
    }

    return String.format(Locale.US, "%.1f %s", value, units[unit])
}
