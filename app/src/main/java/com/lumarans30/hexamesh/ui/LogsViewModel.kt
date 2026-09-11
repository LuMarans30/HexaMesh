package com.lumarans30.hexamesh.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.lumarans30.hexamesh.logs.LLAMA_SERVER_LOG_NAME
import com.lumarans30.hexamesh.logs.LineBuffer
import com.lumarans30.hexamesh.logs.LogTailer
import com.lumarans30.hexamesh.logs.NodeMetrics
import com.lumarans30.hexamesh.logs.TailEvent
import com.lumarans30.hexamesh.logs.ThermalZones
import com.lumarans30.hexamesh.logs.hottestCelsius
import com.lumarans30.hexamesh.logs.parseTokensPerSecond
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Streams the log file and the node diagnostics while the Logs tab is visible. */
class LogsViewModel(application: Application) : AndroidViewModel(application) {

    private val buffer = LineBuffer()

    val lines: StateFlow<List<String>> = buffer.lines

    private val logFile = File(application.cacheDir, LLAMA_SERVER_LOG_NAME)

    private val _metrics = MutableStateFlow(NodeMetrics())
    val metrics: StateFlow<NodeMetrics> = _metrics.asStateFlow()

    suspend fun observe() = coroutineScope {
        launch { tail() }
        launch { pollTemperature() }
    }

    private suspend fun tail() {
        buffer.clear()
        withContext(Dispatchers.IO) {
            LogTailer(logFile).events().collect { event ->
                when (event) {
                    is TailEvent.Line -> {
                        buffer.append(event.text)
                        parseTokensPerSecond(event.text)?.let { rate ->
                            _metrics.update { it.copy(predictedPerSecond = rate) }
                        }
                    }

                    TailEvent.Reset -> buffer.clear()
                }
            }
        }
    }

    private suspend fun pollTemperature() {
        while (currentCoroutineContext().isActive) {
            val zones = withContext(Dispatchers.IO) { ThermalZones.read() }
            _metrics.update { it.copy(temperatureCelsius = hottestCelsius(zones)) }
            delay(TEMP_POLL_MS)
        }
    }

    private companion object {
        const val TEMP_POLL_MS = 2000L
    }
}
