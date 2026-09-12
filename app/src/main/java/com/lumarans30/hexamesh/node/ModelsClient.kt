package com.lumarans30.hexamesh.node

import java.net.HttpURLConnection
import java.net.URL

/**
 * Talks to a running llama-server router: re-scan `--models-dir` so a model added
 * while the node runs appears without a restart, and report which models are loaded.
 */
class ModelsClient(
    private val port: Int,
    private val apiKey: String,
    private val timeoutMs: Int = 3000,
) {
    fun reload(): Boolean {
        val connection = open(reloadUrl(port))

        return try {
            connection.responseCode == HttpURLConnection.HTTP_OK
        } finally {
            connection.disconnect()
        }
    }

    /** Ids of the models the router reports as loaded; empty when the request fails. */
    fun loadedModelIds(): List<String> =
        runCatching {
            val connection = open(modelsUrl(port))
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    emptyList()
                } else {
                    parseLoadedModelIds(connection.inputStream.bufferedReader().use { it.readText() })
                }
            } finally {
                connection.disconnect()
            }
        }.getOrDefault(emptyList())

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            requestMethod = "GET"
            if (apiKey.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer $apiKey")
            }
        }
}

/** The router re-scans on any non-empty `reload` query param (see `get_router_models`). */
internal fun reloadUrl(port: Int): String = "http://127.0.0.1:$port/models?reload=1"

internal fun modelsUrl(port: Int): String = "http://127.0.0.1:$port/models"

private val LOADED_MODEL =
    Regex("\"id\"\\s*:\\s*\"([^\"]+)\"[^{}]*?\"status\"\\s*:\\s*\\{\\s*\"value\"\\s*:\\s*\"loaded\"")

internal fun parseLoadedModelIds(modelsJson: String): List<String> = LOADED_MODEL.findAll(modelsJson).map { it.groupValues[1] }.toList()
