package com.openclaude.mobile.data.local

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.openclaude.mobile.util.Logs

/**
 * What this phone can actually run.
 *
 * The single most common support question for on-device LLMs is "why is it
 * slow" and the answer is always one of three things: not enough RAM, too few
 * cores, or thermal throttling. This class measures all three so the app can pick
 * a quantisation and a thread count instead of guessing.
 */
class DeviceProfile(private val context: Context) {

    data class Snapshot(
        val totalRamBytes: Long,
        val availableRamBytes: Long,
        val cores: Int,
        val bigCores: Int,
        val abi: String,
        val sdk: Int,
        val isLowRamDevice: Boolean,
        val thermalStatus: Int
    ) {
        /** Model budget: never claim more than a third of physical RAM. */
        val modelBudgetBytes: Long get() = totalRamBytes / 3
    }

    val snapshot: Snapshot by lazy { measure() }

    private fun measure(): Snapshot {
        val am = context.getSystemService(ActivityManager::class.java)
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)

        val cores = Runtime.getRuntime().availableProcessors()
        // Every modern arm64 SoC is heterogeneous; the useful number for inference
        // is the count of performance cores, approximated as half the total.
        val bigCores = (cores / 2).coerceAtLeast(1)

        return Snapshot(
            totalRamBytes = info.totalMem,
            availableRamBytes = info.availMem,
            cores = cores,
            bigCores = bigCores,
            abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
            sdk = Build.VERSION.SDK_INT,
            isLowRamDevice = am.isLowRamDevice,
            thermalStatus = thermalStatus()
        )
    }

    fun thermalStatus(): Int = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val pm = context.getSystemService(PowerManager::class.java)
            pm.currentThermalStatus
        } else {
            0
        }
    }.getOrDefault(0)

    /** True when the device can hold a model of this size with room to run. */
    fun canRun(modelBytes: Long): Boolean {
        val snap = snapshot
        if (snap.isLowRamDevice) return false
        if (snap.thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE) return false
        return modelBytes <= snap.modelBudgetBytes
    }

    /** Thread count that leaves one core for the UI. */
    fun recommendedThreads(): Int {
        val snap = snapshot
        val byCore = (snap.bigCores).coerceIn(2, 6)
        return if (snap.thermalStatus >= PowerManager.THERMAL_STATUS_MODERATE) {
            (byCore / 2).coerceAtLeast(1)
        } else {
            byCore
        }
    }

    fun recommendedContext(): Int = when {
        snapshot.totalRamBytes >= 10L * 1024 * 1024 * 1024 -> 8192
        snapshot.totalRamBytes >= 6L * 1024 * 1024 * 1024 -> 4096
        else -> 2048
    }

    fun describe(): String = with(snapshot) {
        "%.1f GB ram, %d cores (%d perf), %s, api %d, thermal %d".format(
            totalRamBytes / 1_073_741_824.0, cores, bigCores, abi, sdk, thermalStatus
        )
    }

    fun log() = Logs.i("device", describe())
}
