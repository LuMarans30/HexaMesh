package com.lumarans30.hexamesh.node

import java.net.URI
import java.net.URLDecoder

data class DownloadRequest(val url: String, val fileName: String)

private const val EXTENSION = ".gguf"

internal fun parseDownloadRequest(input: String): DownloadRequest? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null

    val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase() ?: return null
    if (scheme != "http" && scheme != "https") return null

    val fileName = urlDecode(uri.path.orEmpty().substringAfterLast('/')).trim()
    if (fileName.isEmpty()) return null
    if (fileName.any { it.isISOControl() }) return null
    if (!fileName.lowercase().endsWith(EXTENSION)) return null

    val url = trimmed.replace("/blob/", "/resolve/")

    return DownloadRequest(url = url, fileName = fileName)
}

private fun urlDecode(value: String): String =
    runCatching { URLDecoder.decode(value, Charsets.UTF_8.name()) }.getOrDefault(value)
