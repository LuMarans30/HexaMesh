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

/**
 * Persisted node settings. The launch args are the single source of truth, port
 * included; [port] is parsed back out for the app's own use (health probe, URL).
 *
 * Reads go straight to the [SettingsStore] so the service and the UI, which hold
 * separate instances, always agree; the flow is the UI's reactive copy and must
 * stay per-instance.
 */
class ServerSettings(private val store: SettingsStore) : NodeSettings {

    private val _launchArgs =
        MutableStateFlow(store.getString(KEY_LAUNCH_ARGS, DEFAULT_LAUNCH_ARGS))
    val launchArgsFlow: StateFlow<String> = _launchArgs.asStateFlow()

    val launchArgsText: String
        get() = store.getString(KEY_LAUNCH_ARGS, DEFAULT_LAUNCH_ARGS)

    override val launchArgs: List<String>
        get() = effectiveLaunchArgs(launchArgsText)

    override val port: Int
        get() = parseLaunchPort(launchArgs) ?: DEFAULT_SERVER_PORT

    private val _routerMode = MutableStateFlow(isRouterMode())
    val routerModeFlow: StateFlow<Boolean> = _routerMode.asStateFlow()

    override val routerMode: Boolean
        get() = isRouterMode()

    fun setRouterMode(enabled: Boolean) {
        if (enabled == isRouterMode()) return

        store.putString(KEY_ROUTER_MODE, enabled.toString())
        _routerMode.value = enabled
    }

    private fun isRouterMode(): Boolean = store.getString(KEY_ROUTER_MODE, "") == "true"

    fun setLaunchArgs(text: String) {
        if (text == launchArgsText) return

        store.putString(KEY_LAUNCH_ARGS, text)
        _launchArgs.value = text
    }

    companion object {
        private const val PREFS_NAME = "hexamesh_settings"
        private const val KEY_PORT = "port"
        private const val KEY_LAUNCH_ARGS = "launch_args"
        private const val KEY_ROUTER_MODE = "router_mode"

        fun from(context: Context): ServerSettings {
            val prefs =
                context.applicationContext
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            // One-time: fold the old separate port setting into the args text. Runs
            // when the args are absent, or present but without a `--port` (the
            // intermediate build where the port was still a separate setting).
            val storedArgs = prefs.getString(KEY_LAUNCH_ARGS, null)
            val needsPort =
                storedArgs == null || parseLaunchPort(parseLaunchArgs(storedArgs)) == null
            if (needsPort) {
                val storedPort = prefs.getInt(KEY_PORT, DEFAULT_SERVER_PORT)
                prefs
                    .edit()
                    .putString(KEY_LAUNCH_ARGS, withPort(storedArgs ?: DEFAULT_LAUNCH_ARGS, storedPort))
                    .remove(KEY_PORT)
                    .apply()
            }

            return ServerSettings(
                object : SettingsStore {
                    override fun getString(key: String, defaultValue: String): String =
                        prefs.getString(key, defaultValue) ?: defaultValue

                    override fun putString(key: String, value: String) {
                        prefs.edit().putString(key, value).apply()
                    }
                }
            )
        }
    }
}
