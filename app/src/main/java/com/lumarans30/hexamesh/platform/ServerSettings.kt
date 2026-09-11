package com.lumarans30.hexamesh.platform

import android.content.Context
import com.lumarans30.hexamesh.node.NodeSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

const val DEFAULT_SERVER_PORT = 8080
const val MIN_SERVER_PORT = 1024
const val MAX_SERVER_PORT = 65535

/** Ports below 1024 are privileged, so the node only accepts the user range. */
fun isValidPort(value: Int): Boolean = value in MIN_SERVER_PORT..MAX_SERVER_PORT

fun parsePort(text: String): Int? = text.trim().toIntOrNull()?.takeIf(::isValidPort)

internal fun coercePort(value: Int): Int = if (isValidPort(value)) value else DEFAULT_SERVER_PORT

/**
 * Persisted node settings. SharedPreferences keeps the app free of an extra
 * dependency and stays readable from the foreground service without binding.
 *
 * [portFlow] is the UI's reactive copy and must stay per-instance. [port] reads
 * the store every time instead of caching, because the service and the UI hold
 * separate instances and only the UI ever writes.
 */
class ServerSettings(
    private val read: () -> Int,
    private val write: (Int) -> Unit,
) : NodeSettings {

    private val _port = MutableStateFlow(coercePort(read()))

    val portFlow: StateFlow<Int> = _port.asStateFlow()

    override val port: Int
        get() = coercePort(read())

    fun setPort(value: Int) {
        if (!isValidPort(value) || value == port) return

        write(value)
        _port.value = value
    }

    companion object {
        private const val PREFS_NAME = "hexamesh_settings"
        private const val KEY_PORT = "port"

        fun from(context: Context): ServerSettings {
            val prefs =
                context.applicationContext
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            return ServerSettings(
                read = { prefs.getInt(KEY_PORT, DEFAULT_SERVER_PORT) },
                write = { value -> prefs.edit().putInt(KEY_PORT, value).apply() },
            )
        }
    }
}
