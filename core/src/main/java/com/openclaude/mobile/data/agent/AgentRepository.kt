package com.openclaude.mobile.data.agent

import com.openclaude.mobile.data.runtime.ProotLauncher
import com.openclaude.mobile.data.runtime.RuntimePaths
import com.openclaude.mobile.data.runtime.SessionRunner
import com.openclaude.mobile.util.Logs
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Front door for the agent. One process at a time, deliberately: two live agents
 * on a phone means two copies of node and a thermal cliff.
 */
class AgentRepository(
    private val paths: RuntimePaths,
    private val providerStore: ProviderStore
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val launcher = ProotLauncher(paths)

    private var runner: SessionRunner? = null

    private val _events = MutableSharedFlow<AgentEvent>(replay = 0, extraBufferCapacity = 256, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: Flow<AgentEvent> = _events.asSharedFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    suspend fun ensureStarted(sessionId: String): List<String> {
        val problems = launcher.preflight()
        if (problems.isNotEmpty()) throw IllegalStateException(problems.joinToString("; "))
        if (runner?.isAlive() == true) return problems

        val env = launcher.baseEnv(providerStore.envForActive())
        val cmd = launcher.defaultAgentCommand(providerStore.envForActive())
        paths.work.mkdirs()

        val fresh = SessionRunner(cmd, env, paths.work) { line ->
            Logs.w("agent.stderr", com.openclaude.mobile.util.Logs.redact(line))
        }
        runner = fresh
        _connected.value = true

        scope.launch {
            fresh.lines().collect { line -> dispatch(line) }
            _connected.value = false
            Logs.i("agent", "process exited")
        }

        fresh.send(json.encodeToString(UserTurn.serializer(), UserTurn(sessionId = sessionId, text = "", agentCount = 1)))
        return problems
    }

    suspend fun send(text: String, sessionId: String, agentCount: Int = 1) {
        val r = runner ?: throw IllegalStateException("agent not started")
        val payload = json.encodeToString(UserTurn.serializer(), UserTurn(sessionId = sessionId, text = text, agentCount = agentCount))
        r.send(payload)
    }

    suspend fun interrupt(sessionId: String) {
        val r = runner ?: return
        r.send(json.encodeToString(Interrupt.serializer(), Interrupt(sessionId = sessionId)))
    }

    fun stop() {
        runner?.stop()
        runner = null
        _connected.value = false
    }

    /**
     * Parses one wire line into an [AgentEvent]. Unknown types are logged and
     * dropped rather than crashing the stream: the CLI may add event kinds.
     */
    private suspend fun dispatch(line: String) {
        val element = runCatching { json.parseToJsonElement(line) as JsonObject }.getOrNull()
        if (element == null) {
            _events.emit(AgentEvent.AssistantDelta(line))
            return
        }
        val type = element["type"]?.jsonPrimitive?.content ?: return
        val event: AgentEvent? = when (type) {
            "assistant_delta" -> AgentEvent.AssistantDelta(element["text"]?.jsonPrimitive?.content ?: "")
            "tool_call" -> AgentEvent.ToolCall(
                id = element["id"]?.jsonPrimitive?.content ?: "",
                name = element["name"]?.jsonPrimitive?.content ?: "tool",
                input = element["input"]
            )
            "tool_result" -> AgentEvent.ToolResult(
                id = element["id"]?.jsonPrimitive?.content ?: "",
                ok = element["ok"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true,
                output = element["output"]?.jsonPrimitive?.content ?: ""
            )
            "agent_spawn" -> AgentEvent.AgentSpawn(
                id = element["id"]?.jsonPrimitive?.content ?: "",
                role = element["role"]?.jsonPrimitive?.content ?: "worker",
                task = element["task"]?.jsonPrimitive?.content ?: ""
            )
            "agent_done" -> AgentEvent.AgentDone(
                id = element["id"]?.jsonPrimitive?.content ?: "",
                summary = element["summary"]?.jsonPrimitive?.content ?: ""
            )
            "usage" -> AgentEvent.Usage(
                inputTokens = element["input_tokens"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                outputTokens = element["output_tokens"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            )
            "turn_done" -> AgentEvent.TurnDone(element["stop_reason"]?.jsonPrimitive?.content ?: "end_turn")
            "error" -> AgentEvent.Failure(
                element["message"]?.jsonPrimitive?.content ?: "unknown error",
                element["retryable"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
            )
            else -> {
                Logs.d("agent", "unhandled event type $type")
                null
            }
        }
        event?.let { _events.emit(it) }
    }
}
