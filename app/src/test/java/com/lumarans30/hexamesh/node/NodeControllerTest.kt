package com.lumarans30.hexamesh.node

import com.lumarans30.hexamesh.bridge.Engine
import com.lumarans30.hexamesh.bridge.EngineStatus
import com.lumarans30.hexamesh.bridge.ServerConfig
import com.lumarans30.hexamesh.platform.Locks
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NodeControllerTest {

    private lateinit var locks: FakeLocks
    private lateinit var engine: FakeEngine
    private lateinit var settings: FakeSettings

    @Before
    fun setUp() {
        locks = FakeLocks()
        engine = FakeEngine()
        settings = FakeSettings()
    }

    private fun TestScope.newController(): NodeController {
        val controller =
            NodeController(
                NodeEnvironment(
                    nativeLibDir = "/lib",
                    cacheDir = "/cache",
                    apiKey = "key",
                    serverDiedMessage = "server died",
                    locks = locks,
                    settings = settings,
                ),
                engine,
                backgroundScope,
            )
        engine.stateProvider = { controller.state.value }
        return controller
    }

    @Test
    fun `no model posts idle without touching locks or engine`() = runTest {
        val controller = newController()

        controller.applyModel(null)

        assertEquals(NodeState.Idle, controller.state.value)
        assertEquals(0, locks.acquires)
        assertEquals(0, engine.started.size)
    }

    @Test
    fun `load acquires locks, starts engine, then reaches running`() = runTest {
        val controller = newController()
        engine.status = EngineStatus(EngineStatus.RUNNING, "ready")

        controller.applyModel(MODEL)

        assertEquals(NodeState.Starting(MODEL), controller.state.value)

        runCurrent()

        val running = controller.state.value
        assertTrue(running is NodeState.Running)
        assertEquals(MODEL, (running as NodeState.Running).modelPath)
        assertEquals(1, locks.acquires)

        val config = engine.started.single()
        assertEquals(MODEL, config.modelPath)
        assertEquals("/lib", config.nativeLibDir)
        assertEquals("/cache", config.cacheDir)
        assertEquals("key", config.apiKey)
        assertEquals(8080, config.port)

        controller.unload()
    }

    @Test
    fun `a custom port is threaded into the engine and the serving url`() = runTest {
        val controller = newController()
        settings.portValue = 9090
        engine.status = EngineStatus(EngineStatus.RUNNING, "ready")

        controller.applyModel(MODEL)
        runCurrent()

        assertEquals(9090, engine.started.single().port)

        val running = controller.state.value
        assertTrue(running is NodeState.Running)
        assertTrue((running as NodeState.Running).serverUrl.endsWith(":9090"))

        controller.unload()
    }

    @Test
    fun `each start re-reads the port from settings`() = runTest {
        val controller = newController()
        engine.status = EngineStatus(EngineStatus.RUNNING, "ready")

        controller.applyModel(MODEL)
        runCurrent()
        controller.unload()

        settings.portValue = 9090
        controller.applyModel(MODEL)
        runCurrent()

        assertEquals(listOf(8080, 9090), engine.started.map { it.port })
        val running = controller.state.value
        assertTrue(running is NodeState.Running)
        assertTrue((running as NodeState.Running).serverUrl.endsWith(":9090"))

        controller.unload()
    }

    @Test
    fun `launch args are threaded into the engine config`() = runTest {
        val controller = newController()
        settings.launchArgsValue = listOf("-t", "8", "--no-warmup")
        engine.status = EngineStatus(EngineStatus.RUNNING, "ready")

        controller.applyModel(MODEL)
        runCurrent()

        assertEquals("-t\n8\n--no-warmup", engine.started.single().extraArgs)

        controller.unload()
    }

    @Test
    fun `unload announces stopping before releasing locks`() = runTest {
        val controller = newController()
        controller.applyModel(MODEL)
        runCurrent()

        engine.stateWhenStopped = null
        controller.unload()

        assertEquals(NodeState.Stopping(MODEL), engine.stateWhenStopped)
        assertEquals(NodeState.Stopped, controller.state.value)
        assertEquals(1, locks.releases)
        assertEquals(1, engine.stopCount)
    }

    @Test
    fun `unload without a running node is a no-op`() = runTest {
        val controller = newController()

        controller.unload()

        assertEquals(NodeState.Stopped, controller.state.value)
        assertEquals(0, locks.releases)
        assertEquals(0, engine.stopCount)
    }

    @Test
    fun `concurrent loads only start the engine once`() = runTest {
        val controller = newController()

        List(5) { launch { controller.applyModel(MODEL) } }.forEach { it.join() }

        assertEquals(1, engine.started.size)
        assertEquals(1, locks.acquires)

        controller.unload()
    }

    @Test
    fun `concurrent unloads only stop the engine once`() = runTest {
        val controller = newController()
        controller.applyModel(MODEL)
        runCurrent()

        List(5) { launch { controller.unload() } }.forEach { it.join() }

        assertEquals(1, engine.stopCount)
        assertEquals(1, locks.releases)
    }

    @Test
    fun `a load after an unload starts a fresh engine`() = runTest {
        val controller = newController()

        controller.applyModel(MODEL)
        runCurrent()
        controller.unload()
        controller.applyModel(MODEL)
        runCurrent()

        assertEquals(2, engine.started.size)
        assertEquals(2, locks.acquires)
        assertEquals(1, locks.releases)

        controller.unload()
        assertEquals(2, locks.releases)
    }

    @Test
    fun `engine start failure posts error and releases locks`() = runTest {
        val controller = newController()
        engine.startError = IllegalStateException("boom")

        controller.applyModel(MODEL)

        val state = controller.state.value
        assertTrue(state is NodeState.Error)
        assertEquals("boom", (state as NodeState.Error).message)
        assertEquals(1, locks.acquires)
        assertEquals(1, locks.releases)
        assertEquals(1, engine.stopCount)
    }

    @Test
    fun `engine reporting error tears the node down`() = runTest {
        val controller = newController()
        engine.status = EngineStatus(EngineStatus.ERROR, "died")

        controller.applyModel(MODEL)
        runCurrent()

        val state = controller.state.value
        assertTrue(state is NodeState.Error)
        assertEquals("died", (state as NodeState.Error).message)
        assertEquals(1, locks.releases)
        assertEquals(1, engine.stopCount)
    }

    private class FakeLocks : Locks {
        var acquires = 0
        var releases = 0

        override fun acquire() {
            acquires++
        }

        override fun release() {
            releases++
        }
    }

    private class FakeSettings(
        var portValue: Int = 8080,
        var launchArgsValue: List<String> = emptyList(),
    ) : NodeSettings {
        override val port: Int
            get() = portValue

        override val launchArgs: List<String>
            get() = launchArgsValue
    }

    private class FakeEngine : Engine {
        val started = mutableListOf<ServerConfig>()
        var stopCount = 0
        var status: EngineStatus? = EngineStatus(EngineStatus.STARTING, null)
        var startError: Throwable? = null
        var stateWhenStopped: NodeState? = null
        var stateProvider: (() -> NodeState)? = null

        override fun start(config: ServerConfig) {
            startError?.let { throw it }
            started += config
        }

        override fun stop() {
            stopCount++
            stateWhenStopped = stateProvider?.invoke()
        }

        override fun pollStatus(): EngineStatus? = status
    }

    private companion object {
        const val MODEL = "/models/tiny.gguf"
    }
}
