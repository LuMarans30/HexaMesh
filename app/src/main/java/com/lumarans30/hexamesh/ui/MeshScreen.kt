package com.lumarans30.hexamesh.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lumarans30.hexamesh.R
import com.lumarans30.hexamesh.mesh.PeerNode
import com.lumarans30.hexamesh.mesh.PeerSource

/** Snapshot the Mesh screen renders. */
data class MeshUiState(
    val peers: List<PeerNode> = emptyList(),
    val fallbackPeers: String = "",
    val discoverable: Boolean = false,
    val worker: Boolean = false,
    val usePeers: Boolean = false,
)

/** Callbacks the Mesh screen raises. */
class MeshActions(
    val onApplyFallbackPeers: (String) -> Unit,
    val onDiscoverableChange: (Boolean) -> Unit,
    val onWorkerChange: (Boolean) -> Unit,
    val onUsePeersChange: (Boolean) -> Unit,
    val onObserve: suspend () -> Unit,
)

/**
 * Mesh tab: live peer discovery plus the fallback list. Discovery runs only while
 * this screen is composed, so browsing stops when the user leaves the tab.
 */
@Composable
fun meshScreen(
    state: MeshUiState,
    actions: MeshActions,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) { actions.onObserve() }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                stringResource(R.string.mesh_peers_title),
                style = MaterialTheme.typography.titleMedium,
            )
        }

        item {
            toggleRow(
                title = stringResource(R.string.mesh_discoverable),
                hint = stringResource(R.string.mesh_discoverable_hint),
                checked = state.discoverable,
                onChange = actions.onDiscoverableChange,
            )
        }

        item {
            toggleRow(
                title = stringResource(R.string.mesh_worker),
                hint = stringResource(R.string.mesh_worker_hint),
                checked = state.worker,
                onChange = actions.onWorkerChange,
            )
        }

        item {
            toggleRow(
                title = stringResource(R.string.mesh_use_peers),
                hint = stringResource(R.string.mesh_use_peers_hint),
                checked = state.usePeers,
                enabled = !state.worker,
                onChange = actions.onUsePeersChange,
            )
        }

        if (state.peers.isEmpty()) {
            item { emptyPeers() }
        } else {
            items(state.peers, key = { it.id }) { peer -> peerCard(peer) }
        }

        item { fallbackEditor(state.fallbackPeers, actions.onApplyFallbackPeers) }
    }
}

@Composable
private fun toggleRow(
    title: String,
    hint: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
        }
    }
}

@Composable
private fun peerCard(peer: PeerNode) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_mesh),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(peer.name, style = MaterialTheme.typography.titleMedium)
                val source =
                    stringResource(
                        when (peer.source) {
                            PeerSource.Discovered -> R.string.mesh_source_discovered
                            PeerSource.Manual -> R.string.mesh_source_manual
                        },
                    )
                val latency =
                    peer.stats.latencyMs
                        ?.let { " · $it ms" }
                        .orEmpty()
                Text(
                    text = "${peer.endpoint} · $source$latency",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            peer.stats.freeMemoryBytes?.let { free ->
                Text(
                    text = formatSize(free),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun emptyPeers() {
    Text(
        text = stringResource(R.string.mesh_empty),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun fallbackEditor(
    value: String,
    onApply: (String) -> Unit,
) {
    var text by rememberSaveable(value) { mutableStateOf(value) }

    Column {
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.mesh_fallback_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.mesh_fallback_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = { onApply(text) }) {
            Text(stringResource(R.string.mesh_fallback_apply))
        }
    }
}
