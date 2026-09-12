package com.lumarans30.hexamesh.node

import java.net.HttpURLConnection
import java.net.URL

/**
 * Asks a running llama-server router to re-scan `--models-dir`, so a model added
 * while the node runs appears without a restart.
 */
class ModelsClient(
    private val port: Int,
    private val apiKey: String,
    private val timeoutMs: Int = 3000,
) {
    fun reload(): Boolean {
        val connection =
            (URL(reloadUrl(port)).openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                requestMethod = "GET"
                if (apiKey.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer $apiKey")
                }
            }

        return try {
            connection.responseCode == HttpURLConnection.HTTP_OK
        } finally {
            connection.disconnect()
        }
    }
}

/** The router re-scans on any non-empty `reload` query param (see `get_router_models`). */
internal fun reloadUrl(port: Int): String = "http://127.0.0.1:$port/models?reload=1"
