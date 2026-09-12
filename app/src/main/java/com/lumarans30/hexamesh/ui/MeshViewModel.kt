package com.lumarans30.hexamesh.ui

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import com.lumarans30.hexamesh.bridge.ServerRole
import com.lumarans30.hexamesh.logs.readMemoryInfo
import com.lumarans30.hexamesh.mesh.DEFAULT_RPC_PORT
import com.lumarans30.hexamesh.mesh.MAX_RPC_SERVERS
import com.lumarans30.hexamesh.mesh.PeerNode
import com.lumarans30.hexamesh.mesh.isReachable
import com.lumarans30.hexamesh.mesh.memoryAttributes
import com.lumarans30.hexamesh.mesh.mergePeers
import com.lumarans30.hexamesh.mesh.meshServiceName
import com.lumarans30.hexamesh.mesh.parseFallbackPeers
import com.lumarans30.hexamesh.mesh.rankPeers
import com.lumarans30.hexamesh.mesh.tcpLatencyMs
import com.lumarans30.hexamesh.mesh.withoutSelf
import com.lumarans30.hexamesh.platform.MeshSettings
import com.lumarans30.hexamesh.platform.NsdAdvertiser
import com.lumarans30.hexamesh.platform.NsdDiscovery
import com.lumarans30.hexamesh.platform.ServerSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

/** Discovers mesh peers on the LAN, pings them, and merges the fallback list. */
class MeshViewModel(application: Application) : AndroidViewModel(application) {

    private val discovery = NsdDiscovery(application)
    private val advertiser = NsdAdvertiser(application)
    private val settings = MeshSettings.from(application)
    private val serverSettings = ServerSettings.from(application)

    private val selfName = MutableStateFlow<String?>(null)

    /** Discovered + manual peers, before latency is folded in. */
    private val merged = MutableStateFlow<List<PeerNode>>(emptyList())

    private val latencies = MutableStateFlow<Map<String, Long?>>(emptyMap())

    private val _peers = MutableStateFlow<List<PeerNode>>(emptyList())
    val peers: StateFlow<List<PeerNode>> = _peers.asStateFlow()

    val fallbackPeers: StateFlow<String> = settings.fallbackPeersFlow

    val discoverable: StateFlow<Boolean> = settings.discoverableFlow

    val usePeers: StateFlow<Boolean> = settings.usePeersFlow

    private val _worker = MutableStateFlow(serverSettings.role == ServerRole.RPC)
    val worker: StateFlow<Boolean> = _worker.asStateFlow()

    fun applyFallbackPeers(text: String) = settings.setFallbackPeers(text)

    fun setDiscoverable(discoverable: Boolean) = settings.setDiscoverable(discoverable)

    fun setUsePeers(usePeers: Boolean) = settings.setUsePeers(usePeers)

    fun setWorker(worker: Boolean) {
        serverSettings.setRole(if (worker) ServerRole.RPC else ServerRole.SERVER)
        _worker.value = worker
    }

    /**
     * Endpoints for llama-server's `--rpc`, or null when meshing is off or nothing
     * is reachable. Ranked and capped at [MAX_RPC_SERVERS]; call at start time.
     */
    fun rpcEndpoints(): String? {
        if (!usePeers.value || _worker.value) return null

        val now = System.currentTimeMillis()
        return rankPeers(_peers.value)
            .filter { isReachable(it, now) }
            .take(MAX_RPC_SERVERS)
            .takeIf { it.isNotEmpty() }
            ?.joinToString(",") { "${it.host}:${it.port}" }
    }

    /** Browses, advertises and pings while the Mesh tab is composed. */
    suspend fun observe() = coroutineScope {
        launch {
            combine(discovery.discover(), fallbackPeers, selfName) { discovered, fallback, self ->
                withoutSelf(mergePeers(discovered, parseFallbackPeers(fallback)), self)
            }
                .collect { merged.value = it }
        }

        launch {
            combine(merged, latencies) { list, measured ->
                list.map { peer ->
                    peer.copy(stats = peer.stats.copy(latencyMs = measured[peer.endpoint]))
                }
            }
                .collect { _peers.value = it }
        }

        launch {
            while (isActive) {
                val targets = merged.value
                if (targets.isNotEmpty()) {
                    latencies.value =
                        withContext(Dispatchers.IO) {
                            targets.associate {
                                it.endpoint to tcpLatencyMs(it.host, it.port)
                            }
                        }
                }
                delay(PING_INTERVAL_MS.milliseconds)
            }
        }

        launch {
            discoverable.collectLatest { on ->
                if (on) {
                    val memory = withContext(Dispatchers.IO) { readMemoryInfo() }
                    advertiser
                        .advertise(
                            name = meshServiceName(Build.MODEL),
                            port = DEFAULT_RPC_PORT,
                            attributes = memoryAttributes(memory?.availableBytes, memory?.totalBytes),
                        )
                        .collect { selfName.value = it }
                } else {
                    selfName.value = null
                }
            }
        }
    }

    private companion object {
        const val PING_INTERVAL_MS = 5_000L
    }
}
