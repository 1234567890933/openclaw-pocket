package com.openclaude.mobile.data.runtime

import android.content.Context
import com.openclaude.mobile.util.Constants
import java.io.File

/**
 * Single source of truth for every path the runtime touches. Nothing else in the
 * app is allowed to build a path by string concatenation.
 */
class RuntimePaths(private val context: Context) {

    val root: File get() = File(context.filesDir, Constants.DIR_RUNTIME)
    val downloads: File get() = File(root, "downloads")
    val prootDir: File get() = File(root, "root")
    val rootfs: File get() = File(root, "rootfs")
    val work: File get() = File(root, "work")
    val exports: File get() = File(context.filesDir, Constants.DIR_EXPORTS)
    val logs: File get() = File(root, "logs")

    val prootBinary: File get() = File(prootDir, "proot")

    /** Where the agent bundle lands inside the guest filesystem. */
    val guestOpenClaude: String get() = "/opt/openclaude"
    val guestNode: String get() = "/opt/node/bin/node"
    val guestCli: String get() = "/opt/openclaude/dist/cli.mjs"
    val guestWork: String get() = "/root/work"

    fun ensureAll() {
        listOf(root, downloads, prootDir, rootfs, work, exports, logs).forEach { dir ->
            if (!dir.exists() && !dir.mkdirs()) {
                throw IllegalStateException("cannot create ${dir.absolutePath}")
            }
        }
    }

    /** Guest-side path for a host file inside the rootfs. */
    fun toGuest(hostFile: File): String {
        val rel = hostFile.absolutePath.removePrefix(rootfs.absolutePath)
        return rel.ifEmpty { "/" }
    }

    fun isRuntimeInstalled(): Boolean =
        prootBinary.exists() && File(rootfs, "opt/openclaude/dist/cli.mjs").exists()
}
