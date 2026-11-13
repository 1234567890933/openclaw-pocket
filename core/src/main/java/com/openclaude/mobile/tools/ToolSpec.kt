package com.openclaude.mobile.tools

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * A tool the agent can call.
 *
 * Risk is not decoration: the UI uses it to decide whether a call runs silently or
 * asks first. Anything that can write outside the workspace or touch the network is
 * at least [Risk.WRITE].
 */
@Serializable
data class ToolSpec(
    val name: String,
    val title: String,
    val description: String,
    val inputSchema: String,
    val risk: Risk = Risk.READ,
    val timeoutMs: Long = 20_000
) {
    enum class Risk {
        /** Reads local state only. */
        READ,

        /** Can modify the workspace. */
        WRITE,

        /** Leaves the device. */
        NETWORK,

        /** Runs arbitrary code. */
        EXEC
    }
}

data class ToolResult(
    val ok: Boolean,
    val output: String,
    val ms: Long,
    val truncated: Boolean = false,
    val error: String? = null
) {
    companion object {
        const val MAX_OUTPUT_CHARS = 24_000

        fun ok(output: String, ms: Long): ToolResult {
            val trimmed = output.length > MAX_OUTPUT_CHARS
            return ToolResult(
                ok = true,
                output = if (trimmed) output.take(MAX_OUTPUT_CHARS) + "\n… truncated" else output,
                ms = ms,
                truncated = trimmed
            )
        }

        fun fail(message: String, ms: Long = 0): ToolResult =
            ToolResult(ok = false, output = "", ms = ms, error = message)
    }
}

interface Tool {
    val spec: ToolSpec
    suspend fun run(input: JsonObject): ToolResult
}
