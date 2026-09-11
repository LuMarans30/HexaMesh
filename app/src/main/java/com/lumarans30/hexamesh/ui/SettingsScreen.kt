package com.lumarans30.hexamesh.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lumarans30.hexamesh.R
import com.lumarans30.hexamesh.platform.MAX_SERVER_PORT
import com.lumarans30.hexamesh.platform.MIN_SERVER_PORT
import com.lumarans30.hexamesh.platform.parsePort

/**
 * Settings tab: the persisted `llama-server` port. The node reads it on each
 * start, so saving a new value never disturbs a running server.
 */
@Composable
fun settingsScreen(
    port: Int,
    onPortChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by rememberSaveable(port) { mutableStateOf(port.toString()) }
    var invalid by remember { mutableStateOf(false) }

    fun submit() {
        val parsed = parsePort(editing)
        when {
            parsed == null -> invalid = true
            parsed != port -> onPortChange(parsed)
        }
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
            Text(stringResource(R.string.server_port), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.server_port_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = editing,
                onValueChange = {
                    editing = it
                    invalid = false
                },
                label = { Text(stringResource(R.string.server_port)) },
                singleLine = true,
                isError = invalid,
                supportingText =
                    if (invalid) {
                        { Text(stringResource(R.string.invalid_port, MIN_SERVER_PORT, MAX_SERVER_PORT)) }
                    } else {
                        null
                    },
                keyboardOptions =
                    KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { submit() },
                enabled = editing != port.toString() && !invalid,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.apply))
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
