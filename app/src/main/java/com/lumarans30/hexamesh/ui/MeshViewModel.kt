package com.lumarans30.hexamesh.ui

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import com.lumarans30.hexamesh.mesh.quantizeMemory
import com.lumarans30.hexamesh.mesh.rankPeers
import com.lumarans30.hexamesh.mesh.tcpLatencies
import com.lumarans30.hexamesh.mesh.withoutSelf
import com.lumarans30.hexamesh.node.NodeState
import com.lumarans30.hexamesh.platform.MeshSettings
import com.lumarans30.hexamesh.platform.NsdAdvertiser
import com.lumarans30.hexamesh.platform.NsdDiscovery
import com.lumarans30.hexamesh.platform.ServerSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

/** Discovers mesh peers on the LAN, pings them, and merges the fallback list. */
class MeshViewModel(
    application: Application,
) : AndroidViewModel(application) {
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

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    val fallbackPeers: StateFlow<String> = settings.fallbackPeers

    val discoverable: StateFlow<Boolean> = settings.discoverable

    val usePeers: StateFlow<Boolean> = settings.usePeers

    private val _worker = MutableStateFlow(serverSettings.role == ServerRole.RPC)
    val worker: StateFlow<Boolean> = _worker.asStateFlow()

    private val wired = MutableStateFlow<Set<String>>(emptySet())

    private var observation: Job? = null

    fun start() {
        if (observation?.isActive == true) return
        observation = viewModelScope.launch { observe() }
    }

    fun stop() {
        observation?.cancel()
        observation = null
    }

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
        if (!usePeers.value || _worker.value) {
            wired.value = emptySet()
            return null
        }

        val now = System.currentTimeMillis()
        val selected =
            rankPeers(_peers.value)
                .filter { isReachable(it, now) }
                .take(MAX_RPC_SERVERS)

        wired.value = selected.mapTo(mutableSetOf()) { it.endpoint }
        return selected.takeIf { it.isNotEmpty() }?.joinToString(",") { "${it.host}:${it.port}" }
    }

    fun onNodeState(state: NodeState) {
        if (state is NodeState.Stopped || state is NodeState.Error) wired.value = emptySet()
    }

    suspend fun ensureFreshPeers(timeoutMs: Long = PEER_WAIT_MS) {
        if (!usePeers.value || _worker.value) return
        if (_peers.value.any { isReachable(it, System.currentTimeMillis()) }) return

        start()
        _scanning.value = true
        try {
            withTimeoutOrNull(timeoutMs.milliseconds) {
                _peers.first { peers -> peers.any { isReachable(it, System.currentTimeMillis()) } }
            }
        } finally {
            _scanning.value = false
        }
    }

    private suspend fun observe() =
        coroutineScope {
            launch {
                combine(discovery.discover(), fallbackPeers, selfName) { discovered, fallback, self ->
                    withoutSelf(mergePeers(discovered, parseFallbackPeers(fallback)), self)
                }.collect { merged.value = it }
            }

            launch {
                combine(merged, latencies, wired) { list, measured, wiredSet ->
                    list.map { peer ->
                        val inUse = peer.endpoint in wiredSet
                        peer.copy(
                            stats =
                                peer.stats.copy(
                                    latencyMs = if (inUse) null else measured[peer.endpoint],
                                    inUse = inUse,
                                ),
                        )
                    }
                }.collect { _peers.value = it }
            }

            launch {
                combine(merged, wired) { list, wiredSet ->
                    list.filterNot { it.endpoint in wiredSet }
                }.collectLatest { targets ->
                    while (this@launch.isActive) {
                        if (targets.isNotEmpty()) {
                            latencies.value = tcpLatencies(targets)
                        }
                        delay(PING_INTERVAL_MS.milliseconds)
                    }
                }
            }

            launch {
                discoverable.collectLatest { on ->
                    if (on) {
                        advertiser
                            .advertise(
                                name = meshServiceName(Build.MODEL),
                                port = DEFAULT_RPC_PORT,
                                attributes = {
                                    val memory = withContext(Dispatchers.IO) { readMemoryInfo() }
                                    memoryAttributes(
                                        quantizeMemory(memory?.availableBytes),
                                        memory?.totalBytes,
                                    )
                                },
                            ).collect { selfName.value = it }
                    } else {
                        selfName.value = null
                    }
                }
            }
        }

    private companion object {
        const val PING_INTERVAL_MS = 5_000L
        const val PEER_WAIT_MS = 2_000L
    }
}
