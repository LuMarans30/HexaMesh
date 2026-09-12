package com.lumarans30.hexamesh.node

import com.lumarans30.hexamesh.bridge.Engine
import com.lumarans30.hexamesh.bridge.EngineStatus
import com.lumarans30.hexamesh.bridge.ServerConfig
import com.lumarans30.hexamesh.bridge.ServerRole
import com.lumarans30.hexamesh.mesh.DEFAULT_RPC_PORT
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.milliseconds


class NodeController(
    private val env: NodeEnvironment,
    private val engine: Engine,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val transition = Mutex()

    private val _state = MutableStateFlow<NodeState>(NodeState.Stopped)

    val state: StateFlow<NodeState> = _state.asStateFlow()

    @Volatile
    private var running = false

    @Volatile
    private var activePort: Int = env.settings.port

    @Volatile
    private var activeIsWorker = false

    private var statusJob: Job? = null

    fun start(rpcServers: String? = null) {
        scope.launch { startNode(rpcServers) }
    }

    fun stop() {
        scope.launch { unload() }
    }

    internal suspend fun startNode(rpcServers: String? = null) =
        transition.withLock {
            if (running) return@withLock

            running = true
            val port = env.settings.port
            val role = env.settings.role
            val extraArgs = env.settings.launchArgs.joinToString("\n")
            activePort = port
            activeIsWorker = role == ServerRole.RPC
            env.locks.acquire()
            post(NodeState.Starting)

            try {
                engine.start(
                    ServerConfig(
                        nativeLibDir = env.nativeLibDir,
                        cacheDir = env.cacheDir,
                        llamaCacheDir = env.llamaCacheDir,
                        port = port,
                        backend = BACKEND,
                        apiKey = env.apiKey,
                        extraArgs = extraArgs,
                        role = role,
                        rpcServers = rpcServers.orEmpty(),
                        rpcPort = DEFAULT_RPC_PORT,
                        modelsDir = env.modelsDir,
                    )
                )
            } catch (t: Throwable) {
                teardown()
                post(NodeState.Error(t.message ?: FAILED_TO_START))
                return@withLock
            }

            watchStatus()
        }

    internal suspend fun unload() =
        transition.withLock {
            if (!running) return@withLock

            post(NodeState.Stopping)
            teardown()
            post(NodeState.Stopped)
        }

    private suspend fun fail(message: String) =
        transition.withLock {
            if (!running) return@withLock

            teardown(stopWatcher = false)
            post(NodeState.Error(message))
        }

    private fun watchStatus() {
        statusJob?.cancel()
        statusJob =
            scope.launch {
                var lastState: Int? = null
                var lastMessage: String? = null

                while (isActive && running) {
                    val status = runCatching { engine.pollStatus() }.getOrNull()
                    if (status == null) {
                        delay(STATUS_POLL_INTERVAL_MS.milliseconds)
                        continue
                    }

                    val first = lastState == null
                    val changed =
                        first || status.state != lastState || status.message != lastMessage

                    if (changed) {
                        lastState = status.state
                        lastMessage = status.message

                        if (!(first && status.state == EngineStatus.STOPPED)) {
                            when (status.state) {
                                EngineStatus.RUNNING ->
                                    post(NodeState.Running(runningEndpoint(), activeIsWorker))

                                EngineStatus.ERROR -> {
                                    fail(status.message ?: env.serverDiedMessage)
                                    return@launch
                                }

                                else -> Unit
                            }
                        }
                    }

                    delay(STATUS_POLL_INTERVAL_MS.milliseconds)
                }
            }
    }

    private suspend fun teardown(stopWatcher: Boolean = true) {
        running = false
        activeIsWorker = false
        if (stopWatcher) {
            statusJob?.cancelAndJoin()
        }
        statusJob = null
        runCatching { engine.stop() }
        env.locks.release()
    }

    private fun post(state: NodeState) {
        _state.value = state
    }

    /** HTTP base URL when serving, `host:port` when exposing RPC to the mesh. */
    private fun runningEndpoint(): String {
        val host = lanIpv4Address() ?: "127.0.0.1"
        return if (activeIsWorker) "$host:$DEFAULT_RPC_PORT" else "http://$host:$activePort"
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
        private const val BACKEND = "GPU"
        private const val FAILED_TO_START = "Failed to start the Rust engine"
        private const val STATUS_POLL_INTERVAL_MS = 500L
    }
}