package com.lumarans30.hexamesh.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumarans30.hexamesh.R
import com.lumarans30.hexamesh.node.NodeState

/**
 * Wires the view models to the tabbed shell. Keeps the composables free of
 * view-model lookups so they can be previewed and tested with plain state.
 */
@Composable
fun hexaMeshApp(
    nodeViewModel: NodeViewModel,
    transferViewModel: TransferViewModel,
    settingsViewModel: SettingsViewModel,
    logsViewModel: LogsViewModel,
    batteryExempt: Boolean,
    onPickModel: () -> Unit,
    onOpenAllFilesSettings: () -> Unit,
    onFixBattery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val nodeState by nodeViewModel.state.collectAsStateWithLifecycle()
    val transfer by transferViewModel.uiState.collectAsStateWithLifecycle()
    val port by settingsViewModel.port.collectAsStateWithLifecycle()
    val logLines by logsViewModel.lines.collectAsStateWithLifecycle()

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

    hexaMeshShell(
        state = state,
        actions = actions,
        port = port,
        logLines = logLines,
        tailLogs = logsViewModel::tail,
        onPortChange = settingsViewModel::setPort,
        modifier = modifier,
    )
}

/**
 * Single scaffold for the whole app: brand top bar, the persistent node action
 * bar above the bottom navigation, and one saveable tab at a time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun hexaMeshShell(
    state: ManagerUiState,
    actions: ManagerActions,
    port: Int,
    logLines: List<String>,
    tailLogs: suspend () -> Unit,
    onPortChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableStateOf(HexaTab.Manage) }
    val tabState = rememberSaveableStateHolder()

    BackHandler(enabled = tab != HexaTab.Manage) { tab = HexaTab.Manage }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(R.drawable.ic_hexagon),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.app_name))
                    }
                }
            )
        },
        bottomBar = {
            Column {
                nodeActionBar(
                    state = state.node,
                    hasSelection = state.selectedPath != null,
                    onStart = actions.onStart,
                    onStop = actions.onStop,
                )
                tabBar(selected = tab, onSelect = { tab = it })
            }
        },
    ) { innerPadding ->
        tabState.SaveableStateProvider(tab.name) {
            when (tab) {
                HexaTab.Manage -> nodeScreen(state, actions, Modifier.padding(innerPadding))

                HexaTab.Mesh ->
                    placeholderTab(
                        title = stringResource(R.string.mesh_placeholder_title),
                        body = stringResource(R.string.mesh_placeholder_body),
                        modifier = Modifier.padding(innerPadding),
                    )

                HexaTab.Logs ->
                    logsScreen(
                        lines = logLines,
                        tail = tailLogs,
                        modifier = Modifier.padding(innerPadding),
                    )

                HexaTab.Settings ->
                    settingsScreen(
                        port = port,
                        onPortChange = onPortChange,
                        modifier = Modifier.padding(innerPadding),
                    )
            }
        }
    }
}

/**
 * Start/stop control that stays reachable from every tab. Mirrors the enabled
 * rules of [canLoad] so the button never invites an invalid action.
 */
@Composable
private fun nodeActionBar(
    state: NodeState,
    hasSelection: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val busy = state is NodeState.Starting || state is NodeState.Stopping
    val running = state is NodeState.Running || state is NodeState.Idle
    val enabled =
        when {
            busy -> false
            running -> true
            else -> canLoad(state, hasSelection)
        }

    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = {
                when {
                    busy -> Unit
                    running -> onStop()
                    else -> onStart()
                }
            },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            when {
                busy -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = LocalContentColor.current,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(
                            if (state is NodeState.Starting) R.string.starting_node
                            else R.string.stopping_node
                        )
                    )
                }

                running -> {
                    Icon(painter = painterResource(R.drawable.ic_stop), contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.stop_node))
                }

                else -> {
                    Icon(painter = painterResource(R.drawable.ic_play), contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.start_node))
                }
            }
        }
    }
}

@Composable
private fun tabBar(selected: HexaTab, onSelect: (HexaTab) -> Unit) {
    NavigationBar {
        HexaTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = {
                    Icon(
                        painter = painterResource(tab.iconRes),
                        contentDescription = null,
                    )
                },
                label = { Text(stringResource(tab.labelRes)) },
            )
        }
    }
}

@Composable
private fun placeholderTab(title: String, body: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
