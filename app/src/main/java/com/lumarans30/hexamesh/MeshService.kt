package com.lumarans30.hexamesh

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.lumarans30.hexamesh.bridge.RustEngine
import com.lumarans30.hexamesh.node.ModelRepository
import com.lumarans30.hexamesh.node.NodeController
import com.lumarans30.hexamesh.node.NodeEnvironment
import com.lumarans30.hexamesh.node.NodeState
import com.lumarans30.hexamesh.platform.ApiKeyManager
import com.lumarans30.hexamesh.platform.LockManager
import com.lumarans30.hexamesh.platform.Notifications
import com.lumarans30.hexamesh.platform.ServerSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Persistent foreground service that hosts the HexaMesh node. */
class MeshService : Service() {

    companion object {
        private const val TAG = "MeshService"
        const val EXTRA_MODEL_PATH = "com.lumarans30.hexamesh.extra.MODEL_PATH"
        const val EXTRA_RPC_SERVERS = "com.lumarans30.hexamesh.extra.RPC_SERVERS"
        const val ACTION_STOP = "com.lumarans30.hexamesh.action.STOP"
    }

    inner class LocalBinder : Binder() {
        val nodeState: StateFlow<NodeState>
            get() = node.state
    }

    private val binder = LocalBinder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var notifications: Notifications
    private lateinit var models: ModelRepository
    private lateinit var settings: ServerSettings
    private lateinit var node: NodeController

    private var foregroundActive = false

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        notifications = Notifications(this)
        models = ModelRepository(this)
        settings = ServerSettings.from(this)
        node = NodeController(
            NodeEnvironment(
                nativeLibDir = applicationInfo.nativeLibraryDir,
                cacheDir = cacheDir.absolutePath,
                llamaCacheDir = models.hfCacheDir().absolutePath,
                modelsDir = models.modelsDir().absolutePath,
                apiKey = ApiKeyManager.getOrCreateApiKey(this),
                serverDiedMessage = getString(R.string.state_server_died),
                locks = LockManager(this),
                settings = settings,
            ),
            RustEngine(),
        )

        notifications.createChannel()

        scope.launch {
            node.state.collect { state ->
                val settled =
                    state is NodeState.Stopped ||
                            state is NodeState.Idle ||
                            state is NodeState.Error
                if (foregroundActive) {
                    if (settled) hideForeground(stopSelf = true) else notifications.update(state)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Log.i(TAG, "Stop requested.")
            node.stop()
            hideForeground(stopSelf = true)
            return START_NOT_STICKY
        }

        val requestedModelPath = intent?.getStringExtra(EXTRA_MODEL_PATH)
        val modelPath = requestedModelPath ?: models.selected()?.path

        showForeground(node.state.value)

        if (modelPath == null && !settings.routerMode) {
            Log.w(TAG, "No .gguf model found. Skipping node start.")
            hideForeground(stopSelf = true)
            return START_NOT_STICKY
        }

        Log.i(TAG, "Starting node with model: $modelPath")
        node.start(modelPath, intent?.getStringExtra(EXTRA_RPC_SERVERS))
        return START_STICKY
    }

    override fun onDestroy() {
        Log.i(TAG, "Service destroyed.")
        scope.cancel()
        node.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        foregroundActive = false
        super.onDestroy()
    }

    private fun showForeground(state: NodeState) {
        startForeground(
            Notifications.NOTIFICATION_ID,
            notifications.build(state),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        foregroundActive = true
    }

    private fun hideForeground(stopSelf: Boolean) {
        if (foregroundActive) {
            foregroundActive = false
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
        if (stopSelf) stopSelf()
    }
}
