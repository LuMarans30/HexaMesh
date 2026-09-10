package com.lumarans30.hexamesh

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import com.lumarans30.hexamesh.bridge.RustEngine
import com.lumarans30.hexamesh.node.ModelRepository
import com.lumarans30.hexamesh.node.NodeController
import com.lumarans30.hexamesh.node.NodeEnvironment
import com.lumarans30.hexamesh.platform.ApiKeyManager
import com.lumarans30.hexamesh.platform.LockManager
import com.lumarans30.hexamesh.platform.Notifications

/** Persistent foreground service that hosts the HexaMesh node. */
class MeshService : Service() {

    companion object {
        private const val TAG = "MeshService"
        const val EXTRA_MODEL_PATH = "com.lumarans30.hexamesh.extra.MODEL_PATH"
        const val ACTION_STOP = "com.lumarans30.hexamesh.action.STOP"
    }

    private lateinit var notifications: Notifications
    private lateinit var models: ModelRepository
    private lateinit var node: NodeController

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notifications = Notifications(this)
        models = ModelRepository(this)
        node = NodeController(
            NodeEnvironment(
                nativeLibDir = applicationInfo.nativeLibraryDir,
                cacheDir = cacheDir.absolutePath,
                apiKey = ApiKeyManager.getOrCreateApiKey(this),
                serverDiedMessage = getString(R.string.state_server_died),
                locks = LockManager(this),
            ),
            RustEngine(),
        )

        notifications.createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Log.i(TAG, "Stop requested.")
            stopSelf()
            return START_NOT_STICKY
        }

        val requestedModelPath = intent?.getStringExtra(EXTRA_MODEL_PATH)
        val modelPath = requestedModelPath ?: models.selected()?.path

        startForeground(
            Notifications.NOTIFICATION_ID,
            notifications.build(modelPath),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )

        if (modelPath == null) {
            Log.w(TAG, "No .gguf model found. Skipping node start.")
            stopSelf()
            return START_NOT_STICKY
        }

        Log.i(TAG, "Starting node with model: $modelPath")
        node.start(modelPath)
        return START_STICKY
    }

    override fun onDestroy() {
        Log.i(TAG, "Service destroyed.")
        node.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}