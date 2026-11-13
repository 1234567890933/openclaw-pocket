package com.openclaude.mobile.data.runtime

import com.openclaude.mobile.core.LineFramer
import com.openclaude.mobile.util.Constants
import com.openclaude.mobile.util.Logs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStreamWriter
import java.util.concurrent.atomic.AtomicLong

/**
 * Owns one live agent process.
 *
 * stdout is newline-delimited JSON. Framing happens on bytes via [LineFramer]
 * rather than a BufferedReader, so a chunk boundary landing inside a multi-byte
 * character cannot corrupt a line.
 *
 * Two watchdogs, because they catch different bugs:
 *  - a stderr drain thread, so a chatty child cannot fill the 64 KB pipe buffer and
 *    block forever (this is the classic "agent hangs at 30%" bug)
 *  - an idle timer, so a process that produces nothing for ten minutes is killed
 *    instead of holding the foreground service open all night
 */
class SessionRunner(
    private val command: List<String>,
    private val env: Map<String, String>,
    private val workingDir: File,
    private val idleTimeoutMs: Long = Constants.TURN_TIMEOUT_MS,
    private val stderrSink: ((String) -> Unit)? = null
) {

    private var process: Process? = null
    private val lastActivity = AtomicLong(System.currentTimeMillis())

    fun lines(): Flow<String> = callbackFlow {
        val pb = ProcessBuilder(command)
            .directory(workingDir)
            .redirectErrorStream(false)
        pb.environment().clear()
        pb.environment().putAll(env)

        val proc = try {
            pb.start()
        } catch (t: Throwable) {
            Logs.e("proc", "spawn failed", t)
            close(t)
            return@callbackFlow
        }
        process = proc
        lastActivity.set(System.currentTimeMillis())
        Logs.i("proc", "process ${System.identityHashCode(proc)} started")

        val errThread = Thread {
            runCatching {
                proc.errorStream.bufferedReader().useLines { seq ->
                    seq.forEach { line ->
                        if (line.isNotBlank()) stderrSink?.invoke(line)
                    }
                }
            }
        }.apply { isDaemon = true; name = "agent-stderr"; start() }

        val readerJob = launch(Dispatchers.IO) {
            val framer = LineFramer(Constants.IO_BUFFER_BYTES * 4)
            val chunk = ByteArray(Constants.IO_BUFFER_BYTES)
            try {
                while (isActive) {
                    val read = proc.inputStream.read(chunk)
                    if (read < 0) break
                    lastActivity.set(System.currentTimeMillis())
                    framer.append(chunk, 0, read)
                    while (true) {
                        val line = framer.nextLine() ?: break
                        if (line.isNotBlank()) trySend(line)
                    }
                    if (framer.overflowed()) {
                        Logs.w("proc", "dropped an oversized line from the agent")
                        framer.clearOverflow()
                    }
                }
            } catch (t: Throwable) {
                Logs.w("proc", "read loop ended: ${t.message}")
            }
        }

        val watchdog = launch {
            while (isActive) {
                delay(15_000)
                val idle = System.currentTimeMillis() - lastActivity.get()
                if (idle > idleTimeoutMs) {
                    Logs.w("proc", "no output for ${idle}ms, killing the process")
                    stop()
                    break
                }
            }
        }

        awaitClose {
            readerJob.cancel()
            watchdog.cancel()
            errThread.interrupt()
            stop()
        }
    }.flowOn(Dispatchers.IO)

    suspend fun send(jsonLine: String) = withContext(Dispatchers.IO) {
        val proc = process ?: throw IllegalStateException("agent not running")
        runCatching {
            OutputStreamWriter(proc.outputStream).apply {
                write(jsonLine)
                write("\n")
                flush()
            }
            lastActivity.set(System.currentTimeMillis())
        }.onFailure {
            Logs.e("proc", "write failed", it)
            throw it
        }
    }

    fun stop() {
        val proc = process ?: return
        Logs.i("proc", "stopping process ${System.identityHashCode(proc)}")
        runCatching { proc.outputStream.close() }
        proc.destroy()
        if (proc.isAlive) proc.destroyForcibly()
        process = null
    }

    fun isAlive(): Boolean = process?.isAlive == true

    /** Exposed for the protocol log in developer settings. */
    fun uptimeMs(): Long = if (process == null) 0 else System.currentTimeMillis() - lastActivity.get()
}
