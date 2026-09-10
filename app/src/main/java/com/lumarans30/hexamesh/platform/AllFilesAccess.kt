package com.lumarans30.hexamesh.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings

/**
 * "All files access" (`MANAGE_EXTERNAL_STORAGE`).
 *
 * HexaMesh does not need this to serve models. It is the only way to *move* an
 * imported file into the models directory without copying it (which would
 * briefly need twice the space), so it is requested only when the user picks
 * **Move**.
 */
object AllFilesAccess {
    fun isGranted(): Boolean = Environment.isExternalStorageManager()

    /** Settings screen for this app's "All files access" toggle. */
    fun settingsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )
}
