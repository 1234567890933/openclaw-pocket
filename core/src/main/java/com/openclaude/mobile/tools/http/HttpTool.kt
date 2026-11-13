package com.openclaude.mobile.tools.http

import com.openclaude.mobile.tools.Tool
import com.openclaude.mobile.tools.ToolResult
import com.openclaude.mobile.tools.ToolSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * Plain HTTP for the agent.
 *
 * Refuses loopback and private ranges. That is not paranoia: an agent that can reach
 * `127.0.0.1` can reach the gateway's own API, the tool bridge, and any other local
 * service on the phone — which would turn a web fetch into a privilege escalation.
 */
class HttpTool : Tool {

    override val spec = ToolSpec(
        name = "http",
        title = "Fetch a URL",
        description = "Fetch a URL over HTTP(S) and return the text. HTML is reduced to readable text.",
        inputSchema = """
            {"type":"object","properties":{
              "url":{"type":"string"},
              "method":{"type":"string","enum":["GET","POST"],"default":"GET"},
              "body":{"type":"string"},
              "content_type":{"type":"string","default":"application/json"}
            },"required":["url"]}
        """.trimIndent(),
        risk = ToolSpec.Risk.NETWORK,
        timeoutMs = 30_000
    )

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    override suspend fun run(input: JsonObject): ToolResult = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val url = input["url"]?.jsonPrimitive?.content?.trim()
            ?: return@withContext ToolResult.fail("missing url")
        val method = (input["method"]?.jsonPrimitive?.content ?: "GET").uppercase()

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return@withContext ToolResult.fail("only http and https are allowed")
        }
        val host = url.substringAfter("://").substringBefore('/').substringBefore(':')
        if (isPrivate(host)) {
            return@withContext ToolResult.fail("refusing to fetch a private address: $host")
        }

        val builder = Request.Builder().url(url).header("User-Agent", USER_AGENT)
        if (method == "POST") {
            val body = input["body"]?.jsonPrimitive?.content.orEmpty()
            val type = (input["content_type"]?.jsonPrimitive?.content ?: "application/json").toMediaType()
            builder.post(body.toRequestBody(type))
        }

        runCatching {
            http.newCall(builder.build()).execute().use { response ->
                val stream = response.body?.byteStream()
                    ?: return@withContext ToolResult.fail("empty body")
                val buffer = ByteArray(MAX_BYTES)
                var read = 0
                while (read < MAX_BYTES) {
                    val n = stream.read(buffer, read, MAX_BYTES - read)
                    if (n < 0) break
                    read += n
                }
                val contentType = response.header("Content-Type").orEmpty()
                val raw = String(buffer, 0, read, Charsets.UTF_8)
                val text = if (contentType.contains("html")) readable(raw) else raw

                val header = "HTTP ${response.code}  ${contentType.ifBlank { "unknown type" }}  ${read} bytes\n\n"
                if (!response.isSuccessful) {
                    ToolResult(
                        ok = false,
                        output = header + text,
                        ms = System.currentTimeMillis() - started,
                        error = "HTTP ${response.code}"
                    )
                } else {
                    ToolResult.ok(header + text, System.currentTimeMillis() - started)
                }
            }
        }.getOrElse { error ->
            ToolResult.fail(error.message ?: "request failed", System.currentTimeMillis() - started)
        }
    }

    private fun isPrivate(host: String): Boolean {
        val lower = host.lowercase()
        if (lower == "localhost" || lower == "::1" || lower.endsWith(".local")) return true
        val literal = runCatching { InetAddress.getByName(lower) }.getOrNull() ?: return false
        return literal.isLoopbackAddress || literal.isSiteLocalAddress || literal.isLinkLocalAddress
    }

    /**
     * Crude HTML reduction: drop script and style bodies, strip tags, collapse
     * whitespace. A full parser would be better and would also be 400 KB of code for
     * a job the model can finish itself.
     */
    private fun readable(html: String): String = html
        .replace(Regex("(?is)<script.*?</script>"), " ")
        .replace(Regex("(?is)<style.*?</style>"), " ")
        .replace(Regex("(?is)<noscript.*?</noscript>"), " ")
        .replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("(?i)</(p|div|li|h[1-6]|tr)>"), "\n")
        .replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace(Regex("[ \\t]{2,}"), " ")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()

    private companion object {
        const val MAX_BYTES = 512 * 1024
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) OpenClaudeMobile/1.0"
    }
}
