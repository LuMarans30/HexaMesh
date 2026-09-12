package com.lumarans30.hexamesh.platform

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persisted mesh settings. Reads go to the [SettingsStore] so separate instances
 * agree; the flows are the per-instance UI copy.
 */
class MeshSettings(
    private val store: SettingsStore,
) {
    private val _fallbackPeers = MutableStateFlow(store.getString(KEY_FALLBACK_PEERS, ""))
    val fallbackPeers: StateFlow<String> = _fallbackPeers.asStateFlow()

    private val _discoverable = MutableStateFlow(isDiscoverable())
    val discoverable: StateFlow<Boolean> = _discoverable.asStateFlow()

    private val _usePeers = MutableStateFlow(isUsePeers())
    val usePeers: StateFlow<Boolean> = _usePeers.asStateFlow()

    fun setFallbackPeers(text: String) {
        if (text == store.getString(KEY_FALLBACK_PEERS, "")) return

        store.putString(KEY_FALLBACK_PEERS, text)
        _fallbackPeers.value = text
    }

    fun setDiscoverable(discoverable: Boolean) {
        if (discoverable == isDiscoverable()) return

        store.putString(KEY_DISCOVERABLE, discoverable.toString())
        _discoverable.value = discoverable
    }

    fun setUsePeers(usePeers: Boolean) {
        if (usePeers == isUsePeers()) return

        store.putString(KEY_USE_PEERS, usePeers.toString())
        _usePeers.value = usePeers
    }

    private fun isDiscoverable(): Boolean = store.getString(KEY_DISCOVERABLE, "") == "true"

    private fun isUsePeers(): Boolean = store.getString(KEY_USE_PEERS, "") == "true"

    companion object {
        private const val PREFS_NAME = "hexamesh_settings"
        private const val KEY_FALLBACK_PEERS = "fallback_peers"
        private const val KEY_DISCOVERABLE = "discoverable"
        private const val KEY_USE_PEERS = "use_peers"

        fun from(context: Context): MeshSettings {
            val prefs =
                context.applicationContext
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            return MeshSettings(
                object : SettingsStore {
                    override fun getString(
                        key: String,
                        defaultValue: String,
                    ): String = prefs.getString(key, defaultValue) ?: defaultValue

                    override fun putString(
                        key: String,
                        value: String,
                    ) {
                        prefs.edit().putString(key, value).apply()
                    }
                },
            )
        }
    }
}
