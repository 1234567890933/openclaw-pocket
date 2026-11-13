package com.openclaude.mobile.perf

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import com.openclaude.mobile.util.Constants

/**
 * Checks the hardware, and is honest about which checks are hard limits.
 *
 * A hard limit is something the app cannot work around: a non-arm64 CPU, or so little
 * memory that the gateway is killed during startup. Everything else is a
 * recommendation, because a phone that reports 3.5 GB of usable RAM can still run the
 * agent — it just runs a lite profile.
 *
 * The first version of this gate treated the advertised numbers as hard limits and
 * locked out the very devices it was written for. That was a bug, not caution.
 */
class DeviceGate(private val context: Context) {

    data class Report(
        val ramBytes: Long,
        val storageBytes: Long,
        val abi: String,
        val sdk: Int,
        val ramOk: Boolean,
        val storageOk: Boolean,
        val abiOk: Boolean,
        val ramCritical: Boolean,
        val storageCritical: Boolean
    ) {
        /** Only these stop the app outright. */
        val blocked: Boolean get() = !abiOk || ramCritical || storageCritical

        /** Runs, but should start in lite mode. */
        val degraded: Boolean get() = !ramOk || !storageOk
    }

    fun check(): Report {
        val am = context.getSystemService(ActivityManager::class.java)
        val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val stat = StatFs(context.filesDir.absolutePath)
        val free = stat.availableBytes
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"

        return Report(
            ramBytes = info.totalMem,
            storageBytes = free,
            abi = abi,
            sdk = Build.VERSION.SDK_INT,
            ramOk = info.totalMem >= Constants.MIN_RAM_BYTES,
            storageOk = free >= Constants.MIN_STORAGE_BYTES,
            abiOk = abi.contains("arm64"),
            ramCritical = info.totalMem < Constants.HARD_RAM_FLOOR,
            storageCritical = free < Constants.HARD_STORAGE_FLOOR
        )
    }

    fun describe(report: Report): String = buildString {
        append("RAM %.1f GB · free %.0f GB · %s · API %d".format(
            report.ramBytes / 1_073_741_824.0,
            report.storageBytes / 1_073_741_824.0,
            report.abi,
            report.sdk
        ))
    }
}
