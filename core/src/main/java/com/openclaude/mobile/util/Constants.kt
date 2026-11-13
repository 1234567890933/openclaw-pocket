package com.openclaude.mobile.util

object Constants {

    /** Maximum number of chats retained on device. Older ones are pruned. */
    const val MAX_SESSIONS = 4

    /** Hard cap per session so the database cannot grow without bound. */
    const val MAX_MESSAGES_PER_SESSION = 400

    /** How long a single agent turn may run before the supervisor kills it. */
    const val TURN_TIMEOUT_MS = 10 * 60 * 1000L

    /** Streaming flush interval for partial assistant text. */
    const val STREAM_FLUSH_MS = 40L

    /** Size of the read buffer used when pumping the agent's stdout. */
    const val IO_BUFFER_BYTES = 8192

    /** Loopback port the gateway uses to reach the app's tool bridge. */
    const val TOOL_BRIDGE_PORT = 18790

    /** Fallback port for the in-app gateway when the random pick fails. */
    const val GATEWAY_DEFAULT_PORT = 18789

    /** How long a tool call may run before the bridge gives up on it. */
    const val TOOL_TIMEOUT_MS = 65_000L

    /** Runtime layout, relative to filesDir. */
    const val DIR_RUNTIME = "runtime"
    const val DIR_DOWNLOADS = "runtime/downloads"
    const val DIR_ROOT = "runtime/root"
    const val DIR_ROOTFS = "runtime/rootfs"
    const val DIR_WORK = "runtime/work"
    const val DIR_EXPORTS = "exports"

    /** Shared-preference / DataStore names. */
    const val PREFS_SECURE = "ocm_secure"
    const val PREFS_PLAIN = "ocm_plain"

    /** The passphrase gate. Only a digest is stored; the phrase is never kept. */
    const val VAULT_SALT = "ocm.vault.v1|"
    const val VAULT_DIGEST = "bc4aeaf1bf04449cdab96eac1485deb4a4b3e522f29c16488d7df33da3259206"

    /** Developer gate: twelve digits, digest only. */
    const val DEV_SALT = "ocm.devgate.v1|"
    const val DEV_DIGEST = "72b9749c79f286702d8d2e718b7dff418a2811051f36975e7232c8ddd12d52e4"

    /** Guard maintenance passphrase: digest only. The passphrase itself is not in
     *  this repository, is not shown in the app, and is unrecoverable by design. */
    const val SAFETY_SALT = "ocm.safety.v1|"
    const val SAFETY_DIGEST = "ac73c9087b1c088c184e4ee190e7e4f14063aab62d0751f91d65e1a52b95f827"

    /**
     * Minimum device the app will run on.
     *
     * These are measured against *usable* memory, not the number on the box: a phone
     * sold as 6 GB reports about 3.5 GB to ActivityManager, and a 128 GB phone with a
     * full camera roll has far less free. The first version of this gate used the
     * advertised numbers and locked out the very phones it was written for.
     */
    const val MIN_RAM_BYTES = 2_400_000_000L          // ~3 GB usable
    const val MIN_STORAGE_BYTES = 4_000_000_000L      // 4 GB free
    const val HARD_RAM_FLOOR = 1_900_000_000L         // below this, refuse outright
    const val HARD_STORAGE_FLOOR = 2_500_000_000L

    /** Prompt-injection tripwires: patterns that must never reach the shell. */
    val SHELL_DENY_TOKENS = listOf(
        "rm -rf /", ":(){", "mkfs", "dd if=/dev/zero of=/dev/block",
        "pm uninstall", "settings put global", "su -c"
    )
}
