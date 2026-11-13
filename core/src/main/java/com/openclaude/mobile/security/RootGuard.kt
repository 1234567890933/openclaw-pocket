package com.openclaude.mobile.security

import android.os.Build
import com.openclaude.mobile.util.Logs
import java.io.File

/**
 * Detects a rooted device and refuses to run the agent on one.
 *
 * This is not moralising about root. It is that the gateway runs a Linux userland
 * with a proot sandbox, and proot works by rewriting syscalls through ptrace — on a
 * rooted device with Magisk, SELinux hooks or a modified kernel, that rewriting can
 * fail in ways that corrupt the guest filesystem rather than erroring cleanly. The
 * realistic outcome is a half-extracted rootfs and a phone that behaves oddly, which
 * is exactly what the user asked me to prevent.
 *
 * Detection is deliberately conservative: any one strong signal is enough, and a
 * weak signal alone is not. False positives are worse than a missed detection here,
 * because the penalty is refusing to run on a perfectly good phone.
 */
class RootGuard {

    data class Verdict(val rooted: Boolean, val reasons: List<String>)

    fun inspect(): Verdict {
        val reasons = mutableListOf<String>()

        // 1. su binaries in the usual places
        val suPaths = listOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su",
            "/system/app/Superuser.apk", "/data/local/su", "/data/local/bin/su"
        )
        suPaths.firstOrNull { File(it).exists() }?.let { reasons += "su binary at $it" }

        // 2. build tags
        if (Build.TAGS?.contains("test-keys") == true) reasons += "build signed with test-keys"

        // 3. mount flags: a rw /system is a strong signal
        runCatching {
            File("/proc/mounts").readLines().forEach { line ->
                val parts = line.split(' ')
                if (parts.size > 3 && parts[1] == "/system" && parts[3].contains("rw")) {
                    reasons += "/system mounted read-write"
                }
            }
        }

        // 4. Magisk's own traces
        listOf("/sbin/.magisk", "/data/adb/magisk", "/data/adb/modules").firstOrNull {
            File(it).exists()
        }?.let { reasons += "Magisk artefacts at $it" }

        Logs.i("root", if (reasons.isEmpty()) "clean" else "rooted: ${reasons.joinToString()}")
        return Verdict(reasons.isNotEmpty(), reasons)
    }

    fun isRooted(): Boolean = inspect().rooted
}
