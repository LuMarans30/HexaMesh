package com.lumarans30.hexamesh.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.lumarans30.hexamesh.logs.LLAMA_SERVER_LOG_NAME
import com.lumarans30.hexamesh.logs.LineBuffer
import com.lumarans30.hexamesh.logs.LogTailer
import com.lumarans30.hexamesh.logs.NodeMetrics
import com.lumarans30.hexamesh.logs.SlotsClient
import com.lumarans30.hexamesh.logs.TailEvent
import com.lumarans30.hexamesh.logs.ThermalZones
import com.lumarans30.hexamesh.logs.hottestCelsius
import com.lumarans30.hexamesh.logs.readMemoryInfo
import com.lumarans30.hexamesh.logs.tokensPerSecond
import com.lumarans30.hexamesh.node.ModelsClient
import com.lumarans30.hexamesh.platform.ApiKeyManager
import com.lumarans30.hexamesh.platform.ServerSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

/** Streams the log file and the node diagnostics while the Logs tab is visible. */
class LogsViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val buffer = LineBuffer()

    val lines: StateFlow<List<String>> = buffer.lines

    private val logFile = File(application.cacheDir, LLAMA_SERVER_LOG_NAME)
    private val settings = ServerSettings.from(application)
    private val apiKey = ApiKeyManager.getOrCreateApiKey(application)

    private val _metrics = MutableStateFlow(NodeMetrics())
    val metrics: StateFlow<NodeMetrics> = _metrics.asStateFlow()

    suspend fun observe() =
        coroutineScope {
            launch { tail() }
            launch { pollSystem() }
        }

    private suspend fun tail() {
        buffer.clear()
        withContext(Dispatchers.IO) {
            LogTailer(logFile).events().collect { event ->
                when (event) {
                    is TailEvent.Lines -> buffer.append(event.texts)
                    TailEvent.Reset -> buffer.clear()
                }
            }
        }
    }

    private suspend fun pollSystem() {
        var previousDecoded: Int? = null
        var previousAt = 0L

        while (currentCoroutineContext().isActive) {
            val memory = withContext(Dispatchers.IO) { readMemoryInfo() }
            val temperature = withContext(Dispatchers.IO) { hottestCelsius(ThermalZones.read()) }
            val decoded = withContext(Dispatchers.IO) { sampledDecodedTokens() }

            val now = System.nanoTime() / NANOS_PER_MILLI
            val rate = tokensPerSecond(decoded, previousDecoded, previousAt, now)

            _metrics.update {
                it.copy(
                    predictedPerSecond = rate ?: it.predictedPerSecond,
                    temperatureCelsius = temperature,
                    memory = memory,
                )
            }

            previousDecoded = decoded
            previousAt = now
            delay(POLL_MS.milliseconds)
        }
    }

    private fun sampledDecodedTokens(): Int? {
        val port = settings.port
        val models = ModelsClient(port, apiKey).loadedModelIds()
        if (models.isEmpty()) return null

        val slots = SlotsClient(port, apiKey)
        return models.sumOf { model -> runCatching { slots.decodedTokens(model) }.getOrNull() ?: 0 }
    }

    private companion object {
        const val POLL_MS = 1000L
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
