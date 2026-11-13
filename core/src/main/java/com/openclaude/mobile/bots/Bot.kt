package com.openclaude.mobile.bots

import com.openclaude.mobile.data.router.ModelMode

/**
 * One persona the user can hand a task to.
 *
 * A bot is not a second model. It is a system prompt, a tool allow-list, a model
 * preference and a budget — which is exactly what makes it useful: the same phone
 * can be a careful reviewer and a fast researcher without two installations.
 *
 * The tool list is an allow-list, never a deny-list. A bot that gains a tool because
 * somebody added one to the registry is a security bug, not a feature.
 */
data class Bot(
    val id: String,
    val name: String,
    val glyph: String,
    val tagline: String,
    val persona: String,
    val tools: Set<String>,
    val mode: ModelMode = ModelMode.AUTO,
    val maxSteps: Int = 6,
    val temperature: Float = 0.4f
) {
    /** Turns the persona into the system message the gateway receives. */
    fun systemPrompt(): String = buildString {
        appendLine(persona.trim())
        appendLine()
        appendLine("You are running as \"$name\" on a phone. Be concise: short paragraphs, no preamble.")
        appendLine("You have these tools: ${tools.sorted().joinToString(", ").ifEmpty { "none" }}.")
        appendLine("Ask before anything destructive. Prefer reading over guessing.")
        appendLine("If a task needs a tool you do not have, say which bot does.")
    }
}
