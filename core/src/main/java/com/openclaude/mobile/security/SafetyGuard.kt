package com.openclaude.mobile.security

import com.openclaude.mobile.data.secure.NativeHasher
import com.openclaude.mobile.data.secure.Vault
import com.openclaude.mobile.util.Constants
import com.openclaude.mobile.util.Logs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The protection layer.
 *
 * Fail-closed by construction: [enabled] is true unless a stored flag says otherwise
 * *and* that flag is backed by a digest check. Tamper with the preference file, wipe
 * it, or restore an older backup, and the guard comes back on. There is no code path
 * from a chat message to this class.
 *
 * The maintenance switch exists for one reason — a future change to the rule set
 * needs a way to be turned off while testing — and it is gated by a passphrase whose
 * digest is compiled in and whose plaintext exists nowhere in this repository or in
 * the app's UI. If that passphrase is lost, the guard stays on permanently. That is
 * the intended failure mode, not a bug: the safe state is the default state.
 */
class SafetyGuard(private val vault: Vault) {

    private val _enabled = MutableStateFlow(true)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _blockedCount = MutableStateFlow(0)
    val blockedCount: StateFlow<Int> = _blockedCount.asStateFlow()

    private val _lastReason = MutableStateFlow<String?>(null)
    val lastReason: StateFlow<String?> = _lastReason.asStateFlow()

    val shield = PromptShield()

    fun load() {
        val stored = vault.prefs.getBoolean(KEY_DISABLED, false)
        val tag = vault.prefs.getString(KEY_TAG, null)
        // A disabled flag only counts if it carries the matching tag. Anything else
        // — a hand-edited prefs file, a restored backup — reads as "enabled".
        _enabled.value = !(stored && tag == TAG_VALUE)
        Logs.i("guard", "protection ${if (_enabled.value) "ON" else "OFF (authorised)"}")
    }

    fun screenPrompt(text: String): JailbreakDetector.Result {
        val result = JailbreakDetector.inspect(text)
        when (result.verdict) {
            JailbreakDetector.Verdict.BLOCK -> {
                _blockedCount.value = _blockedCount.value + 1
                _lastReason.value = result.reasons.firstOrNull()
                Logs.w("guard", "blocked prompt: ${result.reasons.joinToString()}")
            }
            JailbreakDetector.Verdict.FLAG -> Logs.i("guard", "flagged: ${result.reasons.joinToString()}")
            JailbreakDetector.Verdict.ALLOW -> Unit
        }
        return result
    }

    /** Applies the shield only when protection is on, so the switch is meaningful. */
    fun hardenSystem(persona: String): String =
        if (_enabled.value) shield.harden(persona) else persona

    fun wrapUser(text: String): String =
        if (_enabled.value) shield.wrapUser(shield.sanitise(text)) else text

    fun screenOutput(text: String): OutputGuard.Result {
        val result = OutputGuard.screen(text)
        if (result.blocked) {
            _blockedCount.value = _blockedCount.value + 1
            _lastReason.value = "output blocked"
        }
        return result
    }

    /** Turning the guard off needs the passphrase; turning it on never does. */
    fun disable(passphrase: String): Boolean {
        val digest = runCatching {
            NativeHasher.sha256HexSalted(Constants.SAFETY_SALT, passphrase)
        }.getOrNull() ?: return false

        val ok = NativeHasher.constantTimeEquals(
            digest.toByteArray(),
            Constants.SAFETY_DIGEST.toByteArray()
        )
        if (!ok) {
            Logs.w("guard", "disable refused")
            return false
        }
        vault.prefs.edit().putBoolean(KEY_DISABLED, true).putString(KEY_TAG, TAG_VALUE).apply()
        _enabled.value = false
        Logs.i("guard", "protection disabled with valid passphrase")
        return true
    }

    fun enable() {
        vault.prefs.edit().putBoolean(KEY_DISABLED, false).remove(KEY_TAG).apply()
        _enabled.value = true
        Logs.i("guard", "protection enabled")
    }

    /** Called on every cold start: the guard must never stay off across a reinstall. */
    fun verifyIntegrity() {
        if (!_enabled.value) {
            val tag = vault.prefs.getString(KEY_TAG, null)
            if (tag != TAG_VALUE) {
                Logs.w("guard", "integrity check failed, re-enabling")
                enable()
            }
        }
    }

    fun resetCounter() {
        _blockedCount.value = 0
        _lastReason.value = null
    }

    private companion object {
        const val KEY_DISABLED = "guard.disabled"
        const val KEY_TAG = "guard.tag"
        const val TAG_VALUE = "ocm-guard-2f9c"
    }
}
