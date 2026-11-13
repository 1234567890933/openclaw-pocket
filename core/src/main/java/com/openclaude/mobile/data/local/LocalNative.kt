package com.openclaude.mobile.data.local

import androidx.annotation.Keep

/**
 * Raw JNI surface for the on-device engine.
 *
 * One handle, five calls. The object is intentionally dumb: every policy decision
 * (which model, how many threads, when to unload) lives in [LocalEngine], because
 * anything that needs a JNIEnv must stay trivially small.
 */
internal object LocalNative {

    init {
        System.loadLibrary("ocmcore")
    }

    @JvmStatic external fun available(): Boolean
    @JvmStatic external fun backendInfo(): String
    @JvmStatic external fun load(path: String, threads: Int, contextSize: Int): Long
    @JvmStatic external fun unload(handle: Long)
    @JvmStatic external fun generate(
        handle: Long,
        prompt: String,
        temperature: Float,
        topK: Int,
        topP: Float,
        maxTokens: Int,
        sink: TokenSink
    ): Int
    @JvmStatic external fun cancel(handle: Long)
    @JvmStatic external fun describe(handle: Long): String
}

/**
 * The callback the native loop calls once per token.
 *
 * Must stay `@Keep`-ed: R8 would rename `emit` and the JNI lookup would fail at
 * runtime, in release builds only.
 */
@Keep
class TokenSink(private val onPiece: (String) -> Unit) {
    @Keep
    fun emit(piece: String) {
        onPiece(piece)
    }
}
