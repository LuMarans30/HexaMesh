package com.lumarans30.hexamesh

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lumarans30.hexamesh.platform.AllFilesAccess
import com.lumarans30.hexamesh.ui.HexaMeshApp
import com.lumarans30.hexamesh.ui.NodeViewModel
import com.lumarans30.hexamesh.ui.TransferViewModel
import com.lumarans30.hexamesh.ui.hexaMeshTheme

/** Thin control panel for the headless node. */
class MainActivity : ComponentActivity() {

    private val nodeViewModel: NodeViewModel by viewModels()
    private val transferViewModel: TransferViewModel by viewModels()

    private val batteryHandler = Handler(Looper.getMainLooper())

    private var batteryExempt by mutableStateOf(false)

    private val pickModel =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) transferViewModel.onPicked(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        refreshBatteryStatus()

        setContent {
            hexaMeshTheme {
                HexaMeshApp(
                    nodeViewModel = nodeViewModel,
                    transferViewModel = transferViewModel,
                    batteryExempt = batteryExempt,
                    onPickModel = { pickModel.launch(arrayOf("*/*")) },
                    onOpenAllFilesSettings = ::openAllFilesSettings,
                    onFixBattery = ::requestIgnoreBatteryOptimizations,
                )
            }
        }

        requestNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        transferViewModel.onResumed()
        scheduleBatteryRefresh()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) scheduleBatteryRefresh()
    }

    override fun onDestroy() {
        super.onDestroy()
        batteryHandler.removeCallbacksAndMessages(null)
    }

    private fun openAllFilesSettings() {
        try {
            startActivity(AllFilesAccess.settingsIntent(this))
        } catch (_: ActivityNotFoundException) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            } catch (_: Exception) {
                // Some OEM builds remove this settings screen.
            }
        }
    }

    private fun refreshBatteryStatus() {
        batteryExempt = isIgnoringBatteryOptimizations()
    }

    /**
     * The battery-optimization screen can commit the whitelist change a moment
     * after we regain focus
     */
    private fun scheduleBatteryRefresh() {
        batteryHandler.removeCallbacksAndMessages(null)
        refreshBatteryStatus()
        for (delay in BATTERY_REFRESH_RETRY_MS) {
            batteryHandler.postDelayed({ refreshBatteryStatus() }, delay)
        }
    }

    private fun isIgnoringBatteryOptimizations(): Boolean =
        getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(packageName) == true

    private fun requestNotificationPermission() {
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQ_NOTIFICATIONS,
            )
        }
    }

    private fun requestIgnoreBatteryOptimizations() {
        val pm = getSystemService(PowerManager::class.java) ?: return
        if (pm.isIgnoringBatteryOptimizations(packageName)) return

        try {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName"),
                )
            )
        } catch (_: Exception) {
            // Some OEM builds remove this settings screen.
        }
    }

    companion object {
        private const val REQ_NOTIFICATIONS = 1001
        private val BATTERY_REFRESH_RETRY_MS = longArrayOf(300, 900, 1800, 3000)
    }
}
