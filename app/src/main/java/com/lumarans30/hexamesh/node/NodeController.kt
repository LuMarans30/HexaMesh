package com.lumarans30.hexamesh.node

import android.content.Context
import com.lumarans30.hexamesh.bridge.EngineStatus
import com.lumarans30.hexamesh.bridge.RustEngine
import com.lumarans30.hexamesh.bridge.ServerConfig
import com.lumarans30.hexamesh.platform.LockManager
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import com.lumarans30.hexamesh.R
import com.lumarans30.hexamesh.platform.ApiKeyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NodeController(
    context: Context,
    private val engine: RustEngine,
) {
    private val app = context.applicationContext
    private val locks = LockManager(app)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var engineThread: Thread? = null
    private var statusThread: Thread? = null

    private val engineStarted = AtomicBoolean(false)
    private val stopping = AtomicBoolean(false)

    fun start(modelPath: String?) {
        if (modelPath == null) {
            NodeState.post(NodeState.Idle)
            return
        }

        if (!engineStarted.compareAndSet(false, true)) return

        stopping.set(false)
        locks.acquire()
        NodeState.post(NodeState.Starting)

        engineThread = thread(name = "hexamesh-engine") {
            try {
                val config = ServerConfig(
                    modelPath = modelPath,
                    nativeLibDir = app.applicationInfo.nativeLibraryDir,
                    cacheDir = app.cacheDir.absolutePath,
                    port = SERVER_PORT,
                    backend = "GPU",
                    apiKey = ApiKeyManager.getOrCreateApiKey(app),
                )
                engine.start(config)
            } catch (t: Throwable) {
                fail(t.message ?: "Failed to start the Rust engine")
            }
        }

        startStatusWatcher()
    }

    fun stop() {
        if (!stopping.compareAndSet(false, true)) return

        scope.launch {
            statusThread?.interrupt()
            statusThread?.join(1_000)
            statusThread = null

            runCatching { engine.stop() }
            engineThread?.let { t -> runCatching { t.join(2_000) } }
            engineThread = null

            engineStarted.set(false)
            locks.release()
            NodeState.post(NodeState.Stopped)
        }
    }

    private fun fail(message: String) {
        if (stopping.getAndSet(true)) return

        statusThread?.interrupt()
        statusThread = null

        runCatching { engine.stop() }

        engineThread?.interrupt()
        engineThread = null
        
        engineStarted.set(false)
        locks.release()
        NodeState.post(NodeState.Error(message))
    }

    private fun startStatusWatcher() {
        statusThread?.interrupt()
        statusThread =
            thread(name = "hexamesh-status") {
                var lastState: Int? = null
                var lastMessage: String? = null

                try {
                    while (!stopping.get()) {
                        val status = runCatching { engine.pollStatus() }.getOrNull()
                        if (status == null) {
                            Thread.sleep(STATUS_POLL_INTERVAL_MS)
                            continue
                        }

                        val state = status.state
                        val message = status.message
                        val first = lastState == null
                        val changed = first || state != lastState || message != lastMessage

                        if (changed) {
                            lastState = state
                            lastMessage = message

                            // Rust's snapshot still reads STOPPED on the first
                            // poll; ignore it so it doesn't clobber the Starting
                            // state posted by start().
                            if (!(first && state == EngineStatus.STOPPED)) {
                                postEngineStatus(state, message)
                            }
                        }

                        Thread.sleep(STATUS_POLL_INTERVAL_MS)
                    }
                } catch (_: InterruptedException) {
                    // Service is shutting down
                }
            }
    }

    private fun postEngineStatus(state: Int, message: String?) {
        when (state) {
            EngineStatus.STARTING -> NodeState.post(NodeState.Starting)

            EngineStatus.RUNNING -> NodeState.post(NodeState.Running(lanEndpoint()))

            EngineStatus.ERROR ->
                fail(message ?: app.getString(R.string.state_server_died))

            EngineStatus.STOPPED -> NodeState.post(NodeState.Stopped)
        }
    }

    private fun lanEndpoint(): String {
        val host = lanIpv4Address() ?: "127.0.0.1"
        return "http://$host:$SERVER_PORT/v1"
    }

    private fun lanIpv4Address(): String? =
        runCatching {
            NetworkInterface.getNetworkInterfaces()
                .asSequence()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.asSequence() }
                .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
                ?.hostAddress
        }.getOrNull()

    companion object {
        private const val SERVER_PORT = 8080
        private const val STATUS_POLL_INTERVAL_MS = 500L
    }
}