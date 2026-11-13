package com.openclaude.mobile.perf

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.openclaude.mobile.util.Logs

/**
 * How much phone this is, and what to turn down because of it.
 *
 * The target is not a flagship. It is a 4 GB Android 12 device on a low-end
 * Dimensity or a Snapdragon 4-series, where a single dropped frame is visible and a
 * warm phone is felt in the hand. Every tunable in the app reads from here instead
 * of guessing:
 *
 *   - inference threads and context size
 *   - whether glass is drawn at all, and at what cost
 *   - animation duration and whether shimmer runs
 *   - how many bots may run at once
 *
 * Tiers are computed once at startup from RAM and core count. Thermal state is
 * re-read on demand, because a phone that was fine at launch is not fine after ten
 * minutes of generation.
 */
class PerformanceProfile(context: Context) {

    enum class Tier {
        /** <= 4 GB, 8 or fewer cores. Glass off, motion short, 2 threads. */
        LOW,

        /** 6-8 GB. Glass on, motion normal, 4 threads. */
        MID,

        /** 12 GB+. Everything on. */
        HIGH
    }

    private val appContext = context.applicationContext
    private val am = appContext.getSystemService(ActivityManager::class.java)

    val tier: Tier by lazy { classify() }
    val isLowEnd: Boolean get() = tier == Tier.LOW

    private fun classify(): Tier {
        val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val gb = info.totalMem / 1_073_741_824.0
        val cores = Runtime.getRuntime().availableProcessors()

        val tier = when {
            am.isLowRamDevice -> Tier.LOW
            gb >= 11.0 && cores >= 8 -> Tier.HIGH
            gb >= 5.5 && cores >= 6 -> Tier.MID
            gb >= 5.5 -> Tier.MID
            else -> Tier.LOW
        }
        Logs.i("perf", "tier=$tier  %.1fGB  %d cores  api %d".format(gb, cores, Build.VERSION.SDK_INT))
        return tier
    }

    /** Threads for on-device inference. Never more than cores - 1. */
    fun inferenceThreads(): Int {
        val cores = Runtime.getRuntime().availableProcessors()
        val cap = when (tier) {
            Tier.LOW -> 2
            Tier.MID -> 4
            Tier.HIGH -> 6
        }
        val thermal = thermalStatus()
        val halved = if (thermal >= PowerManager.THERMAL_STATUS_MODERATE) cap / 2 else cap
        return halved.coerceIn(1, (cores - 1).coerceAtLeast(1))
    }

    /** Context window for local inference; each doubling costs RAM linearly. */
    fun inferenceContext(): Int = when (tier) {
        Tier.LOW -> 2048
        Tier.MID -> 4096
        Tier.HIGH -> 8192
    }

    /** Bots that may run concurrently. On a low-end phone, one. */
    fun parallelBots(): Int = when (tier) {
        Tier.LOW -> 1
        Tier.MID -> 2
        Tier.HIGH -> 3
    }

    /** Glass is the most expensive thing the UI draws; low tier gets flat panels. */
    fun glassEnabled(): Boolean = tier != Tier.LOW

    /** Multiplier applied to every animation duration. */
    fun motionScale(): Float = when (tier) {
        Tier.LOW -> 0.6f
        Tier.MID -> 1.0f
        Tier.HIGH -> 1.0f
    }

    /** Whether the shimmer skeleton is worth its per-frame gradient. */
    fun shimmerEnabled(): Boolean = tier != Tier.LOW

    fun thermalStatus(): Int = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appContext.getSystemService(PowerManager::class.java).currentThermalStatus
        } else 0
    }.getOrDefault(0)

    /** Called by the UI before heavy work; true means "back off now". */
    fun isThrottling(): Boolean =
        thermalStatus() >= PowerManager.THERMAL_STATUS_SEVERE

    fun describe(): String = with(Runtime.getRuntime()) {
        "tier=$tier cores=${availableProcessors()} maxMem=${maxMemory() / 1_048_576}MB " +
            "threads=${inferenceThreads()} ctx=${inferenceContext()} glass=${glassEnabled()}"
    }
}
