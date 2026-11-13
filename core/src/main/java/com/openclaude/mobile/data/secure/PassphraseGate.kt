package com.openclaude.mobile.data.secure

import com.openclaude.mobile.util.Constants
import com.openclaude.mobile.util.Logs

/**
 * The developer gate.
 *
 * The settings screen shows a five-part algebra problem. Each answer, zero padded
 * to nine digits, plus a five digit checksum of all the digits, is the 50-character
 * phrase. We store only SHA-256(salt + phrase), so reading the APK gives an
 * attacker a digest and a puzzle, not a password.
 *
 * There is no prompt path into this: the gate is not reachable from the chat
 * surface, the agent has no tool that writes the unlock flag, and the flag itself
 * lives in the encrypted vault.
 */
class PassphraseGate {

    data class Problem(val lines: List<String>)

    fun problem(): Problem = Problem(
        listOf(
            "1.  Solve for x:  7x - 23 = 4x + 58",
            "2.  Sum of all integers from 1 to 40 inclusive",
            "3.  Given f(x) = x^3 - 6x^2 + 11x - 6, find f(9)",
            "4.  How many integers n with 1 <= n <= 1000 are divisible by 7 or by 11",
            "5.  The value of 3^12 modulo 1000"
        )
    )

    fun hint(): String =
        "Write the five answers in order, each padded with leading zeros to nine " +
            "digits (45 characters). Then append the sum of all 45 digits, padded to " +
            "five digits. The result is exactly 50 characters."

    fun verify(phrase: String): Boolean {
        val cleaned = phrase.filterNot { it.isWhitespace() }
        if (cleaned.length != 50 || !cleaned.all { it.isDigit() }) {
            Logs.w("gate", "rejected: length ${cleaned.length} or non-digit content")
            return false
        }
        val digest = runCatching {
            NativeHasher.sha256HexSalted(Constants.VAULT_SALT, cleaned)
        }.getOrElse {
            Logs.e("gate", "native hasher unavailable, refusing to fall back", it)
            return false
        }
        val expected = Constants.VAULT_DIGEST.toByteArray()
        return NativeHasher.constantTimeEquals(digest.toByteArray(), expected)
    }

    fun expectedLength(): Int = 50
}
