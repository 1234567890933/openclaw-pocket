package com.openclaude.mobile.perf

import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.openclaude.mobile.util.Logs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Heat control.
 *
 * A phone does not fail when it gets hot; it gets *slow*, and the user reads that as
 * "this app is broken". So the app watches the thermal status Android already
 * publishes and steps down before the governor does it for us:
 *
 *  - NONE / LIGHT: full speed
 *  - MODERATE:    half the inference threads, no shimmer, longer animation budget
 *  - SEVERE:      one thread, background work paused, banner shown
 *  - CRITICAL:    everything paused, model unloaded
 *
 * Polling every 20 s is deliberate. A listener would be cheaper, but the thermal
 * callback is only reliable from API 29 and polling costs nothing measurable.
 */
class ThermalGovernor(private val context: Context) {

    enum class Level { NONE, LIGHT, MODERATE, SEVERE, CRITICAL }

    data class State(
        val level: Level = Level.NONE,
        val rawStatus: Int = 0,
        val throttled: Boolean = false
    ) {
        val message: String?
            get() = when (level) {
                Level.MODERATE -> "Phone is warm — running at reduced speed"
                Level.SEVERE -> "Phone is hot — background work paused"
                Level.CRITICAL -> "Phone is too hot — cooling down"
                else -> null
            }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var poller: Job? = null

    fun start() {
        if (poller?.isActive == true) return
        poller = scope.launch {
            while (isActive) {
                sample()
                delay(POLL_MS)
            }
        }
    }

    fun stop() {
        poller?.cancel()
        poller = null
    }

    fun sample(): State {
        val raw = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.getSystemService(PowerManager::class.java).currentThermalStatus
            } else 0
        }.getOrDefault(0)

        val level = when (raw) {
            0 -> Level.NONE
            1 -> Level.LIGHT
            2 -> Level.MODERATE
            3 -> Level.SEVERE
            else -> Level.CRITICAL
        }

        val next = State(level = level, rawStatus = raw, throttled = raw >= 2)
        if (next.level != _state.value.level) {
            Logs.i("thermal", "level -> $level (raw $raw)")
        }
        _state.value = next
        return next
    }

    /** Thread budget multiplier the inference path should apply. */
    fun threadFactor(): Float = when (_state.value.level) {
        Level.NONE, Level.LIGHT -> 1f
        Level.MODERATE -> 0.5f
        Level.SEVERE, Level.CRITICAL -> 0.34f
    }

    fun shouldPauseBackground(): Boolean = _state.value.level >= Level.SEVERE

    fun shouldUnloadModel(): Boolean = _state.value.level == Level.CRITICAL

    private companion object {
        const val POLL_MS = 20_000L
    }
}
