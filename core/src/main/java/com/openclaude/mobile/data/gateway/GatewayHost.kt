package com.openclaude.mobile.data.gateway

import com.openclaude.mobile.data.runtime.ProotLauncher
import com.openclaude.mobile.data.runtime.RuntimePaths
import com.openclaude.mobile.util.Constants
import com.openclaude.mobile.util.Logs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.min
import kotlin.random.Random

/**
 * Runs the OpenClaw gateway *on the phone*.
 *
 * Upstream, the gateway is a desktop control plane and a phone is a client or a
 * node. This class closes that gap: it starts `openclaw gateway` inside the same
 * proot userland the agent already uses, bound to loopback on a port picked at
 * runtime, and supervises it for the life of the app.
 *
 * Design points that matter:
 *
 *  - **Loopback only.** The bind address is 127.0.0.1, never 0.0.0.0. A gateway on a
 *    phone that listens on the LAN is a remote-code-execution surface with a battery.
 *  - **Ephemeral port.** Picked, then confirmed by an actual TCP connect before the
 *    UI is told it is ready. Ports get taken by other apps; a hardcoded one is a bug
 *    that only appears on other people's phones.
 *  - **Restart with backoff.** A gateway that dies at startup dies again. Backoff
 *    caps at 30s and gives up after five attempts, because a crash loop on a phone
 *    is a battery fire, not a service.
 */
class GatewayHost(
    private val paths: RuntimePaths,
    private val pairing: GatewayPairing
) {

    sealed interface State {
        data object Stopped : State
        data class Starting(val attempt: Int) : State
        data class Running(val port: Int, val pid: Long) : State
        data class Failed(val reason: String, val willRetry: Boolean) : State
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val launcher = ProotLauncher(paths)

    private val _state = MutableStateFlow<State>(State.Stopped)
    val state: StateFlow<State> = _state.asStateFlow()

    private var process: Process? = null
    private var supervisor: Job? = null
    private var attempts = 0
    private var port = 0

    fun endpoint(): GatewayPairing.Endpoint = pairing.hostEndpoint(port)

    fun start() {
        if (supervisor?.isActive == true) return
        supervisor = scope.launch {
            attempts = 0
            while (attempts < MAX_ATTEMPTS) {
                attempts++
                _state.value = State.Starting(attempts)
                val ok = attemptStart()
                if (ok) {
                    // Watch until it exits; a clean exit stops the loop.
                    val proc = process ?: return@launch
                    proc.waitFor()
                    if (_state.value is State.Stopped) return@launch
                    Logs.w("gateway", "gateway exited with ${proc.exitValue()}")
                    _state.value = State.Failed("gateway exited (${proc.exitValue()})", willRetry = true)
                }
                val backoff = min(30_000L, 1_000L * (1L shl (attempts - 1)))
                Logs.i("gateway", "restarting in ${backoff}ms (attempt $attempts)")
                delay(backoff)
            }
            _state.value = State.Failed("gave up after $attempts attempts", willRetry = false)
        }
    }

    private suspend fun attemptStart(): Boolean = withContext(Dispatchers.IO) {
        val problems = launcher.preflight()
        if (problems.isNotEmpty()) {
            _state.value = State.Failed(problems.joinToString("; "), willRetry = false)
            return@withContext false
        }

        port = pickPort()
        val token = pairing.hostToken()
        val workDir = File(paths.rootfs, "root/openclaw").apply { mkdirs() }

        val guestArgs = listOf(
            paths.guestNode,
            "/opt/openclaw/node_modules/openclaw/openclaw.mjs",
            "gateway",
            "run",
            "--host", "127.0.0.1",
            "--port", port.toString(),
            "--state-dir", "/root/openclaw/state",
            "--no-telemetry"
        )

        val env = launcher.baseEnv(
            mapOf(
                "OPENCLAW_GATEWAY_TOKEN" to token,
                "OPENCLAW_HOME" to "/root/openclaw",
                "OPENCLAW_TOOL_BRIDGE" to "http://127.0.0.1:${Constants.TOOL_BRIDGE_PORT}",
                "NODE_OPTIONS" to "--max-old-space-size=320"
            )
        )

        val cmd = launcher.buildCommand(guestArgs, env)
        Logs.i("gateway", "starting on port $port")

        val proc = runCatching {
            ProcessBuilder(cmd).directory(workDir).redirectErrorStream(true).start()
        }.getOrElse {
            _state.value = State.Failed(it.message ?: "spawn failed", willRetry = true)
            return@withContext false
        }
        process = proc
        drain(proc.inputStream)

        // Wait for the socket to actually accept before telling anyone it is up.
        val deadline = System.currentTimeMillis() + START_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (!proc.isAlive) {
                _state.value = State.Failed("exited during startup", willRetry = true)
                return@withContext false
            }
            if (probe(port)) {
                _state.value = State.Running(port, System.identityHashCode(proc).toLong())
                Logs.i("gateway", "ready on $port")
                return@withContext true
            }
            delay(250)
        }
        _state.value = State.Failed("timed out waiting for the gateway", willRetry = true)
        stopProcess()
        false
    }

    fun stop() {
        _state.value = State.Stopped
        supervisor?.cancel()
        supervisor = null
        stopProcess()
    }

    private fun stopProcess() {
        val proc = process ?: return
        runCatching { proc.outputStream.close() }
        proc.destroy()
        if (proc.isAlive) proc.destroyForcibly()
        process = null
    }

    /** A real TCP connect, not a file-exists check. */
    private fun probe(port: Int): Boolean = runCatching {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", port), 400)
            true
        }
    }.getOrDefault(false)

    private fun pickPort(): Int {
        repeat(12) {
            val candidate = Random.nextInt(20_000, 60_000)
            if (!probe(candidate)) return candidate
        }
        return 18789
    }

    private fun drain(stream: InputStream) {
        Thread {
            runCatching {
                stream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        if (line.isNotBlank()) {
                            Logs.d("gateway.out", com.openclaude.mobile.util.Logs.redact(line))
                        }
                    }
                }
            }
        }.apply { isDaemon = true; name = "gateway-stdout"; start() }
    }

    /** Sends one control line to the gateway's stdin, used for graceful shutdown. */
    fun writeControl(line: String) {
        val proc = process ?: return
        runCatching {
            val out: OutputStream = proc.outputStream
            out.write((line + "\n").toByteArray())
            out.flush()
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 5
        const val START_TIMEOUT_MS = 45_000L
    }
}
