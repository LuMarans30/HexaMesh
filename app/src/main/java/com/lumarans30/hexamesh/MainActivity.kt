package com.lumarans30.hexamesh

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lumarans30.hexamesh.node.Model
import com.lumarans30.hexamesh.node.ModelRepository
import com.lumarans30.hexamesh.node.NodeState
import com.lumarans30.hexamesh.platform.ApiKeyManager
import com.lumarans30.hexamesh.ui.hexaMeshTheme
import com.lumarans30.hexamesh.ui.nodeScreen

/** Thin control panel for the headless node. */
class MainActivity : ComponentActivity() {

    private lateinit var repository: ModelRepository

    private var available by mutableStateOf<List<Model>>(emptyList())
    private var selectedPath by mutableStateOf<String?>(null)
    private var batteryExempt by mutableStateOf(false)

    private val apiKey by lazy { ApiKeyManager.getOrCreateApiKey(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        repository = ModelRepository(this)
        refreshModels()
        refreshBatteryStatus()

        setContent {
            hexaMeshTheme {
                val state by NodeState.current.collectAsState()
                nodeScreen(
                    state = state,
                    models = available,
                    selectedPath = selectedPath,
                    batteryExempt = batteryExempt,
                    apiKey = apiKey,
                    adbPushHint = repository.adbPushHint(),
                    onSelect = ::selectModel,
                    onDelete = ::deleteModel,
                    onStart = ::startMeshService,
                    onStop = ::stopMeshService,
                    onFixBattery = ::requestIgnoreBatteryOptimizations,
                )
            }
        }

        requestNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        refreshModels()
        refreshBatteryStatus()
    }

    private fun refreshBatteryStatus() {
        batteryExempt = isIgnoringBatteryOptimizations()
    }

    private fun refreshModels() {
        available = repository.list()
        selectedPath = repository.selected()?.path
    }

    private fun selectModel(model: Model) {
        repository.select(model)
        selectedPath = model.path
    }

    private fun deleteModel(model: Model) {
        repository.delete(model)
        refreshModels()
    }

    private fun isIgnoringBatteryOptimizations(): Boolean =
        getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(packageName) == true

    private fun startMeshService() {
        val intent = Intent(this, MeshService::class.java)
        repository.selected()?.let { intent.putExtra(MeshService.EXTRA_MODEL_PATH, it.path) }
        startForegroundService(intent)
    }

    private fun stopMeshService() {
        val intent =
            Intent(this, MeshService::class.java).apply { action = MeshService.ACTION_STOP }
        startService(intent)
    }

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
    }
}