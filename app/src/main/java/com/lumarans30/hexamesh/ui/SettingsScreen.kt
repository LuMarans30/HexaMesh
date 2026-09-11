package com.lumarans30.hexamesh.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lumarans30.hexamesh.R
import com.lumarans30.hexamesh.platform.DEFAULT_LAUNCH_ARGS
import com.lumarans30.hexamesh.platform.lockedLaunchFlags
import com.lumarans30.hexamesh.platform.parseLaunchArgs
import kotlinx.coroutines.launch

/**
 * Settings tab: the launch args text is the single source of truth, port
 * included. The node reads it on each start, so saving never disturbs a running
 * server.
 */
@Composable
fun settingsScreen(
    launchArgs: String,
    onApply: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    var argsText by rememberSaveable { mutableStateOf(launchArgs) }
    val lockedFlags = lockedLaunchFlags(parseLaunchArgs(argsText))
    val scope = rememberCoroutineScope()
    val savedMessage = stringResource(R.string.args_saved)
    val defaultsMessage = stringResource(R.string.defaults_restored)

    fun save(text: String, message: String) {
        onApply(text)
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        lanWarningCard()

        Column {
            Text(
                stringResource(R.string.launch_arguments),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.launch_arguments_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = argsText,
                onValueChange = { argsText = it },
                label = { Text(stringResource(R.string.arguments)) },
                minLines = 3,
                isError = lockedFlags.isNotEmpty(),
                supportingText =
                    if (lockedFlags.isNotEmpty()) {
                        {
                            Text(
                                stringResource(
                                    R.string.managed_flags_ignored,
                                    lockedFlags.joinToString(" "),
                                )
                            )
                        }
                    } else {
                        null
                    },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { save(argsText, savedMessage) },
                    enabled = lockedFlags.isEmpty(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.apply))
                }
                TextButton(
                    onClick = {
                        argsText = DEFAULT_LAUNCH_ARGS
                        save(DEFAULT_LAUNCH_ARGS, defaultsMessage)
                    }
                ) {
                    Text(stringResource(R.string.reset_defaults))
                }
            }
        }
    }
}

@Composable
private fun lanWarningCard() {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.lan_warning_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.lan_warning_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}
