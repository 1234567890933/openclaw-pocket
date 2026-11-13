package com.openclaude.mobile.data.local

import com.openclaude.mobile.util.Logs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * On-device generation.
 *
 * One model loaded at a time, guarded by a mutex: a phone cannot hold two 2 GB
 * contexts, and swapping models mid-conversation costs more than the turn is
 * worth. The handle is kept alive between turns so the second question is fast.
 */
class LocalEngine(
    private val models: LocalModelManager,
    private val device: DeviceProfile
) {

    data class Stats(
        val promptTokens: Int,
        val outputTokens: Int,
        val tokensPerSecond: Double,
        val cancelled: Boolean
    )

    private var handle: Long = 0L
    private var loadedId: String? = null
    private val lock = Mutex()

    val isCompiled: Boolean get() = LocalNative.available()

    fun describe(): String =
        if (!isCompiled) "local engine not compiled in"
        else if (handle == 0L) "no model loaded"
        else LocalNative.describe(handle)

    /** Loads the active model if it is not already resident. */
    suspend fun ensureLoaded(): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isCompiled) return@withContext Result.failure(
            IllegalStateException("this build has no on-device engine")
        )

        val record = models.installedModel()
            ?: return@withContext Result.failure(IllegalStateException("no model installed"))

        if (handle != 0L && loadedId == record.id) return@withContext Result.success(Unit)

        if (handle != 0L) {
            LocalNative.unload(handle)
            handle = 0L
            loadedId = null
        }

        val file = File(models.modelsDir(), record.file)
        if (!file.exists()) return@withContext Result.failure(
            IllegalStateException("model file missing: ${record.file}")
        )

        val threads = device.recommendedThreads()
        val ctx = device.recommendedContext()
        Logs.i("local", "loading ${record.id} threads=$threads ctx=$ctx")

        val started = System.currentTimeMillis()
        val ptr = LocalNative.load(file.absolutePath, threads, ctx)
        if (ptr == 0L) return@withContext Result.failure(
            IllegalStateException("engine refused ${record.id}")
        )

        handle = ptr
        loadedId = record.id
        Logs.i("local", "loaded in ${System.currentTimeMillis() - started} ms")
        Result.success(Unit)
    }

    /**
     * Streams a completion. [onToken] is called from the generating thread, so
     * callers must be prepared for it off the main thread.
     */
    suspend fun generate(
        prompt: String,
        maxTokens: Int = 512,
        temperature: Float = 0.7f,
        topK: Int = 40,
        topP: Float = 0.95f,
        onToken: (String) -> Unit
    ): Result<Stats> = withContext(Dispatchers.IO) {
        lock.withLock {
            ensureLoaded().getOrElse { return@withLock Result.failure(it) }
            val ptr = handle
            if (ptr == 0L) return@withLock Result.failure(IllegalStateException("no handle"))

            val started = System.nanoTime()
            val emitted = intArrayOf(0)
            val sink = TokenSink { piece ->
                emitted[0] += 1
                onToken(piece)
            }

            val produced = LocalNative.generate(
                ptr, prompt, temperature, topK, topP, maxTokens, sink
            )

            val seconds = (System.nanoTime() - started) / 1_000_000_000.0
            if (produced < 0) {
                val reason = when (produced) {
                    -1 -> "engine unavailable or handle closed"
                    -2 -> "prompt produced no tokens"
                    -3 -> "token sink method missing"
                    -4 -> "context overflow"
                    else -> "generation failed ($produced)"
                }
                Logs.e("local", reason)
                return@withLock Result.failure(IllegalStateException(reason))
            }

            val stats = Stats(
                promptTokens = 0,
                outputTokens = produced,
                tokensPerSecond = if (seconds > 0) produced / seconds else 0.0,
                cancelled = produced < maxTokens
            )
            Logs.i("local", "%.1f tok/s, %d tokens".format(stats.tokensPerSecond, produced))
            Result.success(stats)
        }
    }

    fun cancel() {
        if (handle != 0L) LocalNative.cancel(handle)
    }

    fun release() {
        if (handle != 0L) {
            LocalNative.unload(handle)
            handle = 0L
            loadedId = null
        }
    }

    /**
     * Wraps a prompt in the model's chat format.
     *
     * ChatML is the widest default; models that use something else still produce
     * readable output, which beats failing closed on an unknown template.
     */
    fun buildPrompt(system: String, turns: List<Pair<String, String>>): String {
        val sb = StringBuilder()
        if (system.isNotBlank()) sb.append("<|im_start|>system\n").append(system).append("<|im_end|>\n")
        turns.forEach { (role, text) ->
            sb.append("<|im_start|>").append(role).append('\n').append(text).append("<|im_end|>\n")
        }
        sb.append("<|im_start|>assistant\n")
        return sb.toString()
    }
}
