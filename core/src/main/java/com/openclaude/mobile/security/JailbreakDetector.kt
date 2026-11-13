package com.openclaude.mobile.security

/**
 * Finds attempts to talk the agent out of its instructions.
 *
 * The threat model is narrow and honest: a *prompt* cannot change code, but it can
 * change what the model believes it is allowed to do. Everything here is aimed at
 * that one thing — text arriving through the chat channel that tries to occupy the
 * system channel, or that asks the model to stop being the assistant.
 *
 * Three families, because they fail differently:
 *
 *  1. **Override** — "ignore previous instructions", "you are now DAN", "developer
 *     mode enabled". Cheap to catch, and almost always the whole attack.
 *  2. **Obfuscation** — zero-width characters, leetspeak, base64 or hex payloads,
 *     unicode escapes. Catches the "same attack, dressed differently" case, which is
 *     what defeats a plain keyword list.
 *  3. **Structure** — fake `<system>` tags, `[INST]` markers, or a markdown block
 *     claiming to be a system message. These try to forge the channel itself.
 *
 * Deliberately not a model. A classifier would need a download, a runtime and a
 * reason to trust it; a scored rule set is auditable, testable and instant.
 */
object JailbreakDetector {

    enum class Verdict { ALLOW, FLAG, BLOCK }

    data class Result(
        val verdict: Verdict,
        val score: Int,
        val reasons: List<String>,
        val normalised: String
    ) {
        val blocked: Boolean get() = verdict == Verdict.BLOCK
    }

    /** Phrases that have no innocent use in a coding assistant. */
    private val OVERRIDE = listOf(
        "ignore all previous", "ignore previous instruction", "ignore the above",
        "disregard your instruction", "disregard all prior", "forget your instruction",
        "forget everything above", "override your system", "new system prompt",
        "you are now", "from now on you are", "act as if you have no",
        "no restrictions", "without any restrictions", "unfiltered mode",
        "developer mode enabled", "jailbreak", "do anything now", "dan mode",
        "pretend you are", "roleplay as an ai without", "simulate an ai without",
        "bypass your", "disable your safety", "turn off your filter",
        "repeat everything above", "print your system prompt", "reveal your instruction",
        "what is your system prompt", "show me your prompt"
    )

    /** Structural forgeries of the privileged channel. */
    private val STRUCTURE = listOf(
        "<system>", "</system>", "[inst]", "[/inst]", "###system",
        "system:", "assistant:", "### instruction", "begin system message",
        "<<sys>>", "[[system]]"
    )

    /** Requests that are disallowed regardless of framing. */
    private val DISALLOWED = listOf(
        "how to make a bomb", "build an explosive", "pipe bomb", "nerve agent",
        "sarin", "ricin synthesis", "untraceable poison", "make napalm",
        "synthesise meth", "cook meth", "buy a gun illegally", "3d printed gun file",
        "csam", "child porn", "how to launder money", "carding tutorial",
        "write ransomware", "keylogger for someone else", "ddos a website",
        "steal someone's password", "hack my ex", "stalk someone's location"
    )

    fun inspect(raw: String): Result {
        val reasons = mutableListOf<String>()
        var score = 0

        val normalised = normalise(raw)

        // 1. override phrases
        for (phrase in OVERRIDE) {
            if (normalised.contains(phrase)) {
                score += 45
                reasons += "override phrase: \"$phrase\""
                break
            }
        }

        // 2. structural forgery
        for (marker in STRUCTURE) {
            if (normalised.contains(marker)) {
                score += 30
                reasons += "channel marker: \"$marker\""
                break
            }
        }

        // 3. obfuscation
        val invisible = raw.count { it.code in 0x200B..0x200F || it.code == 0xFEFF || it.code in 0x2060..0x2064 }
        if (invisible > 3) {
            score += 25
            reasons += "$invisible invisible characters"
        }
        if (looksLikeEncodedPayload(raw)) {
            score += 25
            reasons += "long encoded payload"
        }
        if (hasUnicodeEscapes(raw)) {
            score += 15
            reasons += "unicode escape sequence"
        }
        if (isLeet(normalised)) {
            score += 15
            reasons += "leet substitution"
        }

        // 4. disallowed subject matter — blocked on its own, no scoring games
        for (topic in DISALLOWED) {
            if (normalised.contains(topic)) {
                return Result(Verdict.BLOCK, 100, listOf("disallowed request: \"$topic\""), normalised)
            }
        }

        // 5. instruction-shaped text in a very short message is suspicious on its own
        if (raw.length < 80 && (normalised.contains("you must") || normalised.contains("you will now"))) {
            score += 15
            reasons += "imperative framing in a short message"
        }

        val verdict = when {
            score >= 60 -> Verdict.BLOCK
            score >= 25 -> Verdict.FLAG
            else -> Verdict.ALLOW
        }
        return Result(verdict, score, reasons, normalised)
    }

    /**
     * Folds the text into something a keyword list can match: lowercase, invisible
     * characters removed, common homoglyphs and leetspeak mapped back.
     */
    fun normalise(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (ch in raw) {
            val code = ch.code
            when {
                code in 0x200B..0x200F || code == 0xFEFF || code in 0x2060..0x2064 -> Unit
                code == 0x00A0 -> sb.append(' ')
                ch == '0' -> sb.append('o')
                ch == '1' || ch == '!' -> sb.append('i')
                ch == '3' -> sb.append('e')
                ch == '4' || ch == '@' -> sb.append('a')
                ch == '5' || ch == '$' -> sb.append('s')
                ch == '7' -> sb.append('t')
                ch == '8' -> sb.append('b')
                ch == '9' -> sb.append('g')
                ch == '|' -> sb.append('i')
                else -> sb.append(ch.lowercaseChar())
            }
        }
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }

    private fun looksLikeEncodedPayload(raw: String): Boolean {
        val token = Regex("[A-Za-z0-9+/=]{120,}").find(raw) ?: return false
        val value = token.value
        // Base64 of ASCII text is mostly printable and lacks long runs of the same char.
        val hasPadding = value.contains('=') || value.length % 4 == 0
        return hasPadding && value.count { it.isUpperCase() } > value.length / 8
    }

    private fun hasUnicodeEscapes(raw: String): Boolean =
        Regex("\\\\u[0-9a-fA-F]{4}").findAll(raw).take(4).count() >= 4

    private fun isLeet(normalised: String): Boolean =
        listOf("jailbrek", "ign0re", "byp4ss", "d4n", "unf1ltered", "n0 restr")
            .any { normalised.contains(it) }
}
