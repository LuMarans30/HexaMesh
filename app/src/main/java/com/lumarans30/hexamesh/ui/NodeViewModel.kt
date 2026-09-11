package com.lumarans30.hexamesh.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lumarans30.hexamesh.MeshService
import com.lumarans30.hexamesh.node.NodeState
import com.lumarans30.hexamesh.platform.ApiKeyManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns the node lifecycle the UI observes.
 */
class NodeViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context = application.applicationContext

    private val _state = MutableStateFlow<NodeState>(NodeState.Stopped)
    val state: StateFlow<NodeState> = _state.asStateFlow()

    val apiKey: String = ApiKeyManager.getOrCreateApiKey(context)

    private var bound = false
    private var stateJob: Job? = null

    private val connection =
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val binder = service as? MeshService.LocalBinder ?: return
                stateJob?.cancel()
                stateJob =
                    viewModelScope.launch {
                        binder.nodeState.collect { _state.value = it }
                    }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                stateJob?.cancel()
                stateJob = null
                _state.value = NodeState.Stopped
            }
        }

    init {
        bound =
            context.bindService(
                Intent(context, MeshService::class.java),
                connection,
                Context.BIND_AUTO_CREATE,
            )
    }

    fun start(modelPath: String?) {
        val intent =
            Intent(context, MeshService::class.java).apply {
                if (modelPath != null) putExtra(MeshService.EXTRA_MODEL_PATH, modelPath)
            }
        context.startForegroundService(intent)
    }

    fun stop() {
        context.startService(
            Intent(context, MeshService::class.java).apply { action = MeshService.ACTION_STOP }
        )
    }

    override fun onCleared() {
        stateJob?.cancel()
        stateJob = null
        if (bound) {
            context.unbindService(connection)
            bound = false
        }
        super.onCleared()
    }
}
