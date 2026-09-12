package com.lumarans30.hexamesh.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumarans30.hexamesh.R
import com.lumarans30.hexamesh.node.NodeState
import kotlinx.coroutines.launch

/** Wires the view models to the tabbed shell; the composables below take plain state. */
@Composable
fun hexaMeshApp(
    nodeViewModel: NodeViewModel,
    transferViewModel: TransferViewModel,
    settingsViewModel: SettingsViewModel,
    logsViewModel: LogsViewModel,
    meshViewModel: MeshViewModel,
    batteryExempt: Boolean,
    onPickModel: () -> Unit,
    onOpenAllFilesSettings: () -> Unit,
    onFixBattery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val nodeState by nodeViewModel.state.collectAsStateWithLifecycle()
    val transfer by transferViewModel.uiState.collectAsStateWithLifecycle()
    val launchArgs by settingsViewModel.launchArgs.collectAsStateWithLifecycle()
    val logLines by logsViewModel.lines.collectAsStateWithLifecycle()
    val nodeMetrics by logsViewModel.metrics.collectAsStateWithLifecycle()
    val meshPeers by meshViewModel.peers.collectAsStateWithLifecycle()
    val fallbackPeers by meshViewModel.fallbackPeers.collectAsStateWithLifecycle()
    val meshDiscoverable by meshViewModel.discoverable.collectAsStateWithLifecycle()
    val meshWorker by meshViewModel.worker.collectAsStateWithLifecycle()
    val meshUsePeers by meshViewModel.usePeers.collectAsStateWithLifecycle()
    val meshScanning by meshViewModel.scanning.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()

    LaunchedEffect(nodeState) {
        meshViewModel.onNodeState(nodeState)
        transferViewModel.setNodeRunning(nodeState is NodeState.Running)
    }

    val manager =
        ManagerUiState(
            node = nodeState,
            scanning = meshScanning,
            batteryExempt = batteryExempt,
            apiKey = nodeViewModel.apiKey,
            transfer = transfer,
        )

    val managerActions =
        remember(
            nodeViewModel,
            transferViewModel,
            meshViewModel,
            onPickModel,
            onOpenAllFilesSettings,
            onFixBattery,
        ) {
            ManagerActions(
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
                onStart = {
                    scope.launch {
                        meshViewModel.ensureFreshPeers()
                        nodeViewModel.start(meshViewModel.rpcEndpoints())
                    }
                },
                onStop = nodeViewModel::stop,
                onFixBattery = onFixBattery,
            )
        }

    val mesh =
        MeshUiState(
            peers = meshPeers,
            fallbackPeers = fallbackPeers,
            discoverable = meshDiscoverable,
            worker = meshWorker,
            usePeers = meshUsePeers,
        )

    val meshActions =
        remember(meshViewModel) {
            MeshActions(
                onApplyFallbackPeers = meshViewModel::applyFallbackPeers,
                onDiscoverableChange = meshViewModel::setDiscoverable,
                onWorkerChange = meshViewModel::setWorker,
                onUsePeersChange = meshViewModel::setUsePeers,
            )
        }

    val logs = LogsUiState(lines = logLines, metrics = nodeMetrics, observe = logsViewModel::observe)
    val settings = SettingsUiState(launchArgs = launchArgs, onApply = settingsViewModel::apply)

    hexaMeshShell(
        manager = manager,
        managerActions = managerActions,
        mesh = mesh,
        meshActions = meshActions,
        logs = logs,
        settings = settings,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun hexaMeshShell(
    manager: ManagerUiState,
    managerActions: ManagerActions,
    mesh: MeshUiState,
    meshActions: MeshActions,
    logs: LogsUiState,
    settings: SettingsUiState,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableStateOf(HexaTab.Manage) }
    val tabState = rememberSaveableStateHolder()
    val snackbarHostState = remember { SnackbarHostState() }

    BackHandler(enabled = tab != HexaTab.Manage) { tab = HexaTab.Manage }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                },
            )
        },
        bottomBar = {
            Column {
                nodeActionBar(
                    state = manager.node,
                    scanning = manager.scanning,
                    onStart = managerActions.onStart,
                    onStop = managerActions.onStop,
                )
                tabBar(selected = tab, onSelect = { tab = it })
            }
        },
    ) { innerPadding ->
        tabState.SaveableStateProvider(tab.name) {
            when (tab) {
                HexaTab.Manage -> {
                    nodeScreen(
                        manager,
                        managerActions,
                        snackbarHostState,
                        Modifier.padding(innerPadding),
                    )
                }

                HexaTab.Mesh -> {
                    meshScreen(mesh, meshActions, Modifier.padding(innerPadding))
                }

                HexaTab.Logs -> {
                    logsScreen(logs, Modifier.padding(innerPadding))
                }

                HexaTab.Settings -> {
                    settingsScreen(settings, snackbarHostState, Modifier.padding(innerPadding))
                }
            }
        }
    }
}

/** Start/stop control reachable from every tab; enabled rules mirror [controlsEnabled]. */
@Composable
private fun nodeActionBar(
    state: NodeState,
    scanning: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val busy = scanning || state is NodeState.Starting || state is NodeState.Stopping
    val running = state is NodeState.Running
    val enabled =
        when {
            busy -> false
            running -> true
            else -> controlsEnabled(state)
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
                            when {
                                scanning -> R.string.scanning_peers
                                state is NodeState.Starting -> R.string.starting_node
                                else -> R.string.stopping_node
                            },
                        ),
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
private fun tabBar(
    selected: HexaTab,
    onSelect: (HexaTab) -> Unit,
) {
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
