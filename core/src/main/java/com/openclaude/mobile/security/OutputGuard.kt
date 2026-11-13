package com.openclaude.mobile.security

/**
 * Screens what comes back before it is shown or spoken.
 *
 * Two jobs only. Redact anything that looks like a live credential — a model that
 * has read a .env file will happily repeat it, and that is a real leak. And replace
 * a response that has drifted into dangerous instruction with a refusal, because a
 * jailbreak that succeeds on the way *out* is still a jailbreak.
 */
object OutputGuard {

    data class Result(val text: String, val redactions: Int, val blocked: Boolean)

    private val CREDENTIALS = listOf(
        Regex("sk-[A-Za-z0-9_\\-]{16,}"),
        Regex("AIza[0-9A-Za-z_\\-]{30,}"),
        Regex("ghp_[A-Za-z0-9]{30,}"),
        Regex("xox[baprs]-[A-Za-z0-9\\-]{10,}"),
        Regex("-----BEGIN [A-Z ]*PRIVATE KEY-----[\\s\\S]{0,4000}?-----END [A-Z ]*PRIVATE KEY-----")
    )

    private val DANGEROUS = listOf(
        "how to make a bomb", "synthesis of sarin", "synthesise ricin",
        "step 1: acquire the precursor", "detonator wiring", "pipe bomb"
    )

    fun screen(text: String): Result {
        var output = text
        var redactions = 0
        for (pattern in CREDENTIALS) {
            val matches = pattern.findAll(output).toList()
            if (matches.isNotEmpty()) {
                redactions += matches.size
                output = pattern.replace(output, "[redacted credential]")
            }
        }

        val lower = output.lowercase()
        val blocked = DANGEROUS.any { lower.contains(it) }
        if (blocked) {
            return Result(
                text = "I can't help with that. If you think this is a mistake, rephrase the " +
                    "request — the guard blocked the response, not you.",
                redactions = redactions,
                blocked = true
            )
        }
        return Result(output, redactions, blocked = false)
    }
}
