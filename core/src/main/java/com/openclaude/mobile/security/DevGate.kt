package com.openclaude.mobile.security

import com.openclaude.mobile.data.secure.NativeHasher
import com.openclaude.mobile.data.secure.Vault
import com.openclaude.mobile.util.Constants
import com.openclaude.mobile.util.Logs

/**
 * The developer gate.
 *
 * A twelve-digit code, checked against a stored digest in native code. The code is
 * not in the repository and not in the APK — only SHA-256(salt + code) is, so
 * reading the binary gives an attacker a 10^12 search space and no shortcut.
 *
 * What it unlocks is *control*, not permission to misbehave: the system prompt, the
 * sampling parameters, the raw protocol log and the tool policy. There is no code
 * path from a chat message to this state — the gate is only reachable from Settings,
 * and the flag lives in the encrypted vault.
 *
 * Rate limited: five wrong codes locks the gate for the rest of the process, which
 * makes a shoulder-surfing guess worthless.
 */
class DevGate(private val vault: Vault) {

    private var failures = 0

    val isUnlocked: Boolean get() = vault.developerUnlocked

    fun remainingAttempts(): Int = (MAX_ATTEMPTS - failures).coerceAtLeast(0)

    fun locked(): Boolean = failures >= MAX_ATTEMPTS

    fun verify(code: String): Result<Unit> {
        if (locked()) {
            return Result.failure(IllegalStateException("gate locked until restart"))
        }

        val cleaned = code.filter { it.isDigit() }
        if (cleaned.length != 12) {
            failures++
            return Result.failure(IllegalArgumentException("the code is twelve digits"))
        }

        val digest = runCatching {
            NativeHasher.sha256HexSalted(Constants.DEV_SALT, cleaned)
        }.getOrElse {
            Logs.e("devgate", "native hasher unavailable", it)
            return Result.failure(IllegalStateException("verifier unavailable"))
        }

        val ok = NativeHasher.constantTimeEquals(
            digest.toByteArray(),
            Constants.DEV_DIGEST.toByteArray()
        )

        return if (ok) {
            vault.developerUnlocked = true
            failures = 0
            Logs.i("devgate", "unlocked")
            Result.success(Unit)
        } else {
            failures++
            Logs.w("devgate", "rejected (${remainingAttempts()} left)")
            Result.failure(IllegalArgumentException("rejected"))
        }
    }

    fun relock() {
        vault.developerUnlocked = false
        failures = 0
        Logs.i("devgate", "relocked")
    }

    private companion object {
        const val MAX_ATTEMPTS = 5
    }
}
