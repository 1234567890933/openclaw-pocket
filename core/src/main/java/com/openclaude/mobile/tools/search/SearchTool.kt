package com.openclaude.mobile.tools.search

import com.openclaude.mobile.tools.Tool
import com.openclaude.mobile.tools.ToolResult
import com.openclaude.mobile.tools.ToolSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

/**
 * Web search.
 *
 * Two backends: an API key if the user has one, and the plain HTML endpoint if not.
 * The HTML path is parsed with a regex on the result blocks, which is fragile in
 * principle and has survived years in practice — and it means search works on a
 * fresh install with no account, which is the whole point of this app.
 *
 * Results are de-duplicated by host+path and capped, because a model that receives
 * twenty near-identical snippets spends its context on noise.
 */
class SearchTool(
    private val apiKeyProvider: (String) -> String? = { null }
) : Tool {

    override val spec = ToolSpec(
        name = "search",
        title = "Web search",
        description = "Search the web and return titles, URLs and snippets.",
        inputSchema = """
            {"type":"object","properties":{
              "query":{"type":"string"},
              "limit":{"type":"integer","minimum":1,"maximum":10}
            },"required":["query"]}
        """.trimIndent(),
        risk = ToolSpec.Risk.NETWORK,
        timeoutMs = 20_000
    )

    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    override suspend fun run(input: JsonObject): ToolResult = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val query = input["query"]?.jsonPrimitive?.content?.trim()
            ?: return@withContext ToolResult.fail("missing query")
        if (query.length < 2) return@withContext ToolResult.fail("query too short")
        val limit = (input["limit"]?.jsonPrimitive?.content?.toIntOrNull() ?: 5).coerceIn(1, 10)

        val apiKey = apiKeyProvider("search.brave")
        val results = if (!apiKey.isNullOrBlank()) {
            runCatching { brave(query, limit, apiKey) }.getOrElse { htmlSearch(query, limit) }
        } else {
            htmlSearch(query, limit)
        }

        if (results.isEmpty()) {
            return@withContext ToolResult.fail("no results for '$query'")
        }

        val body = buildString {
            appendLine("Results for: $query")
            appendLine()
            results.forEachIndexed { index, hit ->
                appendLine("${index + 1}. ${hit.title}")
                appendLine("   ${hit.url}")
                if (hit.snippet.isNotBlank()) appendLine("   ${hit.snippet}")
                appendLine()
            }
        }
        ToolResult.ok(body.trimEnd(), System.currentTimeMillis() - started)
    }

    private data class Hit(val title: String, val url: String, val snippet: String)

    private fun brave(query: String, limit: Int, key: String): List<Hit> {
        val request = Request.Builder()
            .url("https://api.search.brave.com/res/v1/web/search?q=" + encode(query) + "&count=$limit")
            .header("X-Subscription-Token", key)
            .header("Accept", "application/json")
            .build()

        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("brave ${response.code}")
            val hits = mutableListOf<Hit>()
            // [^"]* rather than (.*?) so the raw string does not end on a quote,
            // which the Kotlin lexer would read as a terminator plus a stray quote.
            Regex("""\{"title":"([^"]*)","url":"([^"]*)".*?"description":"([^"]*)""")
                .findAll(body)
                .take(limit)
                .forEach { match ->
                    hits += Hit(
                        unescape(match.groupValues[1]),
                        unescape(match.groupValues[2]),
                        unescape(match.groupValues[3]).replace(Regex("<.*?>"), "")
                    )
                }
            return hits
        }
    }

    private fun htmlSearch(query: String, limit: Int): List<Hit> {
        val request = Request.Builder()
            .url("https://html.duckduckgo.com/html/?q=" + encode(query))
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) OpenClaudeMobile/1.0")
            .build()

        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("search ${response.code}")

            val hits = mutableListOf<Hit>()
            val blocks = body.split("class=\"result__body\"")
            for (block in blocks.drop(1)) {
                val link = Regex("""href="([^"]+)"[^>]*class="result__a"[^>]*>(.*?)</a>""")
                    .find(block) ?: continue
                val rawUrl = decodeRedirect(link.groupValues[1])
                val title = link.groupValues[2].replace(Regex("<.*?>"), "").trim()
                val snippet = Regex("""class="result__snippet"[^>]*>(.*?)</a>""")
                    .find(block)?.groupValues?.get(1)
                    ?.replace(Regex("<.*?>"), "")
                    ?.trim()
                    .orEmpty()

                if (title.isNotBlank() && rawUrl.startsWith("http")) {
                    hits += Hit(title, rawUrl, snippet)
                }
                if (hits.size >= limit) break
            }

            // De-duplicate on host + path so mirrors and anchors collapse.
            return hits.distinctBy { hit ->
                val stripped = hit.url.substringAfter("://").substringBefore('#')
                stripped.substringBefore('?')
            }.take(limit)
        }
    }

    private fun decodeRedirect(url: String): String {
        if (url.contains("uddg=")) {
            val encoded = url.substringAfter("uddg=").substringBefore('&')
            return runCatching { URLDecoder.decode(encoded, "UTF-8") }.getOrDefault(url)
        }
        return if (url.startsWith("//")) "https:$url" else url
    }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8")

    private fun unescape(value: String): String = value
        .replace("\\u003c", "<")
        .replace("\\u003e", ">")
        .replace("\\/", "/")
        .replace("\\\"", "\"")
}
