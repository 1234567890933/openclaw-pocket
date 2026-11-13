package com.openclaude.mobile.data.provider

import com.openclaude.mobile.data.agent.ProviderProfile
import com.openclaude.mobile.util.Logs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Talks to a model provider directly, from Kotlin, with no gateway and no userland.
 *
 * This exists because the gateway path needs a glibc userland, a Node runtime and a
 * proot sandbox — three things that can each fail for reasons the user cannot fix.
 * A chat app whose basic function depends on all three is a chat app that does not
 * work. So the primary path is now the simplest one that can possibly work: an HTTPS
 * request to the provider the user already has a key for.
 *
 * Supports the two wire formats that cover almost everything:
 *  - OpenAI-compatible `/chat/completions` (OpenRouter, Groq, Ollama, vLLM, GitHub Models)
 *  - Gemini `generateContent`
 *
 * Streaming is Server-Sent Events, parsed line by line. The parser is deliberately
 * forgiving: a malformed frame is skipped, not fatal, because one bad chunk should
 * not kill a 2000-token answer.
 */
class DirectModelClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
) {

    /** One turn of conversation. */
    data class Message(val role: String, val content: String)

    /**
     * Streams a completion. Emits text deltas; throws on a transport error so the
     * caller can show a real message rather than a silent stop.
     */
    fun stream(
        profile: ProviderProfile,
        apiKey: String?,
        system: String,
        messages: List<Message>
    ): Flow<String> = callbackFlow {
        val request = when (profile.kind) {
            ProviderProfile.Kind.GEMINI -> geminiRequest(profile, apiKey, system, messages)
            else -> openAiRequest(profile, apiKey, system, messages)
        }

        val call = http.newCall(request)
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val body = response.body?.string().orEmpty().take(400)
                    close(IllegalStateException("${response.code} from ${profile.label}: $body"))
                    return@callbackFlow
                }
                val source = response.body?.source()
                    ?: run { close(IllegalStateException("empty response")); return@callbackFlow }

                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    val delta = parseFrame(profile.kind, line) ?: continue
                    if (delta == DONE) break
                    trySend(delta)
                }
            }
        } catch (t: Throwable) {
            Logs.e("provider", "stream failed", t)
            close(t)
            return@callbackFlow
        }
        close()
        awaitClose { call.cancel() }
    }.flowOn(Dispatchers.IO)

    // ------------------------------------------------------------- wire formats

    private fun openAiRequest(
        profile: ProviderProfile,
        apiKey: String?,
        system: String,
        messages: List<Message>
    ): Request {
        val body = JSONObject().apply {
            put("model", profile.model)
            put("stream", true)
            put("temperature", profile.temperature)
            put("max_tokens", profile.maxTokens)
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", system))
                messages.forEach { put(JSONObject().put("role", it.role).put("content", it.content)) }
            })
        }

        val builder = Request.Builder()
            .url(profile.baseUrl.trimEnd('/') + "/chat/completions")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON))

        if (!apiKey.isNullOrBlank()) builder.header("Authorization", "Bearer $apiKey")
        return builder.build()
    }

    private fun geminiRequest(
        profile: ProviderProfile,
        apiKey: String?,
        system: String,
        messages: List<Message>
    ): Request {
        val body = JSONObject().apply {
            put("systemInstruction", JSONObject().put("parts", JSONArray().put(
                JSONObject().put("text", system)
            )))
            put("contents", JSONArray().apply {
                messages.forEach { m ->
                    put(JSONObject().apply {
                        put("role", if (m.role == "assistant") "model" else "user")
                        put("parts", JSONArray().put(JSONObject().put("text", m.content)))
                    })
                }
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", profile.temperature)
                put("maxOutputTokens", profile.maxTokens)
            })
        }

        val url = profile.baseUrl.trimEnd('/') +
            "/v1beta/models/${profile.model}:streamGenerateContent?alt=sse&key=${apiKey.orEmpty()}"

        return Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON))
            .build()
    }

    /** Returns the text delta in one SSE frame, [DONE], or null to skip the line. */
    private fun parseFrame(kind: ProviderProfile.Kind, line: String): String? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith(":")) return null
        if (!trimmed.startsWith("data:")) return null
        val payload = trimmed.removePrefix("data:").trim()
        if (payload == "[DONE]") return DONE

        return runCatching {
            val json = JSONObject(payload)
            when (kind) {
                ProviderProfile.Kind.GEMINI -> json
                    .optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text")
                    ?.takeIf { it.isNotEmpty() }

                else -> json
                    .optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("delta")
                    ?.optString("content")
                    ?.takeIf { it.isNotEmpty() }
            }
        }.getOrNull()
    }

    /**
     * Non-streaming probe used by the provider editor and by startup validation.
     * Cheaper than a real turn and tells the user immediately whether a key works.
     */
    suspend fun verify(profile: ProviderProfile, apiKey: String?): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val first = stream(
                    profile = profile,
                    apiKey = apiKey,
                    system = "Reply with the single word OK.",
                    messages = listOf(Message("user", "ping"))
                )
                var received = ""
                first.collect { chunk ->
                    received += chunk
                    if (received.length > 40) return@collect
                }
                if (received.isBlank()) error("no output from ${profile.label}")
                received.trim()
            }
        }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
        const val DONE = "\u0000done"
    }
}
