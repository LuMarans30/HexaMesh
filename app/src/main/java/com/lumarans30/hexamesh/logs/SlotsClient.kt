package com.lumarans30.hexamesh.logs

import java.net.HttpURLConnection
import java.net.URL

/** Reads the live slot stats the server exposes when launched with `--slots`. */
class SlotsClient(
    private val port: Int,
    private val apiKey: String,
    private val timeoutMs: Int = 1000,
) {
    fun decodedTokens(): Int? {
        val connection =
            (URL("http://127.0.0.1:$port/slots").openConnection() as HttpURLConnection).apply {
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
