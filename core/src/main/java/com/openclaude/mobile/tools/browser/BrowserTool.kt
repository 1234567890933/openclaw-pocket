package com.openclaude.mobile.tools.browser

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import com.openclaude.mobile.tools.Tool
import com.openclaude.mobile.tools.ToolResult
import com.openclaude.mobile.tools.ToolSpec
import com.openclaude.mobile.util.Logs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A real browser, for pages that need JavaScript.
 *
 * This is the honest reason to have a WebView at all: a plain HTTP fetch gets you a
 * shell of an app-rendered page, and half the web a modern assistant is asked about
 * is exactly that. The WebView is created per call, used, and destroyed — a retained
 * WebView is a memory leak with a JavaScript engine attached.
 *
 * It runs on the main thread because WebView insists, which is why the whole call is
 * wrapped in `Dispatchers.Main` and bounded by a timeout rather than left to hang.
 */
class BrowserTool(private val context: Context?) : Tool {

    override val spec = ToolSpec(
        name = "browser",
        title = "Open a web page",
        description = "Load a URL in a real browser, including JavaScript, and return the visible text.",
        inputSchema = """
            {"type":"object","properties":{
              "url":{"type":"string"},
              "wait_ms":{"type":"integer","minimum":500,"maximum":15000,"default":2500}
            },"required":["url"]}
        """.trimIndent(),
        risk = ToolSpec.Risk.NETWORK,
        timeoutMs = 30_000
    )

    override suspend fun run(input: JsonObject): ToolResult {
        val started = System.currentTimeMillis()
        val ctx = context ?: return ToolResult.fail("browser unavailable: no context")
        val url = input["url"]?.jsonPrimitive?.content?.trim()
            ?: return ToolResult.fail("missing url")
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return ToolResult.fail("only http and https are allowed")
        }
        val waitMs = (input["wait_ms"]?.jsonPrimitive?.content?.toIntOrNull() ?: 2500)
            .coerceIn(500, 15_000)

        return withContext(Dispatchers.Main) {
            val finished = CompletableDeferred<Pair<String, String>>()

            @SuppressLint("SetJavaScriptEnabled")
            val web = WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadsImagesAutomatically = false
                settings.blockNetworkImage = true
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, loadedUrl: String) {
                        view.evaluateJavascript(
                            "(function(){try{return JSON.stringify({t:document.title," +
                                "x:(document.body?document.body.innerText:'')});}" +
                                "catch(e){return JSON.stringify({t:'',x:''});}})()"
                        ) { raw ->
                            if (!finished.isCompleted) finished.complete(decode(raw))
                        }
                    }
                }
            }

            try {
                web.loadUrl(url)
                val result = withTimeoutOrNull(waitMs.toLong() + 8_000) { finished.await() }
                    ?: run {
                        web.stopLoading()
                        return@withContext ToolResult.fail(
                            "page did not finish loading",
                            System.currentTimeMillis() - started
                        )
                    }

                val (title, text) = result
                if (text.isBlank()) {
                    return@withContext ToolResult.fail(
                        "page rendered no readable text",
                        System.currentTimeMillis() - started
                    )
                }
                ToolResult.ok("$title\n$url\n\n$text", System.currentTimeMillis() - started)
            } catch (t: Throwable) {
                Logs.e("browser", "load failed", t)
                ToolResult.fail(t.message ?: "browser failed", System.currentTimeMillis() - started)
            } finally {
                runCatching {
                    web.stopLoading()
                    web.loadUrl("about:blank")
                    web.destroy()
                }
            }
        }
    }

    /** evaluateJavascript hands back a JSON-encoded string, so it is decoded twice. */
    private fun decode(raw: String?): Pair<String, String> {
        if (raw.isNullOrBlank() || raw == "null") return "" to ""
        val unquoted = raw.removeSurrounding("\"")
            .replace("\\\"", "\"")
            .replace("\\n", "\n")
            .replace("\\\\", "\\")
        return runCatching {
            val element = kotlinx.serialization.json.Json.parseToJsonElement(unquoted)
            val obj = element as kotlinx.serialization.json.JsonObject
            val title = obj["t"]?.jsonPrimitive?.content.orEmpty()
            val text = obj["x"]?.jsonPrimitive?.content.orEmpty()
            title to text.take(20_000)
        }.getOrDefault("" to unquoted.take(20_000))
    }
}
