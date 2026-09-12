package com.lumarans30.hexamesh.platform

import android.content.Context
import java.util.UUID

object ApiKeyManager {
    private const val PREFS_NAME = "hexamesh_security"
    private const val KEY_API_KEY = "api_key"

    fun getOrCreateApiKey(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_API_KEY, null) ?: run {
            val newKey = UUID.randomUUID().toString().replace("-", "")
            prefs.edit().putString(KEY_API_KEY, newKey).apply()
            newKey
        }
    }
}
