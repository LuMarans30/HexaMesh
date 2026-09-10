package com.lumarans30.hexamesh.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lumarans30.hexamesh.R
import com.lumarans30.hexamesh.node.NodeState
import java.io.File

/**
 * Top-level control panel. This is the parity rewrite of the old View-based
 * screen; model browsing and load/unload land in later units.
 */
@Composable
fun nodeScreen(
    state: NodeState,
    selectedModel: String?,
    batteryExempt: Boolean,
    apiKey: String,
    adbPushHint: String,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
            )

            Spacer(Modifier.height(16.dp))

            if (selectedModel != null) {
                Text("Model: $selectedModel", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(
                    "No model found. Push a GGUF to this phone:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("  $adbPushHint", style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(16.dp))

            Text(detail(state, apiKey), style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(24.dp))

            val controls = controlsFor(state, onStart, onStop)
            Button(
                onClick = controls.onClick,
                enabled = controls.enabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(controls.label)
            }

            Spacer(Modifier.height(24.dp))

            Text(
                "Battery optimization exempt: ${if (batteryExempt) "yes" else "no"}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Logs: adb logcat -s HexaRust MeshService LlamaServer",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private class Controls(val label: String, val enabled: Boolean, val onClick: () -> Unit)

@Composable
private fun controlsFor(state: NodeState, onStart: () -> Unit, onStop: () -> Unit): Controls =
    when (state) {
        is NodeState.Stopped -> Controls(stringResource(R.string.start_node), true, onStart)
        is NodeState.Starting -> Controls(stringResource(R.string.starting_node), false) {}
        is NodeState.Stopping -> Controls(stringResource(R.string.stopping_node), false) {}
        is NodeState.Idle -> Controls(stringResource(R.string.stop_node), true, onStop)
        is NodeState.Running -> Controls(stringResource(R.string.stop_node), true, onStop)
        is NodeState.Error -> Controls(stringResource(R.string.retry_node), true, onStart)
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
                appendLine("OpenAI-compatible endpoint:")
                appendLine(state.endpoint)
                appendLine("API Key: $apiKey")
                append("(point Open WebUI / any client at it)")
            }

        is NodeState.Error -> "Error: ${state.message}"
    }
