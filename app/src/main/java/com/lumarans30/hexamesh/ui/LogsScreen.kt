package com.lumarans30.hexamesh.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.lumarans30.hexamesh.logs.MemoryInfo
import com.lumarans30.hexamesh.logs.NodeMetrics
import java.util.Locale

/**
 * Logs tab: a diagnostics strip over a live terminal. [observe] suspends for as
 * long as the tab is visible, so the LaunchedEffect cancels it on the way out.
 */
@Composable
fun logsScreen(
    lines: List<String>,
    metrics: NodeMetrics,
    observe: suspend () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) { observe() }

    Column(modifier = modifier.fillMaxSize()) {
        metricsPanel(metrics)

        if (lines.isEmpty()) {
            logsEmpty(Modifier.weight(1f))
        } else {
            logsList(lines, Modifier.weight(1f))
        }
    }
}

@Composable
private fun metricsPanel(metrics: NodeMetrics) {
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            metricValue(stringResource(R.string.metric_tok_s), rate(metrics.predictedPerSecond))
            metricValue(stringResource(R.string.metric_temp), degrees(metrics.temperatureCelsius))
            metricValue(stringResource(R.string.metric_free_mem), memory(metrics.memory))
        }
    }
}

@Composable
private fun metricValue(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        )
    }
}

private fun rate(value: Double?): String =
    if (value == null) UNAVAILABLE else String.format(Locale.US, "%.1f", value)

private fun degrees(value: Double?): String =
    if (value == null) UNAVAILABLE else String.format(Locale.US, "%.1f\u00b0C", value)

private fun memory(info: MemoryInfo?): String =
    if (info == null) {
        UNAVAILABLE
    } else {
        String.format(
            Locale.US,
            "%.1f / %.1f GiB",
            info.availableBytes / GIB_BYTES,
            info.totalBytes / GIB_BYTES,
        )
    }

private const val GIB_BYTES = 1024.0 * 1024.0 * 1024.0

private const val UNAVAILABLE = "--"

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
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
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
