package com.lumarans30.hexamesh.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.lumarans30.hexamesh.platform.ServerSettings
import kotlinx.coroutines.flow.StateFlow

/** Exposes the persisted launch args the Settings tab edits. */
class SettingsViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val settings = ServerSettings.from(application)

    val launchArgs: StateFlow<String> = settings.launchArgsFlow

    fun apply(launchArgs: String) = settings.setLaunchArgs(launchArgs)
}
