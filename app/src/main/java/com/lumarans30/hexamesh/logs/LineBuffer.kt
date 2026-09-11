package com.lumarans30.hexamesh.logs

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Rolling view of the most recent [capacity] log lines for the terminal view. */
class LineBuffer(private val capacity: Int = DEFAULT_CAPACITY) {
    private val buffer = ArrayDeque<String>()

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    fun append(line: String) {
        buffer.addLast(line)
        while (buffer.size > capacity) buffer.removeFirst()
        _lines.value = buffer.toList()
    }

    fun clear() {
        if (buffer.isEmpty()) return
        buffer.clear()
        _lines.value = emptyList()
    }

    companion object {
        const val DEFAULT_CAPACITY = 2000
    }
}
