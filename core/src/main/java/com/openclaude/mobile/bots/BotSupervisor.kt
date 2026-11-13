package com.openclaude.mobile.bots

import com.openclaude.mobile.util.Logs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Runs bots, but not all at once.
 *
 * Two at a time is the cap, and it is a thermal decision rather than a taste one: a
 * phone running three concurrent agent loops hits the thermal governor within a
 * minute and then every bot slows down together, which is worse than running two and
 * queueing the third.
 *
 * Every step is bounded by a timeout and a cancellation token. An agent loop with no
 * ceiling is a battery incident.
 */
class BotSupervisor(private val maxParallel: Int = 2) {

    sealed interface Progress {
        data class Started(val botId: String, val task: String) : Progress
        data class Finished(val botId: String, val output: String, val ms: Long) : Progress
        data class Failed(val botId: String, val reason: String) : Progress
        data class Queued(val botId: String) : Progress
    }

    private val scope = CoroutineScope(SupervisorJob())
    private val _progress = MutableSharedFlow<Progress>(extraBufferCapacity = 128)
    val progress: SharedFlow<Progress> = _progress.asSharedFlow()

    private var active: Job? = null

    /**
     * Runs one task per bot, respecting the parallelism cap, and returns the outputs
     * in the same order as the input.
     */
    suspend fun runAll(
        assignments: List<Pair<Bot, String>>,
        step: suspend (Bot, String) -> String
    ): List<Result<String>> = coroutineScope {
        val gate = Semaphore(maxParallel)

        assignments.map { (bot, task) ->
            async {
                gate.withPermit {
                    _progress.emit(Progress.Started(bot.id, task))
                    val started = System.currentTimeMillis()
                    val result = withTimeoutOrNull(TIMEOUT_MS) {
                        runCatching { step(bot, task) }
                    } ?: Result.failure(IllegalStateException("timed out"))

                    val elapsed = System.currentTimeMillis() - started
                    result
                        .onSuccess { _progress.emit(Progress.Finished(bot.id, it, elapsed)) }
                        .onFailure {
                            Logs.w("bots", "${bot.id} failed: ${it.message}")
                            _progress.emit(Progress.Failed(bot.id, it.message ?: "failed"))
                        }
                }
            }
        }.awaitAll()
    }

    fun cancel() {
        active?.cancel()
        active = null
    }

    private companion object {
        const val TIMEOUT_MS = 4 * 60 * 1000L
    }
}
