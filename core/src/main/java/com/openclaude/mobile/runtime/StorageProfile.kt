package com.openclaude.mobile.runtime

import com.openclaude.mobile.perf.DeviceGate

/**
 * How much of the runtime to install.
 *
 * The full bundle is ~612 MB, and on a phone that is already full that is the
 * difference between installing and not. Three profiles, chosen from free space and
 * switchable afterwards:
 *
 *  - LITE   (~250 MB) — proot, Debian, Node, the agent. No offline toolset, no model
 *                       index. The agent works; the extras download on demand.
 *  - STANDARD (~430 MB) — adds the Python runtime and the tool assets.
 *  - FULL   (~612 MB) — everything, including the model index.
 *
 * The point is that "OpenClaw needs 600 MB" is not a fixed cost. Most of it is
 * optional and most people never touch it.
 */
enum class StorageProfile(val label: String, val approxBytes: Long) {
    LITE("Lite", 250L * 1024 * 1024),
    STANDARD("Standard", 430L * 1024 * 1024),
    FULL("Full", 612L * 1024 * 1024);

    companion object {
        /** Picks a profile that leaves at least 3 GB free afterwards. */
        fun recommend(report: DeviceGate.Report): StorageProfile = when {
            report.storageBytes > 12_000_000_000L -> FULL
            report.storageBytes > 7_000_000_000L -> STANDARD
            else -> LITE
        }

        fun fromName(name: String?): StorageProfile =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: LITE
    }
}
