package com.lumarans30.hexamesh.platform

import android.content.Context
import com.lumarans30.hexamesh.bridge.ServerRole
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
 * Persisted node settings. Launch args are the source of truth, port included;
 * reads go to the [SettingsStore] so separate instances agree, while the flow is
 * the per-instance UI copy.
 */
class ServerSettings(
    private val store: SettingsStore,
) : NodeSettings {
    private val _launchArgs =
        MutableStateFlow(store.getString(KEY_LAUNCH_ARGS, DEFAULT_LAUNCH_ARGS))
    val launchArgsFlow: StateFlow<String> = _launchArgs.asStateFlow()

    val launchArgsText: String
        get() = store.getString(KEY_LAUNCH_ARGS, DEFAULT_LAUNCH_ARGS)

    override val launchArgs: List<String>
        get() = effectiveLaunchArgs(launchArgsText)

    override val port: Int
        get() = parseLaunchPort(launchArgs) ?: DEFAULT_SERVER_PORT

    override val role: String
        get() = store.getString(KEY_ROLE, ServerRole.SERVER)

    fun setLaunchArgs(text: String) {
        if (text == launchArgsText) return

        store.putString(KEY_LAUNCH_ARGS, text)
        _launchArgs.value = text
    }

    fun setRole(role: String) {
        if (role == this.role) return

        store.putString(KEY_ROLE, role)
    }

    companion object {
        private const val KEY_LAUNCH_ARGS = "launch_args"
        private const val KEY_ROLE = "role"

        fun from(context: Context): ServerSettings = ServerSettings(sharedPrefsStore(context, SETTINGS_PREFS_NAME))
    }
}
