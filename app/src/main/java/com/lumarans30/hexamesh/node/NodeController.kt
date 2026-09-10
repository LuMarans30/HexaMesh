package com.lumarans30.hexamesh.node

import android.content.Context
import android.os.SystemClock
import com.lumarans30.hexamesh.bridge.RustEngine
import com.lumarans30.hexamesh.bridge.ServerConfig
import com.lumarans30.hexamesh.platform.LockManager
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import com.lumarans30.hexamesh.R
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
    private var healthThread: Thread? = null

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
                )
                engine.start(config)
            } catch (t: Throwable) {
                fail(t.message ?: "Failed to start the Rust engine")
            } finally {
                engineStarted.set(false)
            }
        }

        startHealthWatcher()
    }

    fun stop() {
        if (!stopping.compareAndSet(false, true)) return

        scope.launch {
            healthThread?.interrupt()
            healthThread?.join(1_000)
            healthThread = null

            runCatching { engine.stop() }

            engineThread?.let { t -> runCatching { t.join(2_000) } }
            engineThread = null

            engineStarted.set(false)
            locks.release()
            NodeState.post(NodeState.Stopped)
        }
    }

    private fun fail(message: String) {
        if (stopping.get()) return
        locks.release()
        NodeState.post(NodeState.Error(message))
    }

    private fun startHealthWatcher() {
        if (healthThread != null) return
        healthThread =
            thread(name = "hexamesh-health") {
                val deadline = SystemClock.elapsedRealtime() + HEALTH_STARTUP_DEADLINE_MS
                var everHealthy = false
                var misses = 0

                try {
                    while (!stopping.get()) {
                        if (isHealthy(SERVER_PORT)) {
                            everHealthy = true
                            misses = 0
                            if (!stopping.get()) {
                                NodeState.post(NodeState.Running(lanEndpoint()))
                            }
                        } else {
                            misses++
                            when {
                                everHealthy && misses >= HEALTH_MAX_MISSES -> {
                                    fail(app.getString(R.string.state_server_died))
                                    return@thread
                                }

                                !everHealthy && SystemClock.elapsedRealtime() > deadline -> {
                                    fail(app.getString(R.string.state_start_timeout))
                                    return@thread
                                }
                            }
                        }
                        Thread.sleep(HEALTH_POLL_INTERVAL_MS)
                    }
                } catch (_: InterruptedException) {
                    // Service is shutting down
                }
            }
    }

    private fun isHealthy(port: Int): Boolean =
        runCatching {
            val conn =
                URI.create("http://127.0.0.1:$port/health")
                    .toURL()
                    .openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 500
                conn.readTimeout = 800
                conn.requestMethod = "GET"
                conn.responseCode == 200
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(false)

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
        private const val HEALTH_POLL_INTERVAL_MS = 2_000L
        private const val HEALTH_STARTUP_DEADLINE_MS = 180_000L
        private const val HEALTH_MAX_MISSES = 3
    }
}