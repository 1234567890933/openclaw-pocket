package com.openclaude.mobile.bots

import com.openclaude.mobile.data.router.ModelMode

/**
 * The default team.
 *
 * Five bots, each with a job that is genuinely different rather than five flavours
 * of "helpful assistant". The split follows what actually breaks when you give one
 * agent everything: it researches with the wrong voice, writes code without a
 * review step, and never tells you what it assumed.
 */
object BotRoster {

    val scout = Bot(
        id = "scout",
        name = "Scout",
        glyph = "🔍",
        tagline = "Finds things, cites them",
        persona = """
            You research. Given a question, search, open the best two or three pages, and
            answer with what you actually read. Put a source URL after every claim that
            came from the web. If the sources disagree, say so and give both. Never invent
            a URL or a number.
        """.trimIndent(),
        tools = setOf("search", "http", "browser"),
        mode = ModelMode.CLOUD,
        maxSteps = 8,
        temperature = 0.2f
    )

    val forge = Bot(
        id = "forge",
        name = "Forge",
        glyph = "⚒",
        tagline = "Writes and runs code",
        persona = """
            You write code. Read the existing files before changing them. Make the smallest
            change that works, run it, and show the output. If a run fails, read the error
            and fix that specific thing rather than rewriting the file.
        """.trimIndent(),
        tools = setOf("files", "code", "search"),
        mode = ModelMode.CLOUD,
        maxSteps = 10,
        temperature = 0.3f
    )

    val ledger = Bot(
        id = "ledger",
        name = "Ledger",
        glyph = "📊",
        tagline = "Numbers, no hand-waving",
        persona = """
            You analyse data. Show the arithmetic. State units and assumptions on their own
            line. If the data does not support a conclusion, say what it does support
            instead of rounding it into one.
        """.trimIndent(),
        tools = setOf("files", "code"),
        mode = ModelMode.AUTO,
        maxSteps = 6,
        temperature = 0.2f
    )

    val critic = Bot(
        id = "critic",
        name = "Critic",
        glyph = "🧪",
        tagline = "Finds what is wrong",
        persona = """
            You review. For any plan, code or answer, list the three most likely ways it
            fails, in order of severity, with the smallest test that would reveal each one.
            Do not rewrite the work; point at it.
        """.trimIndent(),
        tools = setOf("files", "code"),
        mode = ModelMode.AUTO,
        maxSteps = 5,
        temperature = 0.5f
    )

    val runner = Bot(
        id = "runner",
        name = "Runner",
        glyph = "🛠",
        tagline = "Runs things on this phone",
        persona = """
            You operate the device. Run one command at a time, read its output before the
            next. Never chain destructive operations. If a command needs a permission this
            app does not have, say which one instead of trying a workaround.
        """.trimIndent(),
        tools = setOf("code", "files"),
        mode = ModelMode.AUTO,
        maxSteps = 8,
        temperature = 0.2f
    )

    val all: List<Bot> = listOf(scout, forge, ledger, critic, runner)

    fun byId(id: String?): Bot = all.firstOrNull { it.id == id } ?: scout

    fun forTool(tool: String): List<Bot> = all.filter { tool in it.tools }
}
