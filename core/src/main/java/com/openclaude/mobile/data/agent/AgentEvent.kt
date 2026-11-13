package com.openclaude.mobile.data.agent

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Wire format between the app and the guest CLI. One JSON object per line, both
 * directions. The schema mirrors the CLI's `--json-stream` mode so we do not have
 * to parse ANSI terminal output, which is what makes a GUI wrapper fragile.
 */
@Serializable
sealed interface AgentEvent {

    @Serializable
    @SerialName("assistant_delta")
    data class AssistantDelta(val text: String) : AgentEvent

    @Serializable
    @SerialName("tool_call")
    data class ToolCall(
        val id: String,
        val name: String,
        val input: JsonElement? = null
    ) : AgentEvent

    @Serializable
    @SerialName("tool_result")
    data class ToolResult(
        val id: String,
        val ok: Boolean,
        val output: String = "",
        val truncated: Boolean = false
    ) : AgentEvent

    @Serializable
    @SerialName("agent_spawn")
    data class AgentSpawn(val id: String, val role: String, val task: String) : AgentEvent

    @Serializable
    @SerialName("agent_done")
    data class AgentDone(val id: String, val summary: String = "") : AgentEvent

    @Serializable
    @SerialName("usage")
    data class Usage(val inputTokens: Int = 0, val outputTokens: Int = 0) : AgentEvent

    @Serializable
    @SerialName("turn_done")
    data class TurnDone(val stopReason: String = "end_turn") : AgentEvent

    @Serializable
    @SerialName("error")
    data class Failure(val message: String, val retryable: Boolean = false) : AgentEvent
}

@Serializable
data class UserTurn(
    val type: String = "user_turn",
    val sessionId: String,
    val text: String,
    val agentCount: Int = 1
)

@Serializable
data class Interrupt(val type: String = "interrupt", val sessionId: String)
