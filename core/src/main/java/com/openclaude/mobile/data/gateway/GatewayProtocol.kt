package com.openclaude.mobile.data.gateway

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Wire contract with an OpenClaw gateway.
 *
 * The gateway is the local control plane in OpenClaw: sessions, tools, events and
 * channel connections all live behind it, and every first-party client — Control
 * UI, CLI, TUI — is just a consumer of this surface.
 *
 * We keep our own typed copy rather than sharing upstream's TypeScript types
 * because the two are allowed to move at different speeds: the app must keep
 * working against the previous gateway release while the gateway moves on.
 *
 * Envelope is intentionally dumb: `{id, type, ...}` in, `{id, type, ...}` out, with
 * events carrying no id. That is enough for request/response and for a stream of
 * unsolicited events on the same socket.
 */
object GatewayProtocol {

    const val VERSION = 1
    const val PATH_STREAM = "/v1/stream"
    const val PATH_HEALTH = "/v1/health"
    const val PATH_INFO = "/v1/info"

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        classDiscriminator = "type"
    }

    // ------------------------------------------------------------- outbound

    @Serializable
    data class Hello(
        val token: String,
        val client: String = "openclaude-mobile",
        val version: Int = VERSION,
        @SerialName("device_name") val deviceName: String,
        val capabilities: List<String> = listOf("chat", "tools", "bots")
    )

    @Serializable
    data class UserMessage(
        val id: String,
        val sessionId: String,
        val text: String,
        val botId: String? = null,
        val attachments: List<String> = emptyList()
    )

    @Serializable
    data class Interrupt(val id: String, val sessionId: String)

    @Serializable
    data class ApprovalReply(
        val id: String,
        val requestId: String,
        val approved: Boolean,
        val remember: Boolean = false
    )

    @Serializable
    data class Ping(val id: String = "ping")

    // ------------------------------------------------------------- inbound

    @Serializable
    data class Ready(
        val gateway: String = "openclaw",
        @SerialName("gateway_version") val gatewayVersion: String = "",
        @SerialName("protocol_version") val protocolVersion: Int = VERSION,
        val sessions: Int = 0,
        val bots: List<String> = emptyList()
    )

    @Serializable
    data class Delta(
        val sessionId: String,
        val text: String,
        val botId: String? = null
    )

    @Serializable
    data class ToolCall(
        val sessionId: String,
        val callId: String,
        val tool: String,
        val input: String = ""
    )

    @Serializable
    data class ToolResult(
        val sessionId: String,
        val callId: String,
        val ok: Boolean,
        val output: String = "",
        val ms: Long = 0
    )

    @Serializable
    data class ApprovalRequest(
        val sessionId: String,
        val requestId: String,
        val tool: String,
        val summary: String
    )

    @Serializable
    data class BotEvent(
        val sessionId: String,
        val botId: String,
        val state: String,
        val detail: String = ""
    )

    @Serializable
    data class Done(
        val sessionId: String,
        val stopReason: String = "end_turn",
        val inputTokens: Int = 0,
        val outputTokens: Int = 0
    )

    @Serializable
    data class Failure(
        val sessionId: String? = null,
        val code: String = "error",
        val message: String
    )

    /**
     * Maps a raw frame to a typed event.
     *
     * Unknown types return null and are dropped with a log line. A gateway that adds
     * an event kind must not be able to crash a client that predates it.
     */
    fun parse(raw: String): Any? {
        val element = runCatching { json.parseToJsonElement(raw) }.getOrNull() ?: return null
        val type = (element as? kotlinx.serialization.json.JsonObject)
            ?.get("type")
            ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
            ?: return null

        return runCatching {
            when (type) {
                "ready" -> json.decodeFromString(Ready.serializer(), raw)
                "delta", "assistant_delta" -> json.decodeFromString(Delta.serializer(), raw)
                "tool_call" -> json.decodeFromString(ToolCall.serializer(), raw)
                "tool_result" -> json.decodeFromString(ToolResult.serializer(), raw)
                "approval_request" -> json.decodeFromString(ApprovalRequest.serializer(), raw)
                "bot_event" -> json.decodeFromString(BotEvent.serializer(), raw)
                "done", "turn_done" -> json.decodeFromString(Done.serializer(), raw)
                "error" -> json.decodeFromString(Failure.serializer(), raw)
                "pong" -> "pong"
                else -> null
            }
        }.getOrNull()
    }
}
