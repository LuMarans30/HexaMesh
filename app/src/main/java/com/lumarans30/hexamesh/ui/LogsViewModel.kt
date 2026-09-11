package com.lumarans30.hexamesh.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.lumarans30.hexamesh.logs.LLAMA_SERVER_LOG_NAME
import com.lumarans30.hexamesh.logs.LineBuffer
import com.lumarans30.hexamesh.logs.LogTailer
import com.lumarans30.hexamesh.logs.TailEvent
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/** Streams the supervisor's log file while the Logs tab is visible. */
class LogsViewModel(application: Application) : AndroidViewModel(application) {

    private val buffer = LineBuffer()

    val lines: StateFlow<List<String>> = buffer.lines

    private val logFile = File(application.cacheDir, LLAMA_SERVER_LOG_NAME)

    suspend fun tail() {
        buffer.clear()
        withContext(Dispatchers.IO) {
            LogTailer(logFile).events().collect { event ->
                when (event) {
                    is TailEvent.Line -> buffer.append(event.text)
                    TailEvent.Reset -> buffer.clear()
                }
            }
        }
    }
}
