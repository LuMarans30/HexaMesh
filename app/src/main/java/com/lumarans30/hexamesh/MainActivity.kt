package com.lumarans30.hexamesh

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.lumarans30.hexamesh.node.ModelRepository
import com.lumarans30.hexamesh.node.NodeState
import com.lumarans30.hexamesh.platform.ApiKeyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Thin control panel for the headless node. */
class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var toggleButton: Button
    private lateinit var models: ModelRepository
    private var uiScope: CoroutineScope? = null
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collectJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        models = ModelRepository(this)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(64), dp(24), dp(24))
        }

        statusText = TextView(this).apply {
            textSize = 14f
            setTextIsSelectable(true)
        }

        toggleButton = Button(this)

        layout.addView(statusText)
        layout.addView(
            toggleButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(32) }
        )

        setContentView(layout)

        requestNotificationPermission()
        statusText.post { requestIgnoreBatteryOptimizations() }
        startMeshService()
    }

    override fun onResume() {
        super.onResume()
        collectJob = activityScope.launch {
            NodeState.current.collect { render(it) }
        }
    }

    override fun onPause() {
        collectJob?.cancel()
        collectJob = null
        super.onPause()
    }

    override fun onDestroy() {
        activityScope.cancel()
        super.onDestroy()
    }

    private fun render(state: NodeState) {
        val model = models.findDefault()
        val batteryExempt =
            getSystemService(PowerManager::class.java)
                ?.isIgnoringBatteryOptimizations(packageName) == true

        val description =
            when (state) {
                NodeState.Stopped -> {
                    setButton(getString(R.string.start_node)) { startMeshService() }
                    "Node stopped."
                }

                NodeState.Starting -> {
                    setButton(getString(R.string.starting_node), enabled = false)
                    "Starting llama-server..."
                }

                NodeState.Idle -> {
                    setButton(getString(R.string.stop_node)) { stopMeshService() }
                    "Service running, but no model to load."
                }

                is NodeState.Running -> {
                    setButton(getString(R.string.stop_node)) { stopMeshService() }
                    val apiKey = ApiKeyManager.getOrCreateApiKey(this)
                    """
                    OpenAI-compatible endpoint:
                    ${state.endpoint}
                    API Key: $apiKey
                    (point Open WebUI / any client at it)
                    """.trimIndent()
                }

                is NodeState.Error -> {
                    setButton(getString(R.string.retry_node)) { startMeshService() }
                    "Error: ${state.message}"
                }
            }

        statusText.text =
            buildString {
                appendLine(getString(R.string.app_name))
                appendLine()

                if (model != null) {
                    appendLine("Model: ${model.name}")
                } else {
                    appendLine("No model found. Push a GGUF to this phone:")
                    appendLine("  ${models.adbPushHint()}")
                }

                appendLine()
                appendLine(description)
                appendLine()
                appendLine("Battery optimization exempt: ${if (batteryExempt) "yes" else "no"}")
                appendLine("Logs: adb logcat -s HexaRust MeshService LlamaServer")
            }
    }

    private fun setButton(
        text: String,
        enabled: Boolean = true,
        onClick: (() -> Unit)? = null
    ) {
        toggleButton.text = text
        toggleButton.isEnabled = enabled
        toggleButton.setOnClickListener { onClick?.invoke() }
    }

    private fun startMeshService() {
        val intent = Intent(this, MeshService::class.java)
        models.findDefault()?.let {
            intent.putExtra(MeshService.EXTRA_MODEL_PATH, it.absolutePath)
        }
        startForegroundService(intent)
    }

    private fun stopMeshService() {
        val intent = Intent(this, MeshService::class.java).apply {
            action = MeshService.ACTION_STOP
        }
        startService(intent)
    }

    private fun requestNotificationPermission() {
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQ_NOTIFICATIONS
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
                    Uri.parse("package:$packageName")
                )
            )
        } catch (_: Exception) {
            // Some OEM builds remove this settings screen.
        }
    }

    private fun dp(value: Int): Int =
        (resources.displayMetrics.density * value).toInt()

    companion object {
        private const val REQ_NOTIFICATIONS = 1001
    }
}