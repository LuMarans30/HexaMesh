package com.lumarans30.hexamesh.logs

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Reads the live slot stats the server exposes when launched with `--slots`. */
class SlotsClient(
    private val port: Int,
    private val apiKey: String,
    private val timeoutMs: Int = 1000,
) {
    fun decodedTokens(model: String): Int? {
        val connection =
            (URL(slotsUrl(port, model)).openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                requestMethod = "GET"
                if (apiKey.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer $apiKey")
                }
            }

        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                null
            } else {
                parseDecodedTokens(connection.inputStream.bufferedReader().use { it.readText() })
            }
        } finally {
            connection.disconnect()
        }
    }
}

internal fun slotsUrl(
    port: Int,
    model: String,
): String {
    val encoded = URLEncoder.encode(model, Charsets.UTF_8.name()).replace("+", "%20")
    return "http://127.0.0.1:$port/slots?model=$encoded&autoload=0"
}
