package com.openclaude.mobile.tools.code

import com.openclaude.mobile.tools.Tool
import com.openclaude.mobile.tools.ToolResult
import com.openclaude.mobile.tools.ToolSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Runs a short snippet on the device.
 *
 * Three languages, because between them they cover the questions people actually
 * ask a phone assistant: Python for data and quick scripts, Node for anything that
 * came out of a web example, and shell for looking at the device itself.
 *
 * Containment is the workspace directory plus a wall clock. There is no CPU cap and
 * no memory cap — Android has no cgroups for an unprivileged app — so the honest
 * description is "a timeout and a working directory", not "a sandbox". The policy
 * list below is a guard rail against obvious mistakes, not a security boundary, and
 * `docs/TOOLS.md` says so in the same words.
 */
class CodeTool(private val workspace: File) : Tool {

    override val spec = ToolSpec(
        name = "code",
        title = "Run code",
        description = "Run a short python, node or shell snippet on the device and return its output.",
        inputSchema = """
            {"type":"object","properties":{
              "language":{"type":"string","enum":["python","node","shell"]},
              "code":{"type":"string"},
              "timeout_seconds":{"type":"integer","minimum":1,"maximum":60}
            },"required":["language","code"]}
        """.trimIndent(),
        risk = ToolSpec.Risk.EXEC,
        timeoutMs = 65_000
    )

    override suspend fun run(input: JsonObject): ToolResult = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val language = input["language"]?.jsonPrimitive?.content ?: "python"
        val code = input["code"]?.jsonPrimitive?.content ?: return@withContext ToolResult.fail("missing code")
        val timeout = (input["timeout_seconds"]?.jsonPrimitive?.content?.toIntOrNull() ?: 15)
            .coerceIn(1, 60)

        if (code.length > 20_000) return@withContext ToolResult.fail("snippet too long")

        val denied = DENY.firstOrNull { code.contains(it, ignoreCase = true) }
        if (denied != null) {
            return@withContext ToolResult.fail("refused: snippet contains '$denied'")
        }

        val (interpreter, extension) = when (language) {
            "python" -> listOf("/opt/python/bin/python3") to "py"
            "node" -> listOf("/opt/node/bin/node") to "js"
            "shell" -> listOf("/system/bin/sh") to "sh"
            else -> return@withContext ToolResult.fail("unsupported language: $language")
        }

        val script = File(workspace, "run-${System.currentTimeMillis()}.$extension")
        script.parentFile?.mkdirs()
        script.writeText(code)

        try {
            val command = if (language == "shell") interpreter + script.absolutePath
            else interpreter + script.absolutePath

            val process = ProcessBuilder(command)
                .directory(workspace)
                .redirectErrorStream(true)
                .start()

            val output = StringBuilder()
            val reader = Thread {
                runCatching {
                    process.inputStream.bufferedReader().forEachLine { line ->
                        synchronized(output) {
                            if (output.length < ToolResult.MAX_OUTPUT_CHARS) output.appendLine(line)
                        }
                    }
                }
            }.apply { isDaemon = true; start() }

            val finished = withTimeoutOrNull(timeout * 1000L) {
                process.waitFor(timeout.toLong(), TimeUnit.SECONDS)
            } ?: false

            if (!finished) {
                process.destroyForcibly()
                reader.interrupt()
                return@withContext ToolResult.fail("timed out after ${timeout}s", System.currentTimeMillis() - started)
            }

            reader.join(1_000)
            val text = synchronized(output) { output.toString() }
            val exit = process.exitValue()

            val body = if (text.isBlank()) "(no output)" else text
            if (exit != 0) {
                ToolResult(
                    ok = false,
                    output = body,
                    ms = System.currentTimeMillis() - started,
                    error = "exit code $exit"
                )
            } else {
                ToolResult.ok(body, System.currentTimeMillis() - started)
            }
        } catch (t: Throwable) {
            ToolResult.fail(t.message ?: "could not run the snippet", System.currentTimeMillis() - started)
        } finally {
            runCatching { script.delete() }
        }
    }

    private companion object {
        val DENY = listOf(
            "rm -rf /", "mkfs", "dd if=/dev/zero of=/dev", ":(){", "shutdown", "reboot",
            "/system/bin/su", "pm uninstall", "settings put global"
        )
    }
}
