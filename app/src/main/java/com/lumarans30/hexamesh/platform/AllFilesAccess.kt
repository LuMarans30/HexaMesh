package com.lumarans30.hexamesh.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings

/**
 * "All files access" (`MANAGE_EXTERNAL_STORAGE`). HexaMesh serves models without
 * it; it is only needed to *move* an import into place instead of copying it, so
 * it is requested only when the user picks Move.
 */
object AllFilesAccess {
    fun isGranted(): Boolean = Environment.isExternalStorageManager()

    fun settingsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )
}
