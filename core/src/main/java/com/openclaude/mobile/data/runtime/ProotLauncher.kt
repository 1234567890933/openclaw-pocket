package com.openclaude.mobile.data.runtime

import com.openclaude.mobile.util.Constants
import com.openclaude.mobile.util.Logs
import java.io.File

/**
 * Launches the guest agent without root, without Termux.
 *
 * The trick is proot: a userspace chroot that rewrites syscalls with ptrace, so an
 * unprivileged app can present `/opt/openclaude` as a real filesystem root. We
 * bind-mount the host's rootfs in, point HOME at the guest work dir, and exec
 * node directly. No shell is involved unless the agent asks for one.
 */
class ProotLauncher(private val paths: RuntimePaths) {

    fun baseEnv(extra: Map<String, String> = emptyMap()): Map<String, String> {
        val tmp = File(paths.rootfs, "tmp").apply { mkdirs() }
        val env = linkedMapOf(
            "HOME" to "/root",
            "TERM" to "dumb",
            "LANG" to "C.UTF-8",
            "LC_ALL" to "C.UTF-8",
            "PATH" to "/opt/node/bin:/opt/python/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "TMPDIR" to "/tmp",
            "PROOT_TMP_DIR" to tmp.absolutePath,
            "PROOT_NO_SECCOMP" to "1",   // older kernels; costs a little speed, buys stability
            "NODE_OPTIONS" to "--max-old-space-size=384",
            "NO_COLOR" to "1"
        )
        env.putAll(extra)
        return env
    }

    /** Builds the argv for `proot -r <rootfs> -b /dev -b /proc ... node cli.mjs`. */
    fun buildCommand(guestArgs: List<String>, env: Map<String, String>): List<String> {
        val cmd = mutableListOf(
            paths.prootBinary.absolutePath,
            "-r", paths.rootfs.absolutePath,
            "-b", "/dev",
            "-b", "/proc",
            "-b", "/sys",
            "-b", "${paths.rootfs.absolutePath}/tmp:/tmp",
            "-b", "${paths.rootfs.absolutePath}/root:/root",
            "-b", "${paths.work.absolutePath}:/root/work",
            "-w", "/root/work",
            "-0"                       // fake uid 0 inside the guest
        )
        env["PROOT_TMP_DIR"]?.let { cmd += listOf("-b", "$it:/tmp") }
        cmd += guestArgs
        return cmd
    }

    fun defaultAgentCommand(providerEnv: Map<String, String>): List<String> {
        // The CLI speaks newline-delimited JSON on stdin/stdout in --headless mode.
        return buildCommand(
            listOf(
                paths.guestNode,
                paths.guestCli,
                "--headless",
                "--json-stream",
                "--cwd", paths.guestWork
            ),
            baseEnv(providerEnv)
        )
    }

    /**
     * Sanity check before we try to exec: a missing binary produces a confusing
     * ptrace error, so fail early with something readable.
     */
    fun preflight(): List<String> {
        val problems = mutableListOf<String>()
        if (!paths.prootBinary.exists()) problems += "proot binary missing"
        if (!paths.rootfs.exists()) problems += "rootfs missing"
        if (!File(paths.rootfs, "opt/node/bin/node").exists()) problems += "node missing in guest"
        if (!File(paths.rootfs, "opt/openclaude/dist/cli.mjs").exists()) problems += "openclaude bundle missing"
        if (problems.isNotEmpty()) Logs.w("proot", "preflight: $problems")
        return problems
    }

    /** Guards against a prompt that tries to smuggle shell operators into argv. */
    fun isArgumentSafe(arg: String): Boolean {
        if (arg.contains('\u0000')) return false
        return Constants.SHELL_DENY_TOKENS.none { arg.contains(it, ignoreCase = true) }
    }
}
