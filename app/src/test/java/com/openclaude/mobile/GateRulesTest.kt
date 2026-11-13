package com.openclaude.mobile

import com.openclaude.mobile.util.Constants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gate is only as good as its stored digest. These tests check the properties
 * that can be verified without knowing the phrase — which is the point, because the
 * phrase must not live in the repository.
 */
class GateRulesTest {

    @Test
    fun `digest is a sha256 hex string and not a placeholder`() {
        assertEquals(64, Constants.VAULT_DIGEST.length)
        assertTrue(
            "digest must be lowercase hex",
            Constants.VAULT_DIGEST.all { it in "0123456789abcdef" }
        )
        assertTrue(
            "digest is still the sample placeholder",
            Constants.VAULT_DIGEST != "0".repeat(64)
        )
    }

    @Test
    fun `salt is stable`() {
        // Changing the salt silently invalidates every issued passphrase.
        assertEquals("ocm.vault.v1|", Constants.VAULT_SALT)
    }

    @Test
    fun `checksum rule holds for a well formed phrase`() {
        // Five nine digit groups, then the digit sum padded to five.
        val groups = listOf("000000027", "000000820", "000000336", "000000220", "000000441")
        val body = groups.joinToString("")
        assertEquals(45, body.length)

        val sum = body.sumOf { it.digitToInt() }
        val phrase = body + sum.toString().padStart(5, '0')
        assertEquals(50, phrase.length)

        val recomputed = phrase.take(45).sumOf { it.digitToInt() }
        assertEquals(sum, recomputed)
        assertEquals(sum, phrase.takeLast(5).toInt())
    }

    @Test
    fun `a wrong checksum is detectable without the digest`() {
        val body = "000000027000000820000000336000000220000000441"
        val wrong = body + "00045"
        assertTrue(wrong.take(45).sumOf { it.digitToInt() } != wrong.takeLast(5).toInt())
    }

    @Test
    fun `session and message caps are sane`() {
        assertTrue(Constants.MAX_SESSIONS in 1..8)
        assertTrue(Constants.MAX_MESSAGES_PER_SESSION >= 100)
    }
}
