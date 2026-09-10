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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lumarans30.hexamesh.R
import com.lumarans30.hexamesh.node.Model
import com.lumarans30.hexamesh.node.NodeState
import java.io.File
import java.util.Locale

/**
 * Top-level control panel: model picker, node status and the load/unload
 * control.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun nodeScreen(
    state: NodeState,
    models: List<Model>,
    selectedPath: String?,
    batteryExempt: Boolean,
    apiKey: String,
    adbPushHint: String,
    onSelect: (Model) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onFixBattery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
        ) {
            if (models.isEmpty()) {
                Text(
                    "No model found. Push a GGUF to this phone:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("  $adbPushHint", style = MaterialTheme.typography.bodySmall)
            } else {
                Text("Models", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                modelList(
                    models = models,
                    selectedPath = selectedPath,
                    enabled = selectionEnabled(state),
                    onSelect = onSelect,
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(detail(state, apiKey), style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(24.dp))

            val controls = controlsFor(state, hasSelection = selectedPath != null, onStart, onStop)
            Button(
                onClick = controls.onClick,
                enabled = controls.enabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(controls.label)
            }

            Spacer(Modifier.height(24.dp))

            batteryCard(exempt = batteryExempt, onFix = onFixBattery)
        }
    }
}

@Composable
private fun batteryCard(exempt: Boolean, onFix: () -> Unit) {
    val accent = if (exempt) batteryOkGreen else MaterialTheme.colorScheme.error

    Surface(
        color = accent.copy(alpha = 0.16f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().clickable(enabled = !exempt, onClick = onFix),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).background(accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (exempt) "✓" else "!",
                    color = Color(0xFF1B1B1B),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Spacer(Modifier.width(16.dp))

            Column {
                Text(
                    text = if (exempt) "Battery optimization off" else "Battery optimization on",
                    color = accent,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text =
                        if (exempt) {
                            "The node can keep serving in the background"
                        } else {
                            "Tap to let the node keep serving in the background"
                        },
                    color = accent.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private val batteryOkGreen = Color(0xFFB6F04A)

@Composable
private fun modelList(
    models: List<Model>,
    selectedPath: String?,
    enabled: Boolean,
    onSelect: (Model) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        models.forEach { model ->
            modelRow(
                model = model,
                selected = model.path == selectedPath,
                enabled = enabled,
                onClick = { onSelect(model) },
            )
        }
    }
}

@Composable
private fun modelRow(model: Model, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
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
                    .padding(horizontal = 8.dp, vertical = 4.dp),
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
        }
    }
}

private class Controls(val label: String, val enabled: Boolean, val onClick: () -> Unit)

@Composable
private fun controlsFor(
    state: NodeState,
    hasSelection: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
): Controls =
    when (state) {
        is NodeState.Stopped ->
            Controls(stringResource(R.string.start_node), canLoad(state, hasSelection), onStart)

        is NodeState.Error ->
            Controls(stringResource(R.string.retry_node), canLoad(state, hasSelection), onStart)

        is NodeState.Starting -> Controls(stringResource(R.string.starting_node), false) {}

        is NodeState.Stopping -> Controls(stringResource(R.string.stopping_node), false) {}

        is NodeState.Idle -> Controls(stringResource(R.string.stop_node), true, onStop)

        is NodeState.Running -> Controls(stringResource(R.string.stop_node), true, onStop)
    }

private fun detail(state: NodeState, apiKey: String): String =
    when (state) {
        is NodeState.Stopped -> "Node stopped."
        is NodeState.Starting -> "Starting llama-server..."
        is NodeState.Stopping -> "Stopping llama-server..."
        is NodeState.Idle -> "Service running, but no model to load."
        is NodeState.Running ->
            buildString {
                appendLine("Serving: ${File(state.modelPath).name}")
                appendLine()
                appendLine("Web UI: ${state.serverUrl}/")
                appendLine("OpenAI-compatible API: ${state.serverUrl}/v1")
                appendLine("API key: $apiKey")
            }

        is NodeState.Error -> "Error: ${state.message}"
    }

internal fun selectionEnabled(state: NodeState): Boolean =
    when (state) {
        is NodeState.Stopped, is NodeState.Idle, is NodeState.Error -> true
        is NodeState.Starting, is NodeState.Stopping, is NodeState.Running -> false
    }

internal fun canLoad(state: NodeState, hasSelection: Boolean): Boolean =
    hasSelection && (state is NodeState.Stopped || state is NodeState.Error)

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
