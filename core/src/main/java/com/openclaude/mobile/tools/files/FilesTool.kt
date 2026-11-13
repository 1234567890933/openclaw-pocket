package com.openclaude.mobile.tools.files

import com.openclaude.mobile.tools.Tool
import com.openclaude.mobile.tools.ToolResult
import com.openclaude.mobile.tools.ToolSpec
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Workspace files.
 *
 * Every path is resolved against the workspace root and then checked again after
 * canonicalisation. The first check stops `../`; the second stops a symlink that
 * points out of the workspace, which is the one people forget.
 */
class FilesTool(private val workspace: File) : Tool {

    override val spec = ToolSpec(
        name = "files",
        title = "Files",
        description = "Read, write or list files inside the agent workspace. Paths are relative to the workspace root.",
        inputSchema = """
            {"type":"object","properties":{
              "action":{"type":"string","enum":["read","write","list","delete","stat"]},
              "path":{"type":"string","description":"relative path inside the workspace"},
              "content":{"type":"string","description":"for write"}
            },"required":["action","path"]}
        """.trimIndent(),
        risk = ToolSpec.Risk.WRITE
    )

    override suspend fun run(input: JsonObject): ToolResult {
        val started = System.currentTimeMillis()
        val action = input["action"]?.jsonPrimitive?.content ?: return ToolResult.fail("missing action")
        val rawPath = input["path"]?.jsonPrimitive?.content ?: return ToolResult.fail("missing path")

        val target = resolve(rawPath) ?: return ToolResult.fail("path escapes the workspace: $rawPath")

        return when (action) {
            "read" -> {
                if (!target.exists()) return ToolResult.fail("not found: $rawPath")
                if (target.isDirectory) return ToolResult.fail("$rawPath is a directory")
                if (target.length() > MAX_READ) return ToolResult.fail("file too large: ${target.length()} bytes")
                ToolResult.ok(target.readText(), System.currentTimeMillis() - started)
            }

            "write" -> {
                val content = input["content"]?.jsonPrimitive?.content
                    ?: return ToolResult.fail("missing content")
                if (content.length > MAX_WRITE) return ToolResult.fail("content too large")
                target.parentFile?.mkdirs()
                target.writeText(content)
                ToolResult.ok("wrote ${content.length} chars to $rawPath", System.currentTimeMillis() - started)
            }

            "list" -> {
                val dir = if (target.isDirectory) target else target.parentFile
                    ?: return ToolResult.fail("no such directory")
                if (!dir.exists()) return ToolResult.fail("not found: $rawPath")
                val entries = dir.listFiles().orEmpty()
                    .sortedBy { it.name }
                    .joinToString("\n") { file ->
                        val kind = if (file.isDirectory) "dir " else "file"
                        "%s %8d  %s".format(kind, if (file.isDirectory) 0 else file.length(), file.name)
                    }
                ToolResult.ok(entries.ifEmpty { "(empty)" }, System.currentTimeMillis() - started)
            }

            "delete" -> {
                if (!target.exists()) return ToolResult.fail("not found: $rawPath")
                val deleted = target.delete()
                ToolResult.ok(if (deleted) "deleted $rawPath" else "could not delete $rawPath",
                    System.currentTimeMillis() - started)
            }

            "stat" -> {
                if (!target.exists()) return ToolResult.fail("not found: $rawPath")
                val info = buildString {
                    appendLine("path: $rawPath")
                    appendLine("type: ${if (target.isDirectory) "directory" else "file"}")
                    appendLine("bytes: ${if (target.isDirectory) 0 else target.length()}")
                    appendLine("modified: ${target.lastModified()}")
                    appendLine("readable: ${target.canRead()}  writable: ${target.canWrite()}")
                }
                ToolResult.ok(info, System.currentTimeMillis() - started)
            }

            else -> ToolResult.fail("unknown action: $action")
        }
    }

    private fun resolve(rawPath: String): File? {
        if (rawPath.contains('\u0000')) return null
        val rootCanonical = workspace.canonicalFile
        val candidate = File(rootCanonical, rawPath).canonicalFile
        val rootPath = rootCanonical.path + File.separator
        if (candidate.path != rootCanonical.path && !candidate.path.startsWith(rootPath)) return null
        return candidate
    }

    private companion object {
        const val MAX_READ = 512 * 1024
        const val MAX_WRITE = 1024 * 1024
    }
}
