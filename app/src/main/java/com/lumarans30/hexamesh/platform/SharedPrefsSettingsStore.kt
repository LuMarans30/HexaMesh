package com.lumarans30.hexamesh.platform

import android.content.Context

/** SharedPreferences file holding all persisted settings. */
const val SETTINGS_PREFS_NAME = "hexamesh_settings"

/** [SettingsStore] backed by SharedPreferences. */
fun sharedPrefsStore(
    context: Context,
    name: String,
): SettingsStore {
    val prefs = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)
    return object : SettingsStore {
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
    }
}
