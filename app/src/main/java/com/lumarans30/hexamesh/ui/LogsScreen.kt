package com.lumarans30.hexamesh.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lumarans30.hexamesh.R

/**
 * Logs tab: tails the supervisor's log file while composed. [tail] blocks for as
 * long as the tab is visible, so the LaunchedEffect cancels it on the way out.
 */
@Composable
fun logsScreen(
    lines: List<String>,
    tail: suspend () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) { tail() }

    if (lines.isEmpty()) {
        logsEmpty(modifier)
    } else {
        logsList(lines, modifier)
    }
}

@Composable
private fun logsList(lines: List<String>, modifier: Modifier) {
    val listState = rememberLazyListState()

    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            last == null || last.index == info.totalItemsCount - 1
        }
    }

    LaunchedEffect(lines.size) {
        if (atBottom && lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex)
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
    ) {
        items(lines) { line ->
            Text(
                text = line,
                style =
                    MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun logsEmpty(modifier: Modifier) {
    Box(
        modifier = modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.logs_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
