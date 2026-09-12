package com.lumarans30.hexamesh.platform

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns

private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"

/**
 * Maps a local Storage Access Framework document id to a filesystem path; cloud
 * providers have none and return null, so those files can only be copied. Pure
 * so it is testable without Android.
 */
internal fun documentIdToPath(
    authority: String?,
    documentId: String?,
    primaryRoot: String?,
): String? {
    if (documentId.isNullOrEmpty()) return null
    if (documentId.startsWith("raw:")) return documentId.removePrefix("raw:").ifEmpty { null }
    if (authority != EXTERNAL_STORAGE_AUTHORITY) return null

    val parts = documentId.split(":", limit = 2)
    if (parts.size != 2 || parts[1].isEmpty()) return null

    val root =
        if (parts[0] == "primary") {
            primaryRoot ?: return null
        } else {
            "/storage/${parts[0]}"
        }

    return "$root/${parts[1]}"
}

/** Real path for a picked document, or null when the provider is not a local file. */
fun realPath(uri: Uri): String? {
    if (uri.scheme == "file") return uri.path
    val documentId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
    return documentIdToPath(
        authority = uri.authority,
        documentId = documentId,
        primaryRoot = Environment.getExternalStorageDirectory()?.absolutePath,
    )
}

/** Display name for a picked document. */
fun displayName(context: Context, uri: Uri): String? =
    context.contentResolver
        .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

/** Size of a picked document, or null when the provider does not report one. */
fun documentSize(context: Context, uri: Uri): Long? =
    context.contentResolver
        .query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null }
