package com.openclaude.mobile.util

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Thin logging facade. Debug builds log verbosely; release builds keep only
 * warnings and errors so provider keys cannot leak into logcat.
 */
object Logs {

    private const val TAG = "OCM"
    private val stamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val ring = ArrayDeque<String>(512)

    var verbose: Boolean = false

    fun d(scope: String, msg: String) {
        if (verbose) Log.d(TAG, "[$scope] $msg")
        push("D/$scope $msg")
    }

    fun i(scope: String, msg: String) {
        Log.i(TAG, "[$scope] $msg")
        push("I/$scope $msg")
    }

    fun w(scope: String, msg: String) {
        Log.w(TAG, "[$scope] $msg")
        push("W/$scope $msg")
    }

    fun e(scope: String, msg: String, t: Throwable? = null) {
        Log.e(TAG, "[$scope] $msg", t)
        push("E/$scope $msg${t?.let { " :: ${it.javaClass.simpleName}: ${it.message}" } ?: ""}")
    }

    private fun push(line: String) {
        synchronized(ring) {
            ring.addLast("${stamp.format(Date())} $line")
            while (ring.size > 500) ring.removeFirst()
        }
    }

    fun snapshot(): List<String> = synchronized(ring) { ring.toList() }

    fun clear() = synchronized(ring) { ring.clear() }

    /** Redacts anything that looks like a credential before it is displayed. */
    fun redact(text: String): String {
        var out = text
        Regex("(sk-[A-Za-z0-9_\\-]{8})[A-Za-z0-9_\\-]+").findAll(out).forEach {
            out = out.replace(it.value, it.groupValues[1] + "…redacted")
        }
        Regex("(AIza[0-9A-Za-z_\\-]{6})[0-9A-Za-z_\\-]+").findAll(out).forEach {
            out = out.replace(it.value, it.groupValues[1] + "…redacted")
        }
        Regex("(Bearer\\s+)[A-Za-z0-9._\\-]+").findAll(out).forEach {
            out = out.replace(it.value, it.groupValues[1] + "…redacted")
        }
        return out
    }
}
