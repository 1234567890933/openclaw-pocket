package com.openclaude.mobile.bots

import com.openclaude.mobile.util.Logs

/**
 * Turns one user request into a small team effort.
 *
 * The pattern is plan → dispatch → synthesise, and it is deliberately shallow. Deep
 * agent trees are impressive in a demo and unusable on a phone: every level costs a
 * round trip, and the user is staring at a spinner the whole time.
 *
 * The plan is chosen by rules, not by a model. A model-written plan costs a full
 * inference before any work starts, and for the five bots in this app the mapping
 * from intent to team is knowable. Rules are also testable, which a prompt is not.
 */
class Orchestrator(private val supervisor: BotSupervisor = BotSupervisor()) {

    data class Plan(
        val steps: List<Step>,
        val rationale: String
    ) {
        data class Step(val bot: Bot, val task: String)
    }

    /** Keyword rules, ordered: the first match wins. */
    fun plan(request: String): Plan {
        val text = request.lowercase()

        fun has(vararg words: String) = words.any { text.contains(it) }

        return when {
            has("compare", "versus", " vs ", "trade-off", "tradeoff", "which is better") -> Plan(
                listOf(
                    Plan.Step(BotRoster.scout, "Gather current facts and sources for: $request"),
                    Plan.Step(BotRoster.ledger, "Lay out the options side by side with numbers: $request"),
                    Plan.Step(BotRoster.critic, "Name the weakest assumption in that comparison")
                ),
                "a comparison needs sources, numbers, then a challenge"
            )

            has("fix", "bug", "error", "crash", "broken", "not working") -> Plan(
                listOf(
                    Plan.Step(BotRoster.forge, "Reproduce and locate the fault: $request"),
                    Plan.Step(BotRoster.critic, "List the three most likely ways that fix fails"),
                    Plan.Step(BotRoster.forge, "Apply the fix and run it")
                ),
                "diagnose, attack the diagnosis, then fix once"
            )

            has("build", "write", "create", "make", "implement", "app") -> Plan(
                listOf(
                    Plan.Step(BotRoster.forge, "Write the smallest working version of: $request"),
                    Plan.Step(BotRoster.critic, "Review it for the failure that matters most")
                ),
                "build, then review before showing it"
            )

            has("search", "find", "what is", "who is", "latest", "news", "price") -> Plan(
                listOf(
                    Plan.Step(BotRoster.scout, "Answer with sources: $request"),
                    Plan.Step(BotRoster.critic, "Flag any claim that the sources do not support")
                ),
                "research, then check the research"
            )

            else -> Plan(
                listOf(Plan.Step(BotRoster.scout, request)),
                "one bot is enough for this"
            )
        }
    }

    /**
     * Runs the plan. Each step's output is fed to the next, so a reviewer actually
     * reviews something rather than the original question again.
     */
    suspend fun execute(
        request: String,
        step: suspend (Bot, String) -> String
    ): String {
        val plan = plan(request)
        Logs.i("orchestrator", "plan: ${plan.steps.joinToString { it.bot.id }} (${plan.rationale})")

        val transcript = StringBuilder()
        var carried = request

        for (current in plan.steps) {
            val task = if (transcript.isEmpty()) {
                current.task
            } else {
                current.task + "\n\nWork so far:\n" + transcript.toString().takeLast(4000)
            }
            val output = runCatching { step(current.bot, task) }
                .getOrElse { "(${current.bot.name} failed: ${it.message})" }
            transcript.append("### ${current.bot.name}\n").append(output).append("\n\n")
            carried = output
        }

        return transcript.toString().trim()
    }

    fun summarise(transcript: String): String {
        // Keeps the last bot's answer, which is the one that matters to the user.
        val sections = transcript.split("### ").filter { it.isNotBlank() }
        return sections.lastOrNull()?.substringAfter('\n')?.trim() ?: transcript
    }
}
