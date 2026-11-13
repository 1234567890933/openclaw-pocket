package com.openclaude.mobile.tools

import android.content.Context
import com.openclaude.mobile.tools.browser.BrowserTool
import com.openclaude.mobile.tools.code.CodeTool
import com.openclaude.mobile.tools.files.FilesTool
import com.openclaude.mobile.tools.http.HttpTool
import com.openclaude.mobile.tools.search.SearchTool
import com.openclaude.mobile.util.Logs
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * The tool belt.
 *
 * Six tools, chosen because between them they cover what a phone assistant is
 * actually asked to do: look something up, read or write a file, run a snippet,
 * fetch a page, and browse a site that needs JavaScript.
 *
 * Everything runs **in the app process**, not in the gateway's shell. That is the
 * important inversion: the agent asks the phone for a capability, the phone decides
 * whether to grant it, and the sandbox is the app's own permissions rather than a
 * shell that happens to be on the device.
 */
class ToolRegistry(workspace: File, context: Context? = null) {

    private val tools = ConcurrentHashMap<String, Tool>()

    init {
        listOf(
            FilesTool(workspace),
            CodeTool(workspace),
            SearchTool(),
            HttpTool(),
            BrowserTool(context)
        ).forEach { register(it) }
    }

    fun register(tool: Tool) {
        tools[tool.spec.name] = tool
        Logs.d("tools", "registered ${tool.spec.name} (${tool.spec.risk})")
    }

    fun specs(): List<ToolSpec> = tools.values.map { it.spec }.sortedBy { it.name }

    fun spec(name: String): ToolSpec? = tools[name]?.spec

    suspend fun call(name: String, input: JsonObject): ToolResult {
        val tool = tools[name] ?: return ToolResult.fail("unknown tool: $name")
        val started = System.currentTimeMillis()
        return runCatching { tool.run(input) }
            .getOrElse { error ->
                Logs.e("tools", "$name threw", error)
                ToolResult.fail(error.message ?: error.javaClass.simpleName, System.currentTimeMillis() - started)
            }
    }

    /** JSON array of specs, in the shape a model expects for function calling. */
    fun catalogueJson(): String = buildString {
        append('[')
        specs().forEachIndexed { index, spec ->
            if (index > 0) append(',')
            append("""{"name":""").append('"').append(spec.name).append('"')
            append(""","description":""").append('"')
                .append(spec.description.replace("\"", "\\\"").replace("\n", " "))
                .append('"')
            append(""","parameters":""").append(spec.inputSchema)
            append(""","risk":""").append('"').append(spec.risk.name.lowercase()).append('"')
            append('}')
        }
        append(']')
    }
}
